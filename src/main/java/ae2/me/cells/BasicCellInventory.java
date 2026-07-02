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

package ae2.me.cells;

import ae2.api.config.Actionable;
import ae2.api.config.IncludeExclude;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKey2LongMap;
import ae2.api.stacks.AEKeyTypes;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.cells.CellState;
import ae2.api.storage.cells.IBasicCellItem;
import ae2.api.storage.cells.ISaveProvider;
import ae2.api.storage.cells.StorageCell;
import ae2.core.definitions.AEItems;
import ae2.text.TextComponentItemStack;
import ae2.util.CellWorkbenchFilter;
import ae2.util.prioritylist.IPartitionList;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.Objects;

public class BasicCellInventory implements StorageCell {
    private static final String STORAGE_CELL_INV_TAG = "storage_cell_inv";
    private static final String ITEM_COUNT_TAG = "ic";
    private static final String ITEM_SLOT_TAG = "it";
    private static final String ITEM_SLOT_KEY_TAG = "k";
    private static final String ITEM_SLOT_AMOUNT_TAG = "a";
    private static final String ITEM_SLOT_TYPE_TAG = "t";
    private static final BigInteger LONG_MAX_VALUE = BigInteger.valueOf(Long.MAX_VALUE);

    private final ItemStack itemStack;
    private final IBasicCellItem cellType;
    private final ISaveProvider saveProvider;
    private final AEKey2LongMap cellItems = new AEKey2LongMap.OpenHashMap();
    private final long totalBytes;
    private final int bytesPerType;
    private final long totalItemTypes;
    private final int amountPerByte;
    private final long amountLimit;
    private final long maxItemsPerType;
    private final boolean partitionInverted;
    private final boolean partitionFuzzy;
    private final IncludeExclude partitionMode;
    private final IPartitionList partitionList;
    private long storedItemCount;
    private long storedItems;
    private boolean persisted = true;

    // The cell type's channel matches, so this cast is safe
    private BasicCellInventory(ItemStack itemStack, IBasicCellItem cellType, @Nullable ISaveProvider saveProvider) {
        this.itemStack = itemStack;
        this.cellType = cellType;
        this.saveProvider = saveProvider;
        this.partitionInverted = cellType.isPartitionInverted(itemStack);
        this.partitionFuzzy = cellType.isPartitionFuzzy(itemStack);
        this.partitionMode = CellWorkbenchFilter.getMode(this.partitionInverted);
        this.partitionList = CellWorkbenchFilter.createPartitionList(itemStack, cellType, this.partitionFuzzy);
        loadCellItems();
        var restriction = cellType.getCellRestrictionOrNull(itemStack);
        this.bytesPerType = cellType.getBytesPerType(itemStack);
        this.amountPerByte = cellType.getKeyType().getAmountPerByte();
        if (restriction != null) {
            this.totalBytes = IBasicCellItem.getAllocatedBytesForRestriction(restriction.amount(), restriction.types(),
                this.amountPerByte, getBytesPerType());
            this.totalItemTypes = restriction.types();
            this.amountLimit = restriction.amount();
        } else {
            this.totalBytes = cellType.getBytes(itemStack);
            this.totalItemTypes = cellType.getTotalTypes(itemStack);
            this.amountLimit = -1;
        }
        this.maxItemsPerType = calculateMaxItemsPerType();
    }

    public static boolean isCell(ItemStack stack) {
        return !stack.isEmpty()
            && stack.getItem() instanceof IBasicCellItem cellItem
            && cellItem.isStorageCell(stack);
    }

    @Nullable
    public static BasicCellInventory createInventory(ItemStack stack, @Nullable ISaveProvider saveProvider) {
        if (!isCell(stack)) {
// This is not an error. Items may decide to not be a storage cell temporarily.
            return null;
        }
        return new BasicCellInventory(stack, (IBasicCellItem) stack.getItem(), saveProvider);
    }

    private static long saturatedAdd(long left, long right) {
        if (left < 0 || right < 0) {
            return Long.MAX_VALUE;
        }
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long saturatedMultiply(long left, long right) {
        if (left <= 0 || right <= 0) {
            return 0;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }

    private static long saturatedCeilDividedMultiply(long value, long multiplier, long divisor) {
        if (value <= 0 || multiplier <= 0) {
            return 0;
        }
        if (divisor <= 0) {
            return Long.MAX_VALUE;
        }

        BigInteger result = BigInteger.valueOf(value)
                                      .multiply(BigInteger.valueOf(multiplier))
                                      .add(BigInteger.valueOf(divisor - 1))
                                      .divide(BigInteger.valueOf(divisor));
        return result.compareTo(LONG_MAX_VALUE) > 0 ? Long.MAX_VALUE : result.longValue();
    }

    private void saveCellItems() {
        NBTTagCompound tag = getOrCreateTag();
        tag.setLong(ITEM_COUNT_TAG, storedItemCount);
        NBTTagList legacyList = new NBTTagList();
        ObjectList<GenericStack> stacks = new ObjectArrayList<>(cellItems.size());
        for (Object2LongMap.Entry<AEKey> entry : cellItems.object2LongEntrySet()) {
            if (entry.getLongValue() <= 0) {
                continue;
            }
            stacks.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            NBTTagCompound itemTag = new NBTTagCompound();
            itemTag.setString(ITEM_SLOT_TYPE_TAG, entry.getKey().getType().getId().toString());
            itemTag.setTag(ITEM_SLOT_KEY_TAG, entry.getKey().toTag());
            itemTag.setLong(ITEM_SLOT_AMOUNT_TAG, entry.getLongValue());
            legacyList.appendTag(itemTag);
        }
        if (stacks.isEmpty()) {
            tag.removeTag(STORAGE_CELL_INV_TAG);
        } else {
            tag.setTag(STORAGE_CELL_INV_TAG, GenericStack.writeList(stacks));
        }
        tag.setTag(ITEM_SLOT_TAG, legacyList);
    }

    private NBTTagCompound getOrCreateTag() {
        if (!itemStack.hasTagCompound()) {
            itemStack.setTagCompound(new NBTTagCompound());
        }
        return Objects.requireNonNull(itemStack.getTagCompound());
    }

    private static long divideRoundingUp(long value, long divisor) {
        if (value <= 0) {
            return 0;
        }
        if (divisor <= 0) {
            return Long.MAX_VALUE;
        }
        return (value - 1) / divisor + 1;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (what == null || amount <= 0) {
            return 0;
        }

        long currentAmount = cellItems.getLong(what);
        if (currentAmount <= 0) {
            return 0;
        }

        long extracted = Math.min(amount, currentAmount);
        if (mode == Actionable.MODULATE) {
            long remaining = currentAmount - extracted;
            if (remaining > 0) {
                cellItems.put(what, remaining);
            } else {
                cellItems.removeLong(what);
                storedItems--;
            }
            storedItemCount = Math.max(0, storedItemCount - extracted);
            saveChanges();
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (Object2LongMap.Entry<AEKey> entry : cellItems.object2LongEntrySet()) {
            if (entry.getKey() != null && entry.getLongValue() > 0) {
                out.add(entry.getKey(), entry.getLongValue());
            }
        }
    }

    public KeyCounter getAvailableStacks() {
        var result = new KeyCounter();
        getAvailableStacks(result);
        return result;
    }

    @Override
    public ITextComponent getDescription() {
        return TextComponentItemStack.of(itemStack);
    }

    @Override
    public CellState getStatus() {
        if (storedItems == 0) {
            return CellState.EMPTY;
        }
        if (canHoldNewItem()) {
            return CellState.NOT_EMPTY;
        }
        if (getRemainingItemCount() > 0) {
            return CellState.TYPES_FULL;
        }
        return CellState.FULL;
    }

    @Override
    public double getIdleDrain() {
        return cellType.getIdleDrain();
    }

    @Override
    public boolean canFitInsideCell() {
        return cellType.storableInStorageCell() || (storedItems == 0 && storedItemCount == 0);
// if there is no ISaveProvider, store to NBT immediately
    }

    @Override
    public boolean isStickyStorageFor(AEKey what, IActionSource source) {
        if (!cellType.getUpgrades(itemStack).isInstalled(AEItems.STICKY_CARD.item())) {
            return false;
        }

        if (this.partitionList.isEmpty()) {
            return cellItems.containsKey(what);
        }
        return isAllowedByCellWorkbench(what);
    }

    @Override
    public void persist() {
        if (this.persisted) {
            return;
        }

        saveCellItems();
        this.persisted = true;
    }

    private void saveChanges() {
        recalculateStoredAmounts();
        this.persisted = false;
        if (saveProvider != null) {
            saveProvider.saveChanges();
        } else {
            persist();
        }
    }

    private void loadCellItems() {
        cellItems.clear();
        storedItemCount = 0;
        storedItems = 0;

        NBTTagCompound tag = getOrCreateTag();
        if (tag.hasKey(STORAGE_CELL_INV_TAG, 9)) {
            for (GenericStack stack : GenericStack.readList(tag.getTagList(STORAGE_CELL_INV_TAG, 10))) {
                if (stack != null && stack.amount() > 0) {
                    addLoadedCellItem(stack.what(), stack.amount());
                }
            }
            recalculateStoredAmounts();
            return;
        }

        if (!tag.hasKey(ITEM_SLOT_TAG, 9)) {
            return;
        }

        NBTTagList list = tag.getTagList(ITEM_SLOT_TAG, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            readLegacyCellItem(entry);
        }
        recalculateStoredAmounts();
    }

    private void readLegacyCellItem(NBTTagCompound entry) {
        try {
            if (!entry.hasKey(ITEM_SLOT_TYPE_TAG, 8) || !entry.hasKey(ITEM_SLOT_KEY_TAG, 10)) {
                return;
            }
            var keyType = AEKeyTypes.get(new ResourceLocation(entry.getString(ITEM_SLOT_TYPE_TAG)));
            AEKey key = keyType.loadKeyFromTag(entry.getCompoundTag(ITEM_SLOT_KEY_TAG));
            long amount = entry.getLong(ITEM_SLOT_AMOUNT_TAG);
            if (key != null && amount > 0) {
                addLoadedCellItem(key, amount);
            }
        } catch (RuntimeException ignored) {
            // Skip malformed legacy entries instead of making the whole cell unreadable.
        }
    }

    private void addLoadedCellItem(AEKey key, long amount) {
        if (key == null || amount <= 0 || !cellType.getKeyType().contains(key)) {
            return;
        }
        long currentAmount = cellItems.getLong(key);
        long newAmount = saturatedAdd(currentAmount, amount);
        cellItems.put(key, newAmount);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (what == null || amount <= 0 || !cellType.getKeyType().contains(what)) {
            return 0;
        }
        if (!isAllowedByCellWorkbench(what)) {
            return 0;
        }

        long currentAmount = cellItems.getLong(what);
        long remainingItemCount = getRemainingItemCountByBytes();
        if (currentAmount <= 0) {
            if (!canHoldNewItem()) {
                return 0;
            }
            remainingItemCount -= saturatedMultiply(getBytesPerType(), this.amountPerByte);
            if (remainingItemCount <= 0) {
                return 0;
            }
        }
        remainingItemCount = clampByAmountLimit(remainingItemCount);
        remainingItemCount = Math.clamp(this.maxItemsPerType - currentAmount, 0, remainingItemCount);

        long inserted = Math.min(amount, remainingItemCount);
        if (mode == Actionable.MODULATE && inserted > 0) {
            cellItems.addTo(what, inserted);
            storedItemCount = saturatedAdd(storedItemCount, inserted);
            if (currentAmount <= 0) {
                storedItems++;
            }
            saveChanges();
        }
        return inserted;
    }

    private void recalculateStoredAmounts() {
        this.storedItemCount = 0;
        this.storedItems = 0;
        ObjectList<AEKey> emptyKeys = new ObjectArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : cellItems.object2LongEntrySet()) {
            if (entry.getKey() == null) {
                emptyKeys.add(entry.getKey());
                continue;
            }
            long amount = entry.getLongValue();
            if (amount > 0) {
                this.storedItemCount = saturatedAdd(this.storedItemCount, amount);
                this.storedItems++;
            } else {
                emptyKeys.add(entry.getKey());
            }
        }
        for (AEKey emptyKey : emptyKeys) {
            cellItems.removeLong(emptyKey);
        }
    }

    private boolean canHoldNewItem() {
        return getStoredItemTypes() < getTotalItemTypes() && getRemainingItemCount() > 0;
    }

    private long calculateMaxItemsPerType() {
        if (!cellType.getUpgrades(itemStack).isInstalled(AEItems.EQUAL_DISTRIBUTION_CARD.item())) {
            return Long.MAX_VALUE;
        }

        long maxTypes = getTotalItemTypes();
        if (!this.partitionFuzzy && !this.partitionInverted && !this.partitionList.isEmpty()) {
            maxTypes = Math.min(maxTypes, getPartitionedTypeCount());
        }
        if (maxTypes <= 0) {
            return 0;
        }

        long typeBytes = saturatedMultiply(getBytesPerType(), maxTypes);
        long storageForAmounts = Math.max(getTotalBytes() - typeBytes, 0);
        return saturatedCeilDividedMultiply(storageForAmounts, this.amountPerByte, maxTypes);
    }

    private long getPartitionedTypeCount() {
        long count = 0;
        for (AEKey ignored : this.partitionList.getItems()) {
            count++;
        }
        return count;
    }

    private boolean isAllowedByCellWorkbench(AEKey requestedAddition) {
        if (cellType.isBlackListed(itemStack, requestedAddition)) {
            return false;
        }
        return this.partitionList.matchesFilter(requestedAddition, this.partitionMode);
    }

    public long getTotalBytes() {
        return this.totalBytes;
    }

    public int getBytesPerType() {
        return this.bytesPerType;
    }

    public long getTotalItemTypes() {
        return this.totalItemTypes;
    }

    public long getStoredItemCount() {
        return storedItemCount;
    }

    public long getStoredItemTypes() {
        return storedItems;
    }

    public long getUsedBytes() {
        long bytesForItemCount = divideRoundingUp(getStoredItemCount(), this.amountPerByte);
        return saturatedAdd(saturatedMultiply(getStoredItemTypes(), getBytesPerType()), bytesForItemCount);
    }

    public long getRemainingItemCount() {
        return clampByAmountLimit(getRemainingItemCountByBytes());
    }

    private long getRemainingItemCountByBytes() {
        long freeBytes = getTotalBytes() - getUsedBytes();
        if (freeBytes <= 0) {
            return 0;
        }
        long remaining = saturatedAdd(saturatedMultiply(freeBytes, this.amountPerByte), getUnusedItemCount());
// Technically not exactly evenly distributed, but close enough!
        return Math.max(remaining, 0);
    }

    private long clampByAmountLimit(long remainingByBytes) {
        if (this.amountLimit < 0) {
            return remainingByBytes;
        }
        long remainingByLimit = this.amountLimit - getStoredItemCount();
        var b = Math.min(remainingByBytes, remainingByLimit);
        return Math.max(b, 0);
    }

    public int getUnusedItemCount() {
        final int div = (int) (getStoredItemCount() % this.amountPerByte);
        if (div == 0) {
            return 0;
        }
        return this.amountPerByte - div;
    }
}
