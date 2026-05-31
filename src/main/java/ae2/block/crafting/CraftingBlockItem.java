/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package ae2.block.crafting;

import ae2.block.AEBaseBlockItem;
import ae2.core.definitions.AEBlocks;
import ae2.recipes.game.CraftingUnitTransformRecipe;
import ae2.util.InteractionUtil;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

public class CraftingBlockItem extends AEBaseBlockItem {
    public CraftingBlockItem(Block block) {
        super(block);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (!InteractionUtil.isInAlternateUseMode(player)) {
            return super.onItemRightClick(world, player, hand);
        }

        ItemStack held = player.getHeldItem(hand);
        ItemStack removedUpgrade = CraftingUnitTransformRecipe.getRemovedUpgrade(getBlock());
        if (removedUpgrade.isEmpty()) {
            return super.onItemRightClick(world, player, hand);
        }

        int itemCount = held.getCount();
        player.setHeldItem(hand, ItemStack.EMPTY);

        ItemStack returnedUpgrade = removedUpgrade.copy();
        returnedUpgrade.setCount(removedUpgrade.getCount() * itemCount);
        if (!player.inventory.addItemStackToInventory(returnedUpgrade)) {
            player.dropItem(returnedUpgrade, false);
        }

        ItemStack returnedUnits = AEBlocks.CRAFTING_UNIT.stack(itemCount);
        if (!player.inventory.addItemStackToInventory(returnedUnits)) {
            player.dropItem(returnedUnits, false);
        }

        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }
}
