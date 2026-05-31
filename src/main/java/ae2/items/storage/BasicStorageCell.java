/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2018, AlgorithmX2, All rights reserved.
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

package ae2.items.storage;

import ae2.api.config.FuzzyMode;
import ae2.api.stacks.AEKeyType;
import ae2.api.storage.StorageCells;
import ae2.api.storage.cells.CellState;
import ae2.api.storage.cells.IBasicCellItem;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.UpgradeInventories;
import ae2.core.localization.PlayerMessages;
import ae2.items.AEBaseItem;
import ae2.items.contents.CellConfig;
import ae2.recipes.game.StorageCellDisassemblyRecipe;
import ae2.util.ConfigInventory;
import ae2.util.InteractionUtil;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

import java.util.List;
import java.util.Set;

public class BasicStorageCell extends AEBaseItem implements IBasicCellItem {
    private static final String STORAGE_CELL_FUZZY_MODE = "storage_cell_fuzzy_mode";

    protected final double idleDrain;
    protected final int totalBytes;
    protected final int bytesPerType;
    protected final int totalTypes;
    private final AEKeyType keyType;

    public BasicStorageCell(double idleDrain, int kilobytes, int bytesPerType, int totalTypes, AEKeyType keyType) {
        this.setMaxStackSize(1);
        this.idleDrain = idleDrain;
        this.totalBytes = kilobytes * 1024;
        this.bytesPerType = bytesPerType;
        this.totalTypes = totalTypes;
        this.keyType = keyType;
    }

    public static int getColor(ItemStack stack, int tintIndex) {
        if (tintIndex == 1) {
            var cellInv = StorageCells.getCellInventory(stack, null);
            var cellStatus = cellInv != null ? cellInv.getStatus() : CellState.EMPTY;
            return cellStatus.getStateColor();
        } else {
            return 0xFFFFFF;
        }
    }

    @Override
    protected void addCheckedInformation(final ItemStack stack, final World world, final List<String> lines,
                                         final ITooltipFlag advancedTooltips) {
        addToTooltip(stack, lines);
    }

    @Override
    public AEKeyType getKeyType() {
        return this.keyType;
    }

    @Override
    public int getBytes(ItemStack cellItem) {
        return this.totalBytes;
    }

    @Override
    public int getTotalTypes(ItemStack cellItem) {
        return totalTypes;
    }

    @Override
    public double getIdleDrain() {
        return idleDrain;
    }

    @Override
    public int getBytesPerType(ItemStack cellItem) {
        return bytesPerType;
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack is) {
        return UpgradeInventories.forItem(is, keyType == AEKeyType.items() ? 4 : 3);
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack is) {
        return CellConfig.create(Set.of(keyType), is);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        NBTTagCompound tag = is.getTagCompound();
        if (tag != null && tag.hasKey(STORAGE_CELL_FUZZY_MODE, 8)) {
            try {
                return FuzzyMode.valueOf(tag.getString(STORAGE_CELL_FUZZY_MODE));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
        NBTTagCompound tag = is.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            is.setTagCompound(tag);
        }
        tag.setString(STORAGE_CELL_FUZZY_MODE, fzMode.name());
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World level, EntityPlayer player, EnumHand hand) {
        this.disassembleDrive(player.getHeldItem(hand), level, player);
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    private boolean disassembleDrive(ItemStack stack, World level, EntityPlayer player) {
        if (!InteractionUtil.isInAlternateUseMode(player)) {
            return false;
        }

        var disassembledStacks = StorageCellDisassemblyRecipe.getDisassemblyResult(level, stack.getItem());
        if (disassembledStacks.isEmpty()) {
            return false;
        }

        var playerInventory = player.inventory;
        if (playerInventory.getCurrentItem() != stack) {
            return false;
        }

        var inv = StorageCells.getCellInventory(stack, null);
        if (inv != null && !inv.getAvailableStacks().isEmpty()) {
            player.sendStatusMessage(PlayerMessages.OnlyEmptyCellsCanBeDisassembled.text(), true);
            return false;
        }

        playerInventory.setInventorySlotContents(playerInventory.currentItem, ItemStack.EMPTY);

        for (var disassembledStack : disassembledStacks) {
            playerInventory.placeItemBackInInventory(level, disassembledStack.copy());
        }

        getUpgrades(stack).forEach(upgrade -> playerInventory.placeItemBackInInventory(level, upgrade));

        return true;
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, net.minecraft.util.math.BlockPos pos,
                                           EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        return this.disassembleDrive(player.getHeldItem(hand), world, player)
            ? EnumActionResult.SUCCESS
            : EnumActionResult.PASS;
    }
}




