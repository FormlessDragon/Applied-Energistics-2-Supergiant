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

import ae2.api.config.PinDisplayMode;
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
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
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
    private static final int NO_USER_PIN_SLOT = -1;

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
    private final ObjectArrayList<GridInventoryEntry> view = new ObjectArrayList<>();
    private final ObjectArrayList<GridInventoryEntry> pinnedSlots = new ObjectArrayList<>();
    private final IntArrayList pinnedSlotUserSlotIndexes = new IntArrayList();
    private final ObjectArrayList<GridInventoryEntry> scrollableUserPinSlots = new ObjectArrayList<>();
    private final IntArrayList scrollableUserPinSlotIndexes = new IntArrayList();
    private final ObjectArrayList<GridInventoryEntry> craftingPinnedEntries = new ObjectArrayList<>();
    private final ObjectArrayList<GridInventoryEntry> playerPinnedEntries = new ObjectArrayList<>();
    private final Map<AEKey, PinnedKeys.PinReason> pinnedReasons = new Object2ObjectOpenHashMap<>();
    private Set<AEKey> visibleCraftingPinKeys = Set.of();
    /**
     * Entries by item ID to speed up ingredient matching.
     */
    private final Int2ObjectOpenHashMap<ObjectList<GridInventoryEntry>> entriesByItemId = new Int2ObjectOpenHashMap<>();
    private final RepoSearch search = new RepoSearch();
    private final IScrollSource src;
    private final ISortSource sortSrc;
    private int rowSize = 9;
    private int terminalRows = 1;
    private int pinnedRowCount;
    private int visiblePlayerPinRows;
    private int configuredPlayerPinRows;
    private boolean enabled = false;
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
        var localEntry = entries.get(serverEntry.serial());
        if (localEntry == null) {
            // First time we're seeing this serial -> create new entry
            if (serverEntry.what() == null) {
                AELog.warn("First time seeing serial %s, but incomplete info received", serverEntry.serial());
                return;
            }
            if (serverEntry.isMeaningful()) {
                entries.put(serverEntry.serial(), serverEntry);
                addEntryByItemId(serverEntry);
            }
            return;
        }

        // Update the local entry
        if (!serverEntry.isMeaningful()) {
            entries.remove(serverEntry.serial());
            removeEntryByItemId(localEntry);
        } else if (serverEntry.what() == null) {
            var updatedEntry = new GridInventoryEntry(
                serverEntry.serial(),
                localEntry.what(),
                serverEntry.storedAmount(),
                serverEntry.requestableAmount(),
                serverEntry.craftable());
            entries.put(serverEntry.serial(), updatedEntry);
            replaceEntryByItemId(localEntry, updatedEntry);
        } else {
            entries.put(serverEntry.serial(), serverEntry);
            replaceEntryByItemId(localEntry, serverEntry);
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
        this.visibleCraftingPinKeys = PinnedKeys.getVisibleCraftingPinnedKeys(this.rowSize);
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

            ObjectList<GridInventoryEntry> entriesToAdd = new ObjectArrayList<>(this.entries.size());

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
            this.view.ensureCapacity(this.entries.size());
            this.craftingPinnedEntries.ensureCapacity(this.entries.size());
            this.playerPinnedEntries.ensureCapacity(this.entries.size());

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
        for (var entry : entries) {
            if (this.visibleCraftingPinKeys.contains(entry.what())) {
                craftingPinnedEntries.add(entry);
            }
        }

        for (var entry : entries) {
            if (this.visibleCraftingPinKeys.contains(entry.what())) {
                continue;
            }

            if (PinnedKeys.isPlayerPinned(entry.what())) {
                if (shouldUsePlayerPinForCurrentView(entry)) {
                    playerPinnedEntries.add(entry);
                    continue;
                }
            }

            if (passesViewFilters(entry)) {
                this.view.add(entry);
            }
        }
    }

    private boolean passesViewFilters(GridInventoryEntry entry) {
        AEKey what = Objects.requireNonNull(entry.what());

        if (this.partitionList != null && !this.partitionList.isListed(what)) {
            return false;
        }

        var viewMode = this.sortSrc.getSortDisplay();
        if (viewMode == ViewItems.CRAFTABLE && !entry.craftable()) {
            return false;
        }

        if (viewMode == ViewItems.STORED && entry.storedAmount() == 0) {
            return false;
        }

        if (!this.sortSrc.getSortKeyTypes().contains(what.getType())) {
            return false;
        }

        return search.matches(entry);
    }

    private boolean shouldUsePlayerPinForCurrentView(GridInventoryEntry entry) {
        if (!passesViewFilters(entry)) {
            return false;
        }

        AEKey what = Objects.requireNonNull(entry.what());
        int slotIndex = PinnedKeys.getPlayerPinSlotIndex(what);
        if (slotIndex < 0) {
            return false;
        }

        if (slotIndex / this.rowSize >= PinnedKeys.getPlayerPinRows()) {
            return false;
        }

        if (this.sortSrc.getPinDisplayMode() != PinDisplayMode.LOCKED_GRID) {
            return true;
        }

        return slotIndex / this.rowSize < getVisiblePlayerPinRowsForCurrentView();
    }

    private int getVisiblePlayerPinRowsForCurrentView() {
        int craftingRows = getRowsFor(craftingPinnedEntries.size());
        int maxPlayerRows = Math.max(0, this.terminalRows - 1 - craftingRows);
        return Math.min(PinnedKeys.getPlayerPinRows(), Math.min(PinnedKeys.MAX_PLAYER_PIN_ROWS, maxPlayerRows));
    }

    private void rebuildPinnedSlots() {
        this.pinnedSlots.clear();
        this.pinnedSlotUserSlotIndexes.clear();
        this.scrollableUserPinSlots.clear();
        this.scrollableUserPinSlotIndexes.clear();
        this.pinnedReasons.clear();

        int craftingRows = getRowsFor(craftingPinnedEntries.size());
        this.configuredPlayerPinRows = PinnedKeys.getPlayerPinRows();
        boolean lockedGridPins = this.sortSrc.getPinDisplayMode() == PinDisplayMode.LOCKED_GRID;
        this.visiblePlayerPinRows = lockedGridPins ? getVisiblePlayerPinRowsForCurrentView() : 0;
        this.pinnedRowCount = craftingRows + this.visiblePlayerPinRows;
        int pinnedSlotCount = this.pinnedRowCount * this.rowSize;
        int playerSlotCount = this.configuredPlayerPinRows * this.rowSize;
        int scrollableUserPinSlotCount = lockedGridPins ? 0 : playerSlotCount;
        this.pinnedSlots.ensureCapacity(pinnedSlotCount);
        this.pinnedSlotUserSlotIndexes.ensureCapacity(pinnedSlotCount);
        this.scrollableUserPinSlots.ensureCapacity(scrollableUserPinSlotCount);
        this.scrollableUserPinSlotIndexes.ensureCapacity(scrollableUserPinSlotCount);

        for (GridInventoryEntry entry : craftingPinnedEntries) {
            if (pinnedSlots.size() >= craftingRows * rowSize) {
                break;
            }
            pinnedSlots.add(entry);
            pinnedSlotUserSlotIndexes.add(NO_USER_PIN_SLOT);
            pinnedReasons.put(entry.what(), PinnedKeys.PinReason.CRAFTING);
        }
        while (pinnedSlots.size() < craftingRows * rowSize) {
            pinnedSlots.add(null);
            pinnedSlotUserSlotIndexes.add(NO_USER_PIN_SLOT);
        }

        if (lockedGridPins) {
            int visiblePlayerSlotCount = this.visiblePlayerPinRows * this.rowSize;
            for (int slotIndex = 0; slotIndex < visiblePlayerSlotCount; slotIndex++) {
                addFixedUserPinSlot(slotIndex);
            }
        } else {
            for (int slotIndex = 0; slotIndex < playerSlotCount; slotIndex++) {
                addScrollableUserPinSlot(slotIndex);
            }
        }

        while (pinnedSlots.size() < this.pinnedRowCount * rowSize) {
            pinnedSlots.add(null);
            pinnedSlotUserSlotIndexes.add(NO_USER_PIN_SLOT);
        }
    }

    private void addFixedUserPinSlot(int slotIndex) {
        GridInventoryEntry entry = getVisibleUserPinEntry(slotIndex);
        pinnedSlots.add(entry);
        pinnedSlotUserSlotIndexes.add(slotIndex);
    }

    private void addScrollableUserPinSlot(int slotIndex) {
        scrollableUserPinSlots.add(getVisibleUserPinEntry(slotIndex));
        scrollableUserPinSlotIndexes.add(slotIndex);
    }

    @Nullable
    private GridInventoryEntry getVisibleUserPinEntry(int slotIndex) {
        PinnedKeys.PlayerPin pin = PinnedKeys.getPlayerPinSlot(slotIndex);
        if (pin == null || this.visibleCraftingPinKeys.contains(pin.key())) {
            return null;
        }

        GridInventoryEntry entry = findPinnedEntry(playerPinnedEntries, pin.key());
        if (entry != null) {
            pinnedReasons.put(pin.key(), PinnedKeys.PinReason.PLAYER);
            return entry;
        }

        GridInventoryEntry fakeEntry = new GridInventoryEntry(-1, pin.key(), 0, 0, false);
        if (passesViewFilters(fakeEntry)) {
            pinnedReasons.put(pin.key(), PinnedKeys.PinReason.PLAYER);
            return fakeEntry;
        }
        return null;
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
        Map<AEKey, IntList> freeSlots = new Object2ObjectOpenHashMap<>();

        for (int i = 0; i < slots.size(); ++i) {
            var entry = slots.get(i);
            if (entry == null) {
                continue;
            }
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
        ObjectArrayList<GridInventoryEntry> entries = new ObjectArrayList<>(this.pinnedSlots.size());
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

        idx = getScrollableIndex(idx);

        if (idx < this.scrollableUserPinSlots.size()) {
            return this.scrollableUserPinSlots.get(idx);
        }
        idx -= this.scrollableUserPinSlots.size();
        if (idx >= this.view.size()) {
            return null;
        }
        return this.view.get(idx);
    }

    private int getScrollableIndex(int visibleOffset) {
        return visibleOffset + this.src.getCurrentScroll() * this.rowSize;
    }

    public final int size() {
        return this.view.size() + this.scrollableUserPinSlots.size() + this.pinnedRowCount * this.rowSize;
    }

    public final int getScrollableSize() {
        return this.view.size() + this.scrollableUserPinSlots.size();
    }

    public final void clear() {
        this.entries.clear();
        this.view.clear();
        this.pinnedSlots.clear();
        this.pinnedSlotUserSlotIndexes.clear();
        this.scrollableUserPinSlots.clear();
        this.scrollableUserPinSlotIndexes.clear();
        this.craftingPinnedEntries.clear();
        this.playerPinnedEntries.clear();
        this.pinnedReasons.clear();
        this.visibleCraftingPinKeys = Set.of();
        this.pinnedRowCount = 0;
        this.visiblePlayerPinRows = 0;
        this.configuredPlayerPinRows = 0;
        this.entriesByItemId.clear();
    }

    public final boolean hasPinnedRow() {
        return this.pinnedRowCount > 0;
    }

    public final int getPinnedRowCount() {
        return this.pinnedRowCount;
    }

    public final int getPlayerPinCapacity() {
        return PinnedKeys.getPlayerPinRows() * this.rowSize;
    }

    public final boolean canRemoveEmptyPlayerPinRow() {
        return PinnedKeys.canRemoveEmptyPlayerPinRow(this.rowSize);
    }

    @Nullable
    public final PinnedKeys.PinReason getPinReason(GridInventoryEntry entry) {
        return entry != null && entry.what() != null ? this.pinnedReasons.get(entry.what()) : null;
    }

    public final boolean isUserPinSlot(int idx) {
        return getUserPinSlotIndex(idx) >= 0;
    }

    public final boolean isEmptyUserPinSlot(int idx) {
        int slotIndex = getUserPinSlotIndex(idx);
        return slotIndex >= 0 && PinnedKeys.getPlayerPinSlot(slotIndex) == null;
    }

    public final int getUserPinSlotIndex(int idx) {
        int fixedSlots = this.pinnedRowCount * this.rowSize;
        if (idx < fixedSlots) {
            if (idx >= 0 && idx < this.pinnedSlotUserSlotIndexes.size()) {
                return this.pinnedSlotUserSlotIndexes.getInt(idx);
            }
            return NO_USER_PIN_SLOT;
        }

        int scrollableIndex = getScrollableIndex(idx - fixedSlots);
        if (scrollableIndex >= 0 && scrollableIndex < this.scrollableUserPinSlotIndexes.size()) {
            return this.scrollableUserPinSlotIndexes.getInt(scrollableIndex);
        }
        return NO_USER_PIN_SLOT;
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
        return entriesByItemId.getOrDefault(itemId, ObjectLists.emptyList());
    }

    private void replaceEntryByItemId(GridInventoryEntry oldEntry, GridInventoryEntry newEntry) {
        if (oldEntry == newEntry) {
            return;
        }
        removeEntryByItemId(oldEntry);
        addEntryByItemId(newEntry);
    }

    private void addEntryByItemId(GridInventoryEntry entry) {
        if (!(entry.what() instanceof AEItemKey itemKey)) {
            return;
        }
        this.entriesByItemId.computeIfAbsent(Item.getIdFromItem(itemKey.getItem()), ignored -> new ObjectArrayList<>(1))
                            .add(entry);
    }

    private void removeEntryByItemId(GridInventoryEntry entry) {
        if (!(entry.what() instanceof AEItemKey itemKey)) {
            return;
        }
        int itemId = Item.getIdFromItem(itemKey.getItem());
        var currentList = this.entriesByItemId.get(itemId);
        if (currentList == null) {
            return;
        }
        currentList.remove(entry);
        if (currentList.isEmpty()) {
            this.entriesByItemId.remove(itemId);
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

