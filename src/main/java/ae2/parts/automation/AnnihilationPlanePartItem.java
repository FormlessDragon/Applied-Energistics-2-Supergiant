/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package ae2.parts.automation;

import ae2.core.localization.GuiText;
import ae2.items.parts.PartItem;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public class AnnihilationPlanePartItem extends PartItem<AnnihilationPlanePart> {
    public AnnihilationPlanePartItem() {
        super(AnnihilationPlanePart.class, AnnihilationPlanePart::new);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getItemEnchantability() {
        return 10;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return true;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return enchantment == Enchantments.UNBREAKING
            || enchantment == Enchantments.FORTUNE
            || enchantment == Enchantments.SILK_TOUCH
            || enchantment == Enchantments.EFFICIENCY;
    }

    @SideOnly(Side.CLIENT)
    @Override
    protected void addCheckedInformation(ItemStack stack, World world, List<String> lines, ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);

        if (EnchantmentHelper.getEnchantments(stack).isEmpty()) {
            lines.add(GuiText.CanBeEnchanted.getLocal());
        } else {
            lines.add(GuiText.IncreasedEnergyUseFromEnchants.getLocal());
        }
    }

    @Override
    protected void getCheckedSubItems(CreativeTabs creativeTab, NonNullList<ItemStack> itemStacks) {
        super.getCheckedSubItems(creativeTab, itemStacks);

        if (!this.isInCreativeTab(creativeTab)) {
            return;
        }

        ItemStack silkTouch = new ItemStack(this);
        silkTouch.addEnchantment(Enchantments.SILK_TOUCH, 1);
        itemStacks.add(silkTouch);
    }
}
