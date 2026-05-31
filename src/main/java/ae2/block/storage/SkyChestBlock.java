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
package ae2.block.storage;

import ae2.api.orientation.IOrientationStrategy;
import ae2.api.orientation.OrientationStrategies;
import ae2.api.orientation.RelativeSide;
import ae2.block.AEBaseTileBlock;
import ae2.container.GuiIds;
import ae2.core.gui.GuiOpener;
import ae2.helpers.ICustomCollision;
import ae2.tile.storage.TileSkyChest;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.List;

@SuppressWarnings("deprecation")
public class SkyChestBlock extends AEBaseTileBlock<TileSkyChest> implements ICustomCollision {
    private static final double AABB_OFFSET_BOTTOM = 0.00;
    private static final double AABB_OFFSET_SIDES = 0.06;
    private static final double AABB_OFFSET_TOP = 0.125;
    public final SkyChestType type;

    public SkyChestBlock(SkyChestType type) {
        super(Material.ROCK);
        this.type = type;
        setOpaque();
        setFullSize();
        setHardness(50);
        setResistance(150.0f);
        setTileEntity(TileSkyChest.class);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.horizontalFacing();
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ)) {
            return true;
        }

        TileSkyChest tile = this.getTileEntity(world, pos);
        if (tile != null) {
            if (!world.isRemote) {
                GuiOpener.openGui(player, GuiIds.GuiKey.SKY_CHEST, tile);
            }
            return true;
        }
        return false;
    }

    @Override
    public Iterable<AxisAlignedBB> getSelectedBoundingBoxesFromPool(World world, BlockPos pos, Entity entity,
                                                                    boolean hitFluids) {
        return Collections.singletonList(computeAABB(world, pos));
    }

    @Override
    public void addCollidingBlockToList(World world, BlockPos pos, AxisAlignedBB bb, List<AxisAlignedBB> out,
                                        Entity entity) {
        out.add(computeAABB(world, pos));
    }

    private AxisAlignedBB computeAABB(World world, BlockPos pos) {
        TileSkyChest chest = this.getTileEntity(world, pos);
        EnumFacing up = chest != null ? chest.getOrientation().getSide(RelativeSide.TOP) : EnumFacing.UP;

        double offsetX = up.getXOffset() == 0 ? AABB_OFFSET_SIDES : 0.0;
        double offsetY = up.getYOffset() == 0 ? AABB_OFFSET_SIDES : 0.0;
        double offsetZ = up.getZOffset() == 0 ? AABB_OFFSET_SIDES : 0.0;

        double minX = Math.max(0.0,
            offsetX + (up.getXOffset() < 0 ? AABB_OFFSET_BOTTOM : up.getXOffset() * AABB_OFFSET_TOP));
        double minY = Math.max(0.0,
            offsetY + (up.getYOffset() < 0 ? AABB_OFFSET_TOP : up.getYOffset() * AABB_OFFSET_BOTTOM));
        double minZ = Math.max(0.0,
            offsetZ + (up.getZOffset() < 0 ? AABB_OFFSET_BOTTOM : up.getZOffset() * AABB_OFFSET_TOP));

        double maxX = Math.min(1.0,
            1.0 - offsetX - (up.getXOffset() < 0 ? AABB_OFFSET_TOP : up.getXOffset() * AABB_OFFSET_BOTTOM));
        double maxY = Math.min(1.0,
            1.0 - offsetY - (up.getYOffset() < 0 ? AABB_OFFSET_BOTTOM : up.getYOffset() * AABB_OFFSET_TOP));
        double maxZ = Math.min(1.0,
            1.0 - offsetZ - (up.getZOffset() < 0 ? AABB_OFFSET_TOP : up.getZOffset() * AABB_OFFSET_BOTTOM));

        return new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public enum SkyChestType {
        STONE, BLOCK
    }
}
