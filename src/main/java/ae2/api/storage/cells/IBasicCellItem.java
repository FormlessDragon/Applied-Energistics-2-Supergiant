/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package ae2.api.storage.cells;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.core.localization.GuiText;
import ae2.items.storage.StorageCellTooltipComponent;
import ae2.me.cells.BasicCellHandler;
import ae2.util.CellWorkbenchFilter;
import com.google.common.base.Preconditions;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Implement this on any item to register a "basic cell", which is a cell that works similarly to AE2's own item and
 * fluid cells. There is no need to register an {@link ICellHandler} for such an item. AE2 automatically handles the
 * internals and NBT data, which is both nice, and bad for you!
 * <p/>
 * The standard AE implementation also only provides 1-63 Types.
 */
public interface IBasicCellItem extends ICellWorkbenchItem, IStackTooltipDataProvider {
    String CELL_RESTRICTION_TAG = "storage_cell_restriction";
    String CELL_RESTRICTION_AMOUNT_TAG = "amount";
    String CELL_RESTRICTION_TYPES_TAG = "types";

    /**
     * Basic cell items are limited to a single {@link AEKeyType}.
     */
    AEKeyType getKeyType();

    /**
     * The number of bytes that can be stored on this type of storage cell.
     * <p/>
     * It won't work if the return is not a multiple of 8. The limit is ({@link Integer#MAX_VALUE} + 1) / 8.
     *
     * @param cellItem item
     * @return number of bytes
     */
    int getBytes(ItemStack cellItem);

    /**
     * Determines the number of bytes used for any type included on the cell.
     *
     * @param cellItem item
     * @return number of bytes
     */
    int getBytesPerType(ItemStack cellItem);

    /**
     * Must be between 1 and 63, indicates how many types can be stored on this type of storage cell.
     *
     * @param cellItem item
     * @return number of types
     */
    int getTotalTypes(ItemStack cellItem);

    static long getAllocatedBytesForRestriction(long amount, int types, int amountPerByte, int bytesPerType) {
        long typeBytes = (long) types * bytesPerType;
        long amountBytes = amount / amountPerByte + (amount % amountPerByte == 0 ? 0 : 1);
        if (Long.MAX_VALUE - typeBytes < amountBytes) {
            return Long.MAX_VALUE;
        }
        return typeBytes + amountBytes;
    }

    default Optional<CellRestriction> getCellRestriction(ItemStack cellItem) {
        return Optional.ofNullable(getCellRestrictionOrNull(cellItem));
    }

    @Nullable
    default CellRestriction getCellRestrictionOrNull(ItemStack cellItem) {
        Preconditions.checkArgument(cellItem.getItem() == this);
        NBTTagCompound tag = cellItem.getTagCompound();
        if (tag == null || !tag.hasKey(CELL_RESTRICTION_TAG, 10)) {
            return null;
        }

        NBTTagCompound restriction = tag.getCompoundTag(CELL_RESTRICTION_TAG);
        if (!restriction.hasKey(CELL_RESTRICTION_AMOUNT_TAG, 99)
            || !restriction.hasKey(CELL_RESTRICTION_TYPES_TAG, 99)) {
            return null;
        }

        long amount = restriction.getLong(CELL_RESTRICTION_AMOUNT_TAG);
        int types = restriction.getInteger(CELL_RESTRICTION_TYPES_TAG);
        if (amount < 0 || amount > getNativeAmountLimit(cellItem) || types < 0 || types > getTotalTypes(cellItem)) {
            return null;
        }

        long allocatedBytes = getAllocatedBytesForRestriction(amount, types, getKeyType().getAmountPerByte(),
            getBytesPerType(cellItem));
        if (allocatedBytes > getBytes(cellItem)) {
            return null;
        }

        return new CellRestriction(amount, types);
    }

    default void setCellRestriction(ItemStack cellItem, long amount, int types) {
        Preconditions.checkArgument(cellItem.getItem() == this);
        Preconditions.checkArgument(amount >= 0 && amount <= getNativeAmountLimit(cellItem));
        Preconditions.checkArgument(types >= 0 && types <= getTotalTypes(cellItem));
        Preconditions.checkArgument(getAllocatedBytesForRestriction(amount, types, getKeyType().getAmountPerByte(),
            getBytesPerType(cellItem)) <= getBytes(cellItem));

        NBTTagCompound tag = cellItem.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            cellItem.setTagCompound(tag);
        }

        NBTTagCompound restriction = new NBTTagCompound();
        restriction.setLong(CELL_RESTRICTION_AMOUNT_TAG, amount);
        restriction.setInteger(CELL_RESTRICTION_TYPES_TAG, types);
        tag.setTag(CELL_RESTRICTION_TAG, restriction);
    }

    default void clearCellRestriction(ItemStack cellItem) {
        Preconditions.checkArgument(cellItem.getItem() == this);
        NBTTagCompound tag = cellItem.getTagCompound();
        if (tag != null) {
            tag.removeTag(CELL_RESTRICTION_TAG);
        }
    }

    default boolean hasCellRestriction(ItemStack cellItem) {
        return getCellRestrictionOrNull(cellItem) != null;
    }

    default long getNativeAmountLimit(ItemStack cellItem) {
        return (long) getBytes(cellItem) * getKeyType().getAmountPerByte();
    }

    default long getEffectiveBytes(ItemStack cellItem) {
        CellRestriction restriction = getCellRestrictionOrNull(cellItem);
        if (restriction == null) {
            return getBytes(cellItem);
        }
        return getAllocatedBytesForRestriction(restriction.amount(), restriction.types(),
            getKeyType().getAmountPerByte(), getBytesPerType(cellItem));
    }

    default long getEffectiveTotalTypes(ItemStack cellItem) {
        CellRestriction restriction = getCellRestrictionOrNull(cellItem);
        return restriction != null ? restriction.types() : getTotalTypes(cellItem);
    }

    default Optional<Long> getEffectiveAmountLimit(ItemStack cellItem) {
        CellRestriction restriction = getCellRestrictionOrNull(cellItem);
        return restriction != null ? Optional.of(restriction.amount()) : Optional.empty();
    }

    default String getRestrictedDisplayName(ItemStack cellItem, String baseName) {
        if (!hasCellRestriction(cellItem)) {
            return baseName;
        }
        return baseName + " " + TextFormatting.GRAY + GuiText.RestrictedSuffix.getLocal() + TextFormatting.RESET;
    }

    /**
     * Allows you to fine tune which items are allowed on a given cell, if you don't care, just return false; As the
     * handler for this type of cell is still the default cells, the normal AE black list is also applied.
     *
     * @param cellItem          item
     * @param requestedAddition requested addition
     * @return true to preventAdditionOfItem
     */
    default boolean isBlackListed(ItemStack cellItem, AEKey requestedAddition) {
        return false;
    }

    default boolean isPartitionInverted(ItemStack cellItem) {
        return CellWorkbenchFilter.isInverted(cellItem, this);
    }

    default boolean isPartitionFuzzy(ItemStack cellItem) {
        return CellWorkbenchFilter.isFuzzy(cellItem, this);
    }

    default boolean isAllowedByCellWorkbench(ItemStack cellItem, AEKey requestedAddition) {
        if (isBlackListed(cellItem, requestedAddition)) {
            return false;
        }

        return CellWorkbenchFilter.matches(
            cellItem,
            this,
            requestedAddition,
            isPartitionInverted(cellItem),
            isPartitionFuzzy(cellItem));
    }

    /**
     * Allows you to specify if this storage cell can be stored inside other storage cells, only set this for special
     * items like the matter cannon that are not general purpose storage.
     *
     * @return true if the storage cell can be stored inside other storage cells, this is generally false, except for
     * certain situations such as the matter cannon.
     */
    default boolean storableInStorageCell() {
        return false;
    }

    /**
     * Allows an item to selectively enable or disable its status as a storage cell.
     *
     * @param i item
     * @return if the ItemStack should currently be usable as a storage cell.
     */
    default boolean isStorageCell(ItemStack i) {
        Preconditions.checkNotNull(i);
        return true;
    }

    /**
     * @return drain in ae/t this storage cell will use.
     */
    double getIdleDrain();

    record CellRestriction(long amount, int types) {
    }

    /**
     * Convenient helper to append useful tooltip information.
     */
    default void addCellInformationToTooltip(ItemStack is, List<String> lines) {
        Preconditions.checkArgument(is.getItem() == this);
        BasicCellHandler.INSTANCE.addCellInformationToTooltip(is, lines);
    }

    @Override
    default void addToTooltip(ItemStack is, List<String> lines) {
        addCellInformationToTooltip(is, lines);
    }

    @Override
    default Optional<StorageCellTooltipComponent> getStackTooltipData(ItemStack is) {
        Preconditions.checkArgument(is.getItem() == this);
        return BasicCellHandler.INSTANCE.getTooltipData(is);
    }
}
