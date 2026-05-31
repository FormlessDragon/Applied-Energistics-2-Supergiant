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

package ae2.parts;

import ae2.api.parts.SelectedPart;
import ae2.api.util.AEColor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Random;

public interface ICableBusContainer {

    int isProvidingStrongPower(EnumFacing side);

    int isProvidingWeakPower(EnumFacing side);

    boolean canConnectRedstone(EnumFacing side);

    void onEntityCollision(Entity entity);

    boolean onUseItemOn(ItemStack heldItem, EntityPlayer player, EnumHand hand, Vec3d localPos);

    boolean onUseWithoutItem(EntityPlayer player, Vec3d localPos);

    boolean onWrenched(EntityPlayer player, Vec3d localPos);

    boolean onClicked(EntityPlayer player, Vec3d localPos);

    void onNeighborChanged(IBlockAccess level, BlockPos pos, BlockPos neighbor);

    void onUpdateShape(EnumFacing side);

    boolean isEmpty();

    SelectedPart selectPartLocal(Vec3d pos);

    boolean recolourBlock(EnumFacing side, AEColor colour, EntityPlayer who);

    boolean isLadder(EntityLivingBase entity);

    void randomDisplayTick(World world, BlockPos pos, Random random);

    int getLightValue();

    boolean isRequiresDynamicRender();

    Iterable<AxisAlignedBB> getBoxes(boolean includeFacades, @Nullable Entity entity, boolean visual);
}
