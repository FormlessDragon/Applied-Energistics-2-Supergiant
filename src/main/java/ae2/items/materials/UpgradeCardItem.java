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

package ae2.items.materials;

import ae2.api.ids.AEItemIds;
import ae2.api.parts.IPartHost;
import ae2.api.parts.SelectedPart;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.IUpgradeableObject;
import ae2.api.upgrades.Upgrades;
import ae2.container.AEBaseContainer;
import ae2.container.SlotSemantics;
import ae2.container.slot.AppEngSlot;
import ae2.core.localization.GuiText;
import ae2.core.localization.InGameTooltip;
import ae2.core.localization.ItemTooltip;
import ae2.core.localization.PlayerMessages;
import ae2.items.AEBaseItem;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public class UpgradeCardItem extends AEBaseItem {
    private static final String FORCE_CRAFTING_TAG = "crafting_card_force_start";

    public static boolean isCraftingCard(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof UpgradeCardItem)) {
            return false;
        }
        return AEItemIds.CRAFTING_CARD.equals(stack.getItem().getRegistryName());
    }

    public static boolean isPseudoCraftingCard(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof UpgradeCardItem)) {
            return false;
        }
        return AEItemIds.PSEUDO_CRAFTING_CARD.equals(stack.getItem().getRegistryName());
    }

    public static boolean isForceCraftingEnabled(ItemStack stack) {
        if (!isCraftingCard(stack)) {
            return false;
        }
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.getBoolean(FORCE_CRAFTING_TAG);
    }

    public static void setForceCraftingEnabled(ItemStack stack, boolean enabled) {
        if (!isCraftingCard(stack)) {
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setBoolean(FORCE_CRAFTING_TAG, enabled);
    }

    private static void toggleForceCrafting(ItemStack stack) {
        setForceCraftingEnabled(stack, !isForceCraftingEnabled(stack));
    }

    @SideOnly(Side.CLIENT)
    @Override
    protected void addCheckedInformation(ItemStack stack, World world, List<String> lines, ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);

        if (isCraftingCard(stack)) {
            String status = isForceCraftingEnabled(stack)
                ? TextFormatting.GREEN + GuiText.Yes.getLocal()
                : TextFormatting.RED + GuiText.No.getLocal();
            lines.add(TextFormatting.GRAY + GuiText.CraftingCardForceCrafting.getLocal(status));
            lines.add(TextFormatting.DARK_GRAY + GuiText.CraftingCardForceCraftingHint.getLocal());
            lines.add(TextFormatting.DARK_GRAY + GuiText.CraftingCardForceCraftingDesc.getLocal());
        } else if (isPseudoCraftingCard(stack)) {
            lines.add(TextFormatting.GRAY + GuiText.PseudoCraftingCardLine1.getLocal());
            lines.add(TextFormatting.DARK_GRAY + GuiText.PseudoCraftingCardLine2.getLocal());
        } else if (AEItemIds.PARALLEL_CARD.equals(stack.getItem().getRegistryName())) {
            lines.add(TextFormatting.GRAY + ItemTooltip.ParallelCard.getLocal());
        }

        var supportedBy = Upgrades.getTooltipLinesForCard(this);
        if (!supportedBy.isEmpty()) {
            lines.add(InGameTooltip.supported_by.getLocal());
            for (var line : supportedBy) {
                lines.add(line.getFormattedText());
            }
        }
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        if (player.isSneaking()) {
            TileEntity tileEntity = world.getTileEntity(pos);
            IUpgradeInventory upgrades = null;

            if (tileEntity instanceof IPartHost partHost) {
                SelectedPart selectedPart = partHost.selectPartLocal(new Vec3d(hitX, hitY, hitZ));
                if (selectedPart.part instanceof IUpgradeableObject upgradeableObject) {
                    upgrades = upgradeableObject.getUpgrades();
                }
            } else if (tileEntity instanceof IUpgradeableObject upgradeableObject) {
                upgrades = upgradeableObject.getUpgrades();
            }

            if (upgrades != null && upgrades.size() > 0) {
                ItemStack heldStack = player.getHeldItem(hand);

                boolean isFull = true;
                for (int i = 0; i < upgrades.size(); i++) {
                    if (upgrades.getStackInSlot(i).isEmpty()) {
                        isFull = false;
                        break;
                    }
                }

                if (isFull) {
                    player.sendStatusMessage(PlayerMessages.MaxUpgradesInstalled.text(), true);
                    return EnumActionResult.FAIL;
                }

                int maxInstalled = upgrades.getMaxInstalled(heldStack.getItem());
                int installed = upgrades.getInstalledUpgrades(heldStack.getItem());
                if (maxInstalled <= 0) {
                    player.sendStatusMessage(PlayerMessages.UnsupportedUpgrade.text(), true);
                    return EnumActionResult.FAIL;
                } else if (installed >= maxInstalled) {
                    player.sendStatusMessage(PlayerMessages.MaxUpgradesOfTypeInstalled.text(), true);
                    return EnumActionResult.FAIL;
                }

                if (world.isRemote) {
                    return EnumActionResult.PASS;
                }

                player.setHeldItem(hand, upgrades.addItems(heldStack));
                return EnumActionResult.SUCCESS;
            }
        }

        return super.onItemUseFirst(player, world, pos, side, hitX, hitY, hitZ, hand);
    }

    @Override
    public boolean onOtherStackedOnMe(ItemStack stack, ItemStack otherStack, Slot slot, EntityPlayer player) {
        if (!otherStack.isEmpty() || !isCraftingCard(stack)) {
            return false;
        }
        if (!(slot instanceof AppEngSlot appEngSlot)) {
            return false;
        }
        if (!(appEngSlot.getContainer() instanceof AEBaseContainer container)) {
            return false;
        }
        if (container.getSlotSemantic(slot) != SlotSemantics.UPGRADE) {
            return false;
        }

        toggleForceCrafting(stack);
        slot.onSlotChanged();
        return true;
    }
}
