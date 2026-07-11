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
package ae2.block.crafting;

import ae2.api.networking.extensions.GridLogicExtensions;
import ae2.block.AEBaseTileBlock;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.tile.crafting.TilePatternProvider;
import ae2.util.InteractionUtil;
import ae2.util.Platform;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@SuppressWarnings("deprecation")
public class PatternProviderBlock extends AEBaseTileBlock<TilePatternProvider> {
    private static final PushDirection[] PUSH_DIRECTIONS = PushDirection.values();

    public static final PropertyEnum<PushDirection> PUSH_DIRECTION = PropertyEnum.create("push_direction",
        PushDirection.class);

    public PatternProviderBlock() {
        super(Material.IRON);
        setHardness(2.2F);
        setResistance(11.0F);
        setTileEntity(TilePatternProvider.class);
        this.setDefaultState(this.blockState.getBaseState().withProperty(PUSH_DIRECTION, PushDirection.ALL));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return createBlockState(PUSH_DIRECTION);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(PUSH_DIRECTION).ordinal();
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        int index = meta >= 0 && meta < PUSH_DIRECTIONS.length ? meta : PushDirection.ALL.ordinal();
        return this.getDefaultState().withProperty(PUSH_DIRECTION, PUSH_DIRECTIONS[index]);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        super.neighborChanged(state, world, pos, blockIn, fromPos);
        TilePatternProvider tile = this.getTileEntity(world, pos);
        if (tile != null) {
            tile.getLogic().invalidateTargetCaches();
            tile.getLogic().updateRedstoneState();
            var side = GridLogicExtensions.getNeighborSide(pos, fromPos);
            if (side != null && !world.isRemote) {
                tile.getLogic().onNeighborChanged(side);
            }
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ)) {
            return true;
        }

        TilePatternProvider tile = this.getTileEntity(world, pos);
        if (tile == null) {
            return false;
        }

        ItemStack heldItem = player.getHeldItem(hand);
        if (!heldItem.isEmpty() && InteractionUtil.canWrenchRotate(player, heldItem, pos)) {
            if (!world.isRemote) {
                this.setSide(world, pos, facing);
            }
            return true;
        }

        if (!world.isRemote) {
            tile.openGui(player, GuiHostLocators.forTile(tile));
        }
        return true;
    }

    public void setSide(World world, BlockPos pos, EnumFacing facing) {
        IBlockState currentState = world.getBlockState(pos);
        EnumFacing pushSide = currentState.getValue(PUSH_DIRECTION).getDirection();

        PushDirection newPushDirection;
        if (pushSide == facing.getOpposite()) {
            newPushDirection = PushDirection.fromDirection(facing);
        } else if (pushSide == facing) {
            newPushDirection = PushDirection.ALL;
        } else if (pushSide == null) {
            newPushDirection = PushDirection.fromDirection(facing.getOpposite());
        } else {
            newPushDirection = PushDirection.fromDirection(Platform.rotateAround(pushSide, facing));
        }

        world.setBlockState(pos, currentState.withProperty(PUSH_DIRECTION, newPushDirection), 3);
        TilePatternProvider tile = this.getTileEntity(world, pos);
        if (tile != null) {
            tile.getLogic().invalidateTargetCaches();
            tile.onPushDirectionChanged();
        }
    }

}
