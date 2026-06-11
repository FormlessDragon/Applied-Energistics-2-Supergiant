/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2017, AlgorithmX2, All rights reserved.
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
import ae2.core.AppEng;
import ae2.items.parts.PartModels;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.List;

public class FEP2PTunnelPart extends CapabilityP2PTunnelPart<FEP2PTunnelPart, IEnergyStorage> {
    private static final P2PModels MODELS = new P2PModels(AppEng.makeId("part/p2p/p2p_tunnel_fe"),
        AppEng.makeId("part/p2p_tunnel_energy"));
    private static final IEnergyStorage NULL_ENERGY_STORAGE = new NullEnergyStorage();

    public FEP2PTunnelPart(IPartItem<?> partItem) {
        super(partItem, CapabilityEnergy.ENERGY);
        inputHandler = new InputEnergyStorage();
        outputHandler = new OutputEnergyStorage();
        emptyHandler = NULL_ENERGY_STORAGE;
    }

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(this.isPowered(), this.isActive());
    }

    private static class NullEnergyStorage implements IEnergyStorage {

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return 0;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }

    private class InputEnergyStorage implements IEnergyStorage {
        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int total = 0;

            final int outputTunnels = FEP2PTunnelPart.this.getOutputs().size();

            if (outputTunnels == 0 || maxReceive == 0) {
                return 0;
            }

            final int amountPerOutput = maxReceive / outputTunnels;
            int overflow = maxReceive % outputTunnels;

            for (FEP2PTunnelPart target : FEP2PTunnelPart.this.getOutputs()) {
                try (CapabilityGuard capabilityGuard = target.getAdjacentCapability()) {
                    final IEnergyStorage output = capabilityGuard.get();
                    final int toSend = amountPerOutput + overflow;
                    final int received = output.receiveEnergy(toSend, simulate);

                    overflow = toSend - received;
                    total += received;
                }
            }

            if (!simulate) {
                deductEnergyCost(total);
            }

            return total;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }

        @Override
        public int getMaxEnergyStored() {
            int total = 0;

            for (FEP2PTunnelPart t : FEP2PTunnelPart.this.getOutputs()) {
                try (CapabilityGuard capabilityGuard = t.getAdjacentCapability()) {
                    total += capabilityGuard.get().getMaxEnergyStored();
                }
            }

            return total;
        }

        @Override
        public int getEnergyStored() {
            int total = 0;

            for (FEP2PTunnelPart t : FEP2PTunnelPart.this.getOutputs()) {
                try (CapabilityGuard capabilityGuard = t.getAdjacentCapability()) {
                    total += capabilityGuard.get().getEnergyStored();
                }
            }

            return total;
        }
    }

    private class OutputEnergyStorage implements IEnergyStorage {
        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int total = 0;
            int remaining = maxExtract;
            for (FEP2PTunnelPart input : getInputs()) {
                if (remaining <= 0) {
                    break;
                }
                try (CapabilityGuard capabilityGuard = input.getAdjacentCapability()) {
                    int extracted = capabilityGuard.get().extractEnergy(remaining, simulate);
                    total += extracted;
                    remaining -= extracted;
                }
            }

            if (!simulate) {
                deductEnergyCost(total);
            }

            return total;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public boolean canExtract() {
            for (FEP2PTunnelPart input : getInputs()) {
                try (CapabilityGuard capabilityGuard = input.getAdjacentCapability()) {
                    if (capabilityGuard.get().canExtract()) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean canReceive() {
            return false;
        }

        @Override
        public int getMaxEnergyStored() {
            int total = 0;
            for (FEP2PTunnelPart input : getInputs()) {
                try (CapabilityGuard capabilityGuard = input.getAdjacentCapability()) {
                    total += capabilityGuard.get().getMaxEnergyStored();
                }
            }
            return total;
        }

        @Override
        public int getEnergyStored() {
            int total = 0;
            for (FEP2PTunnelPart input : getInputs()) {
                try (CapabilityGuard capabilityGuard = input.getAdjacentCapability()) {
                    total += capabilityGuard.get().getEnergyStored();
                }
            }
            return total;
        }
    }
}
