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
package ae2.block.misc;

import ae2.api.orientation.IOrientationStrategy;
import ae2.api.orientation.OrientationStrategies;
import ae2.api.orientation.RelativeSide;
import ae2.block.AEBaseTileBlock;
import ae2.client.render.effects.ParticleTypes;
import ae2.core.AEConfig;
import ae2.core.AppEngBase;
import ae2.tile.misc.TileGrowthAccelerator;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

import java.util.Random;

public class GrowthAcceleratorBlock extends AEBaseTileBlock<TileGrowthAccelerator> {
    public static final PropertyBool POWERED = PropertyBool.create("powered");

    public GrowthAcceleratorBlock() {
        super(Material.ROCK);
        this.setSoundType(SoundType.METAL);
        this.setHardness(2.2F);
        this.setResistance(11.0F);
        this.setTileEntity(TileGrowthAccelerator.class);
        this.setDefaultState(this.getDefaultState().withProperty(POWERED, Boolean.FALSE));
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.facing();
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return createBlockState(POWERED);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return super.getMetaFromState(state) | (state.getValue(POWERED) ? 8 : 0);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return super.getStateFromMeta(meta).withProperty(POWERED, (meta & 8) == 8);
    }

    @Override
    protected IBlockState updateBlockStateFromTileEntity(IBlockState currentState, TileGrowthAccelerator tileEntity) {
        return currentState.withProperty(POWERED, tileEntity.isPowered());
    }

    @Override
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random random) {
        if (!AEConfig.instance().isEnableEffects() || !AppEngBase.runtime().shouldAddParticles(random)) {
            return;
        }

        TileGrowthAccelerator tile = this.getTileEntity(world, pos);
        if (tile == null || !tile.isPowered()) {
            return;
        }

        final double d0 = random.nextFloat() - 0.5F;
        final double d1 = random.nextFloat() - 0.5F;

        var up = tile.getOrientation().getSide(RelativeSide.TOP);
        var forward = tile.getOrientation().getSide(RelativeSide.FRONT);
        Vec3i west = forward.getDirectionVec().crossProduct(up.getDirectionVec());

        double rx = 0.5 + pos.getX();
        double ry = 0.5 + pos.getY();
        double rz = 0.5 + pos.getZ();

        rx += up.getXOffset() * d0;
        ry += up.getYOffset() * d0;
        rz += up.getZOffset() * d0;

        double dz = 0;
        double dx = 0;
        BlockPos particlePos = null;

        switch (random.nextInt(4)) {
            case 0 -> {
                dx = 0.6;
                dz = d1;
                particlePos = pos.add(west.getX(), west.getY(), west.getZ());
            }
            case 1 -> {
                dx = d1;
                dz = 0.6;
                particlePos = pos.offset(forward);
            }
            case 2 -> {
                dx = d1;
                dz = -0.6;
                particlePos = pos.offset(forward.getOpposite());
            }
            case 3 -> {
                dx = -0.6;
                dz = d1;
                particlePos = pos.add(-west.getX(), -west.getY(), -west.getZ());
            }
            default -> {
            }
        }

        if (particlePos == null || !world.isAirBlock(particlePos)) {
            return;
        }

        rx += dx * west.getX();
        ry += dx * west.getY();
        rz += dx * west.getZ();

        rx += dz * forward.getXOffset();
        ry += dz * forward.getYOffset();
        rz += dz * forward.getZOffset();

        ParticleTypes.LIGHTNING.spawn(world, rx, ry, rz, 0.0D, 0.0D, 0.0D, null);
    }
}


