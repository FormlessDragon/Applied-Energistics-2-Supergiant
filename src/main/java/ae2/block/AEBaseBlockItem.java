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

package ae2.block;

import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AEBaseBlockItem extends ItemBlock {

    private final AEBaseBlock blockType;

    public AEBaseBlockItem(Block id) {
        super(id);
        this.blockType = (AEBaseBlock) id;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public final void addInformation(ItemStack itemStack, @Nullable World worldIn,
                                     List<String> toolTip, ITooltipFlag advancedTooltips) {
        this.addCheckedInformation(itemStack, worldIn, toolTip, advancedTooltips);
    }

    @SideOnly(Side.CLIENT)
    public void addCheckedInformation(ItemStack itemStack, @Nullable World worldIn,
                                      List<String> toolTip, ITooltipFlag advancedTooltips) {
    }

    @Override
    public boolean isBookEnchantable(ItemStack itemstack1, ItemStack itemstack2) {
        return false;
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (this.isInCreativeTab(tab)) {
            this.blockType.getSubBlocks(tab, items);
        }
    }

    @Override
    public String getTranslationKey(ItemStack is) {
        return this.blockType.getTranslationKey();
    }

    public AEBaseBlock getBlockType() {
        return this.blockType;
    }
}

