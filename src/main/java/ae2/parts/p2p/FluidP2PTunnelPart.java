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

package ae2.parts.p2p;

import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.stacks.AEKeyType;
import ae2.core.AppEng;
import ae2.items.parts.PartModels;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class FluidP2PTunnelPart extends CapabilityP2PTunnelPart<FluidP2PTunnelPart, IFluidHandler> {

    private static final P2PModels MODELS = new P2PModels(AppEng.makeId("part/p2p/p2p_tunnel_fluids"),
        AppEng.makeId("part/p2p_tunnel_fluid"));
    private static final IFluidHandler NULL_FLUID_HANDLER = new NullFluidHandler();
    private static final IFluidTankProperties[] INPUT_TANKS = {
        new FluidTankProperties(null, Integer.MAX_VALUE, true, false)
    };
    private static final IFluidTankProperties[] EMPTY_TANKS = new IFluidTankProperties[0];

    public FluidP2PTunnelPart(IPartItem<?> partItem) {
        super(partItem, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY);
        inputHandler = new InputFluidHandler();
        outputHandler = new OutputFluidHandler();
        emptyHandler = NULL_FLUID_HANDLER;
    }

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(this.isPowered(), this.isActive());
    }

    private static class NullFluidHandler implements IFluidHandler {
        @Override
        public IFluidTankProperties[] getTankProperties() {
            return EMPTY_TANKS;
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            return 0;
        }

        @Override
        public @Nullable FluidStack drain(FluidStack resource, boolean doDrain) {
            return null;
        }

        @Override
        public @Nullable FluidStack drain(int maxDrain, boolean doDrain) {
            return null;
        }
    }

    private class InputFluidHandler implements IFluidHandler {
        @Override
        public IFluidTankProperties[] getTankProperties() {
            return INPUT_TANKS;
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            if (resource == null) {
                return 0;
            }

            final int outputTunnels = FluidP2PTunnelPart.this.getOutputs().size();
            final int amount = resource.amount;
            if (outputTunnels == 0 || amount == 0) {
                return 0;
            }

            final int amountPerOutput = amount / outputTunnels;
            int overflow = amount % outputTunnels;
            int total = 0;

            for (FluidP2PTunnelPart target : FluidP2PTunnelPart.this.getOutputs()) {
                try (CapabilityGuard capabilityGuard = target.getAdjacentCapability()) {
                    final IFluidHandler output = capabilityGuard.get();
                    final int toSend = amountPerOutput + overflow;
                    if (toSend <= 0) {
                        break;
                    }

                    final FluidStack stack = resource.copy();
                    stack.amount = toSend;
                    final int received = output.fill(stack, doFill);
                    overflow = toSend - received;
                    total += received;
                }
            }

            if (doFill && total > 0) {
                deductTransportCost(total, AEKeyType.fluids());
            }

            return total;
        }

        @Override
        public @Nullable FluidStack drain(FluidStack resource, boolean doDrain) {
            return null;
        }

        @Override
        public @Nullable FluidStack drain(int maxDrain, boolean doDrain) {
            return null;
        }
    }

    private class OutputFluidHandler implements IFluidHandler {
        @Override
        public IFluidTankProperties[] getTankProperties() {
            List<IFluidTankProperties> properties = new java.util.ArrayList<>();
            for (FluidP2PTunnelPart input : getInputs()) {
                try (CapabilityGuard capabilityGuard = input.getAdjacentCapability()) {
                    Collections.addAll(properties, capabilityGuard.get().getTankProperties());
                }
            }
            return properties.toArray(new IFluidTankProperties[0]);
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (resource == null) {
                return null;
            }
            for (FluidP2PTunnelPart input : getInputs()) {
                try (CapabilityGuard capabilityGuard = input.getAdjacentCapability()) {
                    FluidStack result = capabilityGuard.get().drain(resource, doDrain);
                    if (result == null) {
                        continue;
                    }
                    if (doDrain) {
                        deductTransportCost(result.amount, AEKeyType.fluids());
                    }
                    return result;
                }
            }
            return null;
        }

        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            for (FluidP2PTunnelPart input : getInputs()) {
                try (CapabilityGuard capabilityGuard = input.getAdjacentCapability()) {
                    FluidStack result = capabilityGuard.get().drain(maxDrain, doDrain);
                    if (result == null) {
                        continue;
                    }
                    if (doDrain) {
                        deductTransportCost(result.amount, AEKeyType.fluids());
                    }
                    return result;
                }
            }
            return null;
        }
    }
}
