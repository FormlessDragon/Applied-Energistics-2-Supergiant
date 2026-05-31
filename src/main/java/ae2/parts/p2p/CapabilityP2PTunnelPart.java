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

package ae2.parts.p2p;

import ae2.api.parts.IPartItem;
import ae2.parts.PartAdjacentApi;
import ae2.util.Platform;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.capabilities.Capability;

/**
 * Base class for simple capability-based p2p tunnels.
 */
public abstract class CapabilityP2PTunnelPart<P extends CapabilityP2PTunnelPart<P, T>, T> extends P2PTunnelPart<P> {
    private final PartAdjacentApi<T> adjacentCapability;
    private final CapabilityGuard capabilityGuard = new CapabilityGuard();
    private final EmptyCapabilityGuard emptyCapabilityGuard = new EmptyCapabilityGuard();
    protected T inputHandler;
    protected T outputHandler;
    protected T emptyHandler;
    private int accessDepth = 0;

    public CapabilityP2PTunnelPart(IPartItem<?> partItem, Capability<T> capability) {
        super(partItem);
        this.adjacentCapability = new PartAdjacentApi<>(this, capability, this::forwardCapabilityInvalidation);
    }

    @Override
    protected float getPowerDrainPerTick() {
        return 2.0f;
    }

    public T getExposedApi() {
        return isOutput() ? outputHandler : inputHandler;
    }

    protected final CapabilityGuard getAdjacentCapability() {
        accessDepth++;
        return capabilityGuard;
    }

    protected final CapabilityGuard getInputCapability() {
        P input = getInput();
        return input == null ? emptyCapabilityGuard : input.getAdjacentCapability();
    }

    protected void forwardCapabilityInvalidation() {
        if (getTileEntity() == null || getLevel() == null) {
            return;
        }

        if (isOutput()) {
            P input = getInput();
            if (input != null && input.getTileEntity() != null) {
                Platform.notifyBlocksOfNeighbors(input.getLevel(), input.getTileEntity().getPos());
                input.getHost().markForUpdate();
            }
        } else {
            for (P output : getOutputs()) {
                if (output.getTileEntity() != null) {
                    Platform.notifyBlocksOfNeighbors(output.getLevel(), output.getTileEntity().getPos());
                    output.getHost().markForUpdate();
                }
            }
        }
    }

    @Override
    public void onNeighborChanged(IBlockAccess level, BlockPos pos, BlockPos neighbor) {
        this.adjacentCapability.onNeighborChanged(neighbor);
    }

    @Override
    public void onTunnelNetworkChange() {
        if (getTileEntity() != null) {
            Platform.notifyBlocksOfNeighbors(getLevel(), getTileEntity().getPos());
            getHost().markForUpdate();
        }
    }

    protected class CapabilityGuard implements AutoCloseable {
        public T get() {
            if (accessDepth == 0) {
                throw new IllegalStateException("Capability guard was used after closing");
            } else if (accessDepth == 1) {
                if (isActive()) {
                    T adjacent = adjacentCapability.find();
                    if (adjacent != null) {
                        return adjacent;
                    }
                    return emptyHandler;
                }
                return emptyHandler;
            } else {
                return emptyHandler;
            }
        }

        @Override
        public void close() {
            if (--accessDepth < 0) {
                throw new IllegalStateException("Capability guard closed multiple times");
            }
        }
    }

    protected class EmptyCapabilityGuard extends CapabilityGuard {
        @Override
        public void close() {
        }

        @Override
        public T get() {
            return emptyHandler;
        }
    }
}


