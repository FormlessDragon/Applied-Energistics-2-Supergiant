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

package ae2.items.parts;

import ae2.api.features.P2PTunnelAttunementInternal;
import ae2.api.parts.IPart;
import ae2.api.parts.IPartItem;
import ae2.api.parts.PartHelper;
import ae2.core.localization.InGameTooltip;
import ae2.items.AEBaseItem;
import ae2.parts.p2p.P2PTunnelPart;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;
import java.util.function.Function;

public class PartItem<T extends IPart> extends AEBaseItem implements IPartItem<T> {

    private final Class<T> partClass;
    private final Function<IPartItem<T>, T> factory;

    public PartItem(Class<T> partClass, Function<IPartItem<T>, T> factory) {
        this.partClass = partClass;
        this.factory = factory;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing side,
                                      float hitX, float hitY, float hitZ) {
        return PartHelper.usePartItem(player.getHeldItem(hand), player, world, pos, hand, side, hitX, hitY, hitZ);
    }

    @Override
    public Class<T> getPartClass() {
        return this.partClass;
    }

    @SideOnly(Side.CLIENT)
    @Override
    protected void addCheckedInformation(ItemStack stack, World world, List<String> lines,
                                         ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);
        if (P2PTunnelPart.class.isAssignableFrom(this.partClass)) {
            lines.add(P2PTunnelAttunementInternal.supportsMultipleInputs(this)
                ? InGameTooltip.P2PMultipleInputs.getLocal()
                : InGameTooltip.P2PSingleInput.getLocal());
        }
    }

    @Override
    public T createPart() {
        return this.factory.apply(this);
    }
}
