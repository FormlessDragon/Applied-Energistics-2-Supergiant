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
package ae2.block.networking;

import ae2.api.orientation.IOrientationStrategy;
import ae2.api.orientation.OrientationStrategies;
import ae2.block.AEBaseTileBlock;
import ae2.container.GuiIds;
import ae2.core.gui.GuiOpener;
import ae2.tile.networking.TileWirelessAccessPoint;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Locale;

public class WirelessAccessPointBlock extends AEBaseTileBlock<TileWirelessAccessPoint> {

    public static final PropertyEnum<State> STATE = PropertyEnum.create("state", State.class);
    private static final AxisAlignedBB DOWN_AABB = new AxisAlignedBB(3.0 / 16.0, 5.0 / 16.0, 3.0 / 16.0,
        13.0 / 16.0, 1.0, 13.0 / 16.0);
    private static final AxisAlignedBB EAST_AABB = new AxisAlignedBB(0.0, 3.0 / 16.0, 3.0 / 16.0,
        11.0 / 16.0, 13.0 / 16.0, 13.0 / 16.0);
    private static final AxisAlignedBB NORTH_AABB = new AxisAlignedBB(3.0 / 16.0, 3.0 / 16.0, 5.0 / 16.0,
        13.0 / 16.0, 13.0 / 16.0, 1.0);
    private static final AxisAlignedBB SOUTH_AABB = new AxisAlignedBB(3.0 / 16.0, 3.0 / 16.0, 0.0,
        13.0 / 16.0, 13.0 / 16.0, 11.0 / 16.0);
    private static final AxisAlignedBB UP_AABB = new AxisAlignedBB(3.0 / 16.0, 0.0, 3.0 / 16.0,
        13.0 / 16.0, 11.0 / 16.0, 13.0 / 16.0);
    private static final AxisAlignedBB WEST_AABB = new AxisAlignedBB(5.0 / 16.0, 3.0 / 16.0, 3.0 / 16.0,
        1.0, 13.0 / 16.0, 13.0 / 16.0);

    public WirelessAccessPointBlock() {
        super(Material.IRON);
        this.setHardness(2.2F);
        this.setResistance(11.0F);
        this.setTileEntity(TileWirelessAccessPoint.class);
        this.setOpaque();
        this.setFullSize();
        this.setLightOpacity(0);
        this.setDefaultState(this.blockState.getBaseState().withProperty(STATE, State.OFF));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return createBlockState(STATE);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.facing();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ)) {
            return true;
        }

        TileWirelessAccessPoint tile = this.getTileEntity(world, pos);
        if (tile != null) {
            if (!world.isRemote) {
                GuiOpener.openGui(player, GuiIds.GuiKey.WIRELESS_ACCESS_POINT, tile);
            }
            return true;
        }

        return false;
    }

    @Override
    protected IBlockState updateBlockStateFromTileEntity(IBlockState currentState, TileWirelessAccessPoint tileEntity) {
        State nextState = State.OFF;
        if (tileEntity.isActive()) {
            nextState = State.HAS_CHANNEL;
        } else if (tileEntity.isPowered()) {
            nextState = State.ON;
        }
        return currentState.withProperty(STATE, nextState);
    }

    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileWirelessAccessPoint tile = this.getTileEntity(world, pos);
        if (tile == null) {
            return super.getActualState(state, world, pos).withProperty(STATE, State.OFF);
        }

        State nextState = State.OFF;
        if (tile.isActive()) {
            nextState = State.HAS_CHANNEL;
        } else if (tile.isPowered()) {
            nextState = State.ON;
        }

        return super.getActualState(state, world, pos).withProperty(STATE, nextState);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return super.getMetaFromState(state);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return super.getStateFromMeta(meta).withProperty(STATE, State.OFF);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return switch (this.getOrientationStrategy().getFacing(state)) {
            case DOWN -> DOWN_AABB;
            case EAST -> EAST_AABB;
            case NORTH -> NORTH_AABB;
            case SOUTH -> SOUTH_AABB;
            case WEST -> WEST_AABB;
            default -> UP_AABB;
        };
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return layer == BlockRenderLayer.CUTOUT;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockFaceShape getBlockFaceShape(IBlockAccess worldIn, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    public enum State implements IStringSerializable {
        OFF,
        ON,
        HAS_CHANNEL;

        @Override
        public String getName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

}
