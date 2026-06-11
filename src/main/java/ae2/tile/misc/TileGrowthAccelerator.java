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
package ae2.tile.misc;

import ae2.api.config.Actionable;
import ae2.api.implementations.IPowerChannelState;
import ae2.api.implementations.blockentities.ICrankable;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.orientation.BlockOrientation;
import ae2.api.orientation.RelativeSide;
import ae2.api.util.AECableType;
import ae2.block.misc.GrowthAcceleratorBlock;
import ae2.core.AEConfig;
import ae2.core.definitions.AEBlocks;
import ae2.tile.grid.AENetworkedPoweredTile;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import org.jetbrains.annotations.Nullable;
import java.util.EnumSet;
import java.util.Set;

public class TileGrowthAccelerator extends AENetworkedPoweredTile implements IPowerChannelState {
    private static final int POWER_PER_TICK = 8;

    private final ICrankable crankable = new Crankable();

    public TileGrowthAccelerator() {
        setInternalMaxPower(10 * POWER_PER_CRANK_TURN);
        setPowerSides(getGridConnectableSides(getOrientation()));
        getMainNode().setFlags();
        getMainNode().setIdlePowerUsage(POWER_PER_TICK);
        getMainNode().addService(IGridTickable.class, new IGridTickable() {
            @Override
            public TickingRequest getTickingRequest(IGridNode node) {
                int speed = AEConfig.instance().getGrowthAcceleratorSpeed();
                return new TickingRequest(speed, speed, false);
            }

            @Override
            public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
                onTick(ticksSinceLastCall);
                return TickRateModulation.SAME;
            }
        });
    }

    @Override
    public ItemStack getItemFromTile() {
        return AEBlocks.GROWTH_ACCELERATOR.stack();
    }

    public Set<EnumFacing> getGridConnectableSides(BlockOrientation orientation) {
        return orientation.getSides(EnumSet.of(RelativeSide.FRONT, RelativeSide.BACK));
    }

    @Override
    protected void onOrientationChanged(BlockOrientation orientation) {
        super.onOrientationChanged(orientation);
        setPowerSides(getGridConnectableSides(getOrientation()));
    }

    private void onTick(int ticksSinceLastCall) {
        boolean powered = isPowered();
        IBlockState state = getBlockState();
        if (state != null && powered != state.getValue(GrowthAcceleratorBlock.POWERED)) {
            markForUpdate();
        }

        if (!powered) {
            return;
        }

        extractAEPower(POWER_PER_TICK * ticksSinceLastCall, Actionable.MODULATE);

        if (!(getWorld() instanceof WorldServer serverLevel)) {
            return;
        }

        for (EnumFacing direction : EnumFacing.VALUES) {
            BlockPos adjPos = getPos().offset(direction);
            IBlockState adjState = serverLevel.getBlockState(adjPos);
            adjState.getBlock().randomTick(serverLevel, adjPos, adjState, serverLevel.rand);
        }
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (reason == IGridNodeListener.State.POWER) {
            markForUpdate();
        }
    }

    @Override
    public AECableType getCableConnectionType(EnumFacing dir) {
        return AECableType.COVERED;
    }

    @Override
    public boolean isPowered() {
        if (isClientSide()) {
            IBlockState state = getBlockState();
            return state != null && state.getValue(GrowthAcceleratorBlock.POWERED);
        }

        if (getMainNode().isPowered()) {
            return true;
        }
        return extractAEPower(POWER_PER_TICK, Actionable.SIMULATE) >= POWER_PER_TICK;
    }

    @Override
    public boolean isActive() {
        return isPowered();
    }

    @Nullable
    @Override
    protected ICrankable getCrankable(@Nullable EnumFacing direction) {
        if (direction != null && getPowerSides().contains(direction)) {
            return this.crankable;
        }
        return null;
    }
}
