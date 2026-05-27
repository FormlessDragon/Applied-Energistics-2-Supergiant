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

package appeng.me.cells;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.core.definitions.AEItems;
import appeng.text.TextComponentItemStack;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class BasicCellInventory implements StorageCell {
    private static final String STORAGE_CELL_INV_TAG = "storage_cell_inv";
    private static final String ITEM_COUNT_TAG = "ic";
    private static final String ITEM_SLOT_TAG = "it";
    private static final String ITEM_SLOT_KEY_TAG = "k";
    private static final String ITEM_SLOT_AMOUNT_TAG = "a";
    private static final String ITEM_SLOT_TYPE_TAG = "t";

    private final ItemStack itemStack;
    private final IBasicCellItem cellType;
    private final ISaveProvider saveProvider;
    private final Object2LongMap<AEKey> cellItems = new Object2LongOpenHashMap<>();
    private long storedItemCount;
    private long storedItems;
    private boolean persisted = true;

    // The cell type's channel matches, so this cast is safe
    private BasicCellInventory(ItemStack itemStack, IBasicCellItem cellType, @Nullable ISaveProvider saveProvider) {
        this.itemStack = itemStack;
        this.cellType = cellType;
        this.saveProvider = saveProvider;
        loadCellItems();
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

    private void loadCellItems() {
        cellItems.clear();
        storedItemCount = 0;
        storedItems = 0;

        NBTTagCompound tag = getOrCreateTag();
        if (tag.hasKey(STORAGE_CELL_INV_TAG, 9)) {
            for (GenericStack stack : GenericStack.readList(tag.getTagList(STORAGE_CELL_INV_TAG, 10))) {
                if (stack != null && stack.amount() > 0) {
                    cellItems.put(stack.what(), stack.amount());
                    storedItemCount += stack.amount();
                    storedItems++;
                }
            }
            return;
        }

        if (!tag.hasKey(ITEM_SLOT_TAG, 9)) {
            return;
        }

        NBTTagList list = tag.getTagList(ITEM_SLOT_TAG, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            var keyType = AEKeyTypes.get(new net.minecraft.util.ResourceLocation(entry.getString(ITEM_SLOT_TYPE_TAG)));
            AEKey key = keyType.loadKeyFromTag(entry.getCompoundTag(ITEM_SLOT_KEY_TAG));
            long amount = entry.getLong(ITEM_SLOT_AMOUNT_TAG);
            if (key != null && amount > 0) {
                cellItems.put(key, amount);
                storedItemCount += amount;
                storedItems++;
            }
        }
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

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !cellType.getKeyType().contains(what)) {
            return 0;
        }
        if (!cellType.isAllowedByCellWorkbench(itemStack, what)) {
            return 0;
        }

        long currentAmount = cellItems.getLong(what);
        long remainingItemCount = getRemainingItemCount();
        if (currentAmount <= 0) {
            if (!canHoldNewItem()) {
                return 0;
            }
            remainingItemCount -= (long) getBytesPerType() * what.getAmountPerByte();
            if (remainingItemCount <= 0) {
                return 0;
            }
        }

        long inserted = Math.min(amount, remainingItemCount);
        if (mode == Actionable.MODULATE && inserted > 0) {
            cellItems.put(what, currentAmount + inserted);
            storedItemCount += inserted;
            if (currentAmount <= 0) {
                storedItems++;
            }
            saveChanges();
        }
        return inserted;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        long currentAmount = cellItems.getLong(what);
        if (amount <= 0 || currentAmount <= 0) {
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
            storedItemCount -= extracted;
            saveChanges();
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (Object2LongMap.Entry<AEKey> entry : cellItems.object2LongEntrySet()) {
            out.add(entry.getKey(), entry.getLongValue());
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

        var config = cellType.getConfigInventory(itemStack);
        if (config.isEmpty()) {
            return cellItems.containsKey(what);
        }
        return cellType.isAllowedByCellWorkbench(itemStack, what);
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

    private void recalculateStoredAmounts() {
        this.storedItemCount = 0;
        this.storedItems = 0;
        ObjectList<AEKey> emptyKeys = new ObjectArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : cellItems.object2LongEntrySet()) {
            long amount = entry.getLongValue();
            if (amount > 0) {
                this.storedItemCount += amount;
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

    public long getTotalBytes() {
        return cellType.getBytes(itemStack);
    }

    public int getBytesPerType() {
        return cellType.getBytesPerType(itemStack);
    }

    public long getTotalItemTypes() {
        return cellType.getTotalTypes(itemStack);
    }

    public long getStoredItemCount() {
        return storedItemCount;
    }

    public long getStoredItemTypes() {
        return storedItems;
    }

    public long getFreeBytes() {
        return getTotalBytes() - getUsedBytes();
    }

    public long getUsedBytes() {
        var bytesForItemCount = (getStoredItemCount() + getUnusedItemCount()) / cellType.getKeyType().getAmountPerByte();
        return getStoredItemTypes() * getBytesPerType() + bytesForItemCount;
    }

    public long getRemainingItemCount() {
        final long remaining = getFreeBytes() * cellType.getKeyType().getAmountPerByte() + getUnusedItemCount();
// Technically not exactly evenly distributed, but close enough!
        return Math.max(remaining, 0);
    }

    public int getUnusedItemCount() {
        final int div = (int) (getStoredItemCount() % cellType.getKeyType().getAmountPerByte());
        if (div == 0) {
            return 0;
        }
        return cellType.getKeyType().getAmountPerByte() - div;
    }
}
