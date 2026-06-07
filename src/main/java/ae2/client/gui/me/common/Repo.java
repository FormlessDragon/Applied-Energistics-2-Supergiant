/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.client.gui.me.common;

import ae2.api.config.SortDir;
import ae2.api.config.SortOrder;
import ae2.api.config.ViewItems;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.client.gui.me.search.RepoSearch;
import ae2.client.gui.widgets.IScrollSource;
import ae2.client.gui.widgets.ISortSource;
import ae2.container.me.common.GridInventoryEntry;
import ae2.container.me.common.IClientRepo;
import ae2.core.AELog;
import ae2.util.prioritylist.IPartitionList;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * For showing the network content of a storage channel, this class will maintain a client-side copy of the current
 * server-side storage, which is continuously synchronized to the client while it is open.
 */
public class Repo implements IClientRepo {

    public static final Comparator<GridInventoryEntry> AMOUNT_ASC = Comparator
        .comparingDouble((GridInventoryEntry entry) -> {
            var what = Objects.requireNonNull(entry.what());
            return ((double) entry.storedAmount()) / ((double) what.getAmountPerUnit());
        });

    public static final Comparator<GridInventoryEntry> AMOUNT_DESC = AMOUNT_ASC.reversed();

    private static final Comparator<GridInventoryEntry> PINNED_ROW_COMPARATOR = Comparator.comparing(entry -> {
        var pinInfo = PinnedKeys.getCraftingPinInfo(entry.what());
        return pinInfo != null ? pinInfo.since : Instant.MAX;
    });
    private final BiMap<Long, GridInventoryEntry> entries = HashBiMap.create();
    private final ObjectList<GridInventoryEntry> view = new ObjectArrayList<>();
    private final ObjectList<GridInventoryEntry> pinnedSlots = new ObjectArrayList<>();
    private final ObjectList<GridInventoryEntry> craftingPinnedEntries = new ObjectArrayList<>();
    private final ObjectList<GridInventoryEntry> playerPinnedEntries = new ObjectArrayList<>();
    private final Map<AEKey, PinnedKeys.PinReason> pinnedReasons = new it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<>();
    /**
     * Entries by item ID to speed up ingredient matching.
     */
    private final Int2ObjectOpenHashMap<List<GridInventoryEntry>> entriesByItemId = new Int2ObjectOpenHashMap<>();
    private final RepoSearch search = new RepoSearch();
    private final IScrollSource src;
    private final ISortSource sortSrc;
    private int rowSize = 9;
    private int terminalRows = 1;
    private int pinnedRowCount;
    private int visiblePlayerPinRows;
    private boolean enabled = false;
    private boolean entriesByItemIdNeedsUpdate = true;
    private IPartitionList partitionList;
    private Runnable updateViewListener;
    private boolean paused;

    public Repo(IScrollSource src, ISortSource sortSrc) {
        this.src = src;
        this.sortSrc = sortSrc;
    }

    private static boolean takeOverSlotOccupiedByRemovedItem(GridInventoryEntry serverEntry,
                                                             Map<AEKey, IntList> freeSlots, List<GridInventoryEntry> slots) {
        IntList freeSlotIndices = freeSlots.get(serverEntry.what());
        if (freeSlotIndices == null) {
            return false;
        }

        int i = freeSlotIndices.removeInt(freeSlotIndices.size() - 1);
        if (freeSlotIndices.isEmpty()) {
            freeSlots.remove(serverEntry.what());
        }

        slots.set(i, serverEntry);
        return true;
    }

    public void setPartitionList(IPartitionList partitionList) {
        if (partitionList != this.partitionList) {
            this.partitionList = partitionList;
            this.updateView();
        }
    }

    @Override
    public final void handleUpdate(boolean fullUpdate, List<GridInventoryEntry> entries) {
        if (fullUpdate) {
            clear();
        }

        for (var entry : entries) {
            handleUpdate(entry);
        }

        updateView();
    }

    private void handleUpdate(GridInventoryEntry serverEntry) {
        entriesByItemIdNeedsUpdate = true;

        var localEntry = entries.get(serverEntry.serial());
        if (localEntry == null) {
            // First time we're seeing this serial -> create new entry
            if (serverEntry.what() == null) {
                AELog.warn("First time seeing serial %s, but incomplete info received", serverEntry.serial());
                return;
            }
            if (serverEntry.isMeaningful()) {
                entries.put(serverEntry.serial(), serverEntry);
            }
            return;
        }

        // Update the local entry
        if (!serverEntry.isMeaningful()) {
            entries.remove(serverEntry.serial());
        } else if (serverEntry.what() == null) {
            entries.put(serverEntry.serial(), new GridInventoryEntry(
                serverEntry.serial(),
                localEntry.what(),
                serverEntry.storedAmount(),
                serverEntry.requestableAmount(),
                serverEntry.craftable()));
        } else {
            entries.put(serverEntry.serial(), serverEntry);
        }
    }

    @Nullable
    private static GridInventoryEntry findPinnedEntry(List<GridInventoryEntry> entries, AEKey key) {
        for (GridInventoryEntry entry : entries) {
            if (key.equals(entry.what())) {
                return entry;
            }
        }
        return null;
    }

    public final void updateView() {
        // While the view is paused, we try to only append to the view list in order to avoid mis-clicks by the
        // player due to items shifting under their mouse cursor.
        if (isPaused()) {
            // First pass -> detect and update
            var visibleSerials = new LongOpenHashSet(this.view.size());
            updateEntriesWhilePaused(craftingPinnedEntries, visibleSerials);
            updateEntriesWhilePaused(playerPinnedEntries, visibleSerials);
            updateEntriesWhilePaused(view, visibleSerials);

            var craftingPinnedFreeSlots = getFreeSlots(craftingPinnedEntries);
            var playerPinnedFreeSlots = getFreeSlots(playerPinnedEntries);
            var viewFreeSlots = getFreeSlots(view);

            ObjectList<GridInventoryEntry> entriesToAdd = new ObjectArrayList<>();

            // Determine what to do with server entries that are not currently being shown
            for (var serverEntry : entries.values()) {
                if (visibleSerials.contains(serverEntry.serial())) {
                    continue; // Entry is already visible
                }

                // First, try to find an empty/meaningless slot in the view that is visually indistinguishable
                // and fill it
                if (takeOverSlotOccupiedByRemovedItem(serverEntry, craftingPinnedFreeSlots, craftingPinnedEntries)
                    || takeOverSlotOccupiedByRemovedItem(serverEntry, playerPinnedFreeSlots, playerPinnedEntries)
                    || takeOverSlotOccupiedByRemovedItem(serverEntry, viewFreeSlots, view)) {
                    continue;
                }

                // if we couldn't take over an existing slot, just append it
                entriesToAdd.add(serverEntry);
            }

            addEntriesToView(entriesToAdd);
        } else {
            this.view.clear();
            this.craftingPinnedEntries.clear();
            this.playerPinnedEntries.clear();

            addEntriesToView(this.entries.values());
        }

        // Don't re-sort while being paused
        if (!isPaused()) {
            // Sort older entries first in the pinned row
            craftingPinnedEntries.sort(PINNED_ROW_COMPARATOR);

            var sortOrder = this.sortSrc.getSortBy();
            var sortDir = this.sortSrc.getSortDir();

            this.view.sort(getComparator(sortOrder, sortDir));
        }

        rebuildPinnedSlots();

        if (this.updateViewListener != null) {
            this.updateViewListener.run();
        }
    }

    private void addEntriesToView(Collection<GridInventoryEntry> entries) {
        var viewMode = this.sortSrc.getSortDisplay();
        var typeFilter = this.sortSrc.getSortKeyTypes();

        for (var entry : entries) {
            // Pinned keys ignore all filters & search
            if (PinnedKeys.isCraftingPinned(entry.what())) {
                craftingPinnedEntries.add(entry);
                continue;
            }
            if (PinnedKeys.isPlayerPinned(entry.what())) {
                playerPinnedEntries.add(entry);
                continue;
            }

            if (this.partitionList != null && !this.partitionList.isListed(entry.what())) {
                continue;
            }

            if (viewMode == ViewItems.CRAFTABLE && !entry.craftable()) {
                continue;
            }

            if (viewMode == ViewItems.STORED && entry.storedAmount() == 0) {
                continue;
            }

            var what = Objects.requireNonNull(entry.what());
            if (!typeFilter.contains(what.getType())) {
                continue;
            }

            if (search.matches(entry)) {
                this.view.add(entry);
            }
        }

        // Any pinned entry that has not yet been added to the pinned row will be represented by a fake
        // entry. Pinned crafting jobs are excluded from this because they *should* have a grid-entry
        // with craftable=true if they're craftable on this grid.
        for (var pinnedKey : PinnedKeys.getPlayerPinnedKeys()) {
            if (!PinnedKeys.isCraftingPinned(pinnedKey)
                && playerPinnedEntries.stream().noneMatch(r -> pinnedKey.equals(r.what()))) {
                this.playerPinnedEntries.add(new GridInventoryEntry(
                    -1, pinnedKey, 0, 0, false));
            }
        }
    }

    private void rebuildPinnedSlots() {
        this.pinnedSlots.clear();
        this.pinnedReasons.clear();

        int craftingRows = getRowsFor(craftingPinnedEntries.size());
        int maxPlayerRows = Math.max(0, this.terminalRows - 1 - craftingRows);
        this.visiblePlayerPinRows = Math.min(PinnedKeys.getPlayerPinRows(),
            Math.min(PinnedKeys.MAX_PLAYER_PIN_ROWS, maxPlayerRows));
        this.pinnedRowCount = craftingRows + this.visiblePlayerPinRows;

        for (GridInventoryEntry entry : craftingPinnedEntries) {
            if (pinnedSlots.size() >= craftingRows * rowSize) {
                break;
            }
            pinnedSlots.add(entry);
            pinnedReasons.put(entry.what(), PinnedKeys.PinReason.CRAFTING);
        }
        while (pinnedSlots.size() < craftingRows * rowSize) {
            pinnedSlots.add(null);
        }

        int playerCapacity = getPlayerPinCapacity();
        int playerAdded = 0;
        for (AEKey key : PinnedKeys.getPlayerPinnedKeys()) {
            if (PinnedKeys.isCraftingPinned(key) || playerAdded >= playerCapacity) {
                continue;
            }
            GridInventoryEntry entry = findPinnedEntry(playerPinnedEntries, key);
            if (entry == null) {
                entry = new GridInventoryEntry(-1, key, 0, 0, false);
            }
            pinnedSlots.add(entry);
            pinnedReasons.put(key, PinnedKeys.PinReason.PLAYER);
            playerAdded++;
        }
        while (pinnedSlots.size() < this.pinnedRowCount * rowSize) {
            pinnedSlots.add(null);
        }
    }

    private int getRowsFor(int entryCount) {
        if (entryCount <= 0) {
            return 0;
        }
        return (entryCount + this.rowSize - 1) / this.rowSize;
    }

    private void updateEntriesWhilePaused(List<GridInventoryEntry> shownEntries, LongSet visibleSerials) {
        for (int i = 0; i < shownEntries.size(); i++) {
            var entry = shownEntries.get(i);

            // Update entries with data from server, which doesn't move them
            var serverEntry = entries.get(entry.serial());
            if (serverEntry == null) {
                // The server has removed the entry. Let's replace it with an entry that is not meaningful, but shows
                // amount 0
                entry = new GridInventoryEntry(
                    entry.serial(),
                    entry.what(),
                    0,
                    0,
                    false);
            } else {
                entry = serverEntry;
            }

            visibleSerials.add(entry.serial());
            shownEntries.set(i, entry);
        }
    }

    /**
     * Computes free slot indices by AEKey. Used to replace removed items by items that are visually indistinguishable.
     */
    private Map<AEKey, IntList> getFreeSlots(List<GridInventoryEntry> slots) {
        Map<AEKey, IntList> freeSlots = new it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<>();

        for (int i = 0; i < slots.size(); ++i) {
            var entry = slots.get(i);
            if (!entries.containsKey(entry.serial())) {
                freeSlots.computeIfAbsent(entry.what(), ignored -> new IntArrayList()).add(i);
            }
        }

        // Reverse list, so we can pop from the end of the list
        for (var list : freeSlots.values()) {
            Collections.reverse(list);
        }

        return freeSlots;
    }

    private Comparator<? super GridInventoryEntry> getComparator(SortOrder sortOrder, SortDir sortDir) {
        if (sortOrder == SortOrder.AMOUNT) {
            return sortDir == SortDir.ASCENDING ? AMOUNT_ASC : AMOUNT_DESC;
        }

        return Comparator.comparing(GridInventoryEntry::what, getKeyComparator(sortOrder, sortDir))
                         .thenComparingLong(GridInventoryEntry::serial);
    }

    public List<GridInventoryEntry> getPinnedEntries() {
        ObjectArrayList<GridInventoryEntry> entries = new ObjectArrayList<>();
        for (GridInventoryEntry entry : this.pinnedSlots) {
            if (entry != null) {
                entries.add(entry);
            }
        }
        return Collections.unmodifiableList(entries);
    }

    @Nullable
    public final GridInventoryEntry get(int idx) {
        int pinnedSlots = this.pinnedRowCount * this.rowSize;
        if (idx < pinnedSlots) {
            if (idx < this.pinnedSlots.size()) {
                return this.pinnedSlots.get(idx);
            }
            return null;
        }
        idx -= pinnedSlots;

        idx += this.src.getCurrentScroll() * this.rowSize;

        if (idx >= this.view.size()) {
            return null;
        }
        return this.view.get(idx);
    }

    public final int size() {
        return this.view.size() + this.pinnedRowCount * this.rowSize;
    }

    public final int getScrollableSize() {
        return this.view.size();
    }

    public final void clear() {
        this.entries.clear();
        this.view.clear();
        this.pinnedSlots.clear();
        this.craftingPinnedEntries.clear();
        this.playerPinnedEntries.clear();
        this.pinnedReasons.clear();
        this.pinnedRowCount = 0;
        this.visiblePlayerPinRows = 0;
        this.entriesByItemId.clear();
        this.entriesByItemIdNeedsUpdate = true;
    }

    public final boolean hasPinnedRow() {
        return this.pinnedRowCount > 0;
    }

    public final int getPinnedRowCount() {
        return this.pinnedRowCount;
    }

    public final int getPlayerPinCapacity() {
        return this.visiblePlayerPinRows * this.rowSize;
    }

    public final boolean canRemoveEmptyPlayerPinRow() {
        return PinnedKeys.getPlayerPinRows() > 0
            && PinnedKeys.getPlayerPinnedKeys().size() <= Math.max(0, PinnedKeys.getPlayerPinRows() - 1) * this.rowSize;
    }

    @Nullable
    public final PinnedKeys.PinReason getPinReason(GridInventoryEntry entry) {
        return entry != null && entry.what() != null ? this.pinnedReasons.get(entry.what()) : null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @SuppressWarnings("unused")
    public final int getRowSize() {
        return this.rowSize;
    }

    public final void setRowSize(int rowSize) {
        this.rowSize = rowSize;
    }

    public final void setTerminalRows(int terminalRows) {
        this.terminalRows = Math.max(1, terminalRows);
    }

    public final String getSearchString() {
        return this.search.getSearchString();
    }

    public final void setSearchString(String searchString) {
        this.search.setSearchString(searchString);
    }

    private Comparator<AEKey> getKeyComparator(SortOrder sortBy, SortDir sortDir) {
        return KeySorters.getComparator(sortBy, sortDir);
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        if (this.paused != paused) {
            this.paused = paused;
            AELog.debug("Toggling client-repo pause mode to %s", this.paused);
            if (!paused) {
                updateView(); // resort on unpause
            }
        }
    }

    @Override
    public Set<GridInventoryEntry> getAllEntries() {
        return entries.values();
    }

    @Override
    public List<GridInventoryEntry> getByIngredient(Ingredient ingredient) {
        ObjectList<GridInventoryEntry> entries = new ObjectArrayList<>();
        for (var stack : ingredient.getMatchingStacks()) {
            for (var entry : getByItemId(Item.getIdFromItem(stack.getItem()))) {
                if (entry.what() instanceof AEItemKey itemKey && itemKey.matches(ingredient)) {
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    private Collection<GridInventoryEntry> getByItemId(int itemId) {
        // Build the itemid->entry map if needed
        if (entriesByItemIdNeedsUpdate) {
            rebuildItemIdToEntries();
            entriesByItemIdNeedsUpdate = false;
        }
        return entriesByItemId.getOrDefault(itemId, List.of());
    }

    private void rebuildItemIdToEntries() {
        entriesByItemId.clear();
        for (var entry : getAllEntries()) {
            if (entry.what() instanceof AEItemKey itemKey) {
                var itemId = Item.getIdFromItem(itemKey.getItem());
                var currentList = entriesByItemId.get(itemId);
                if (currentList == null) {
                    // For many items without NBT, this list will only ever have one entry
                    entriesByItemId.put(itemId, List.of(entry));
                } else if (currentList.size() == 1) {
                    // Convert the list from an immutable single-entry list to a mutable normal arraylist
                    ObjectList<GridInventoryEntry> mutableList = new ObjectArrayList<>(10);
                    mutableList.addAll(currentList);
                    mutableList.add(entry);
                    entriesByItemId.put(itemId, mutableList);
                } else {
                    // If it had more than 1 item, it must have been mutable already
                    currentList.add(entry);
                }
            }
        }
    }

    public final void setUpdateViewListener(Runnable updateViewListener) {
        this.updateViewListener = updateViewListener;
    }

    /**
     * Checks if the repo knows that the given key can be crafted.
     */
    @SuppressWarnings("unused")
    public boolean isCraftable(AEKey what) {
        for (var entry : entries.values()) {
            if (entry.craftable() && what.equals(entry.what())) {
                return true;
            }
        }
        return false;
    }
}

