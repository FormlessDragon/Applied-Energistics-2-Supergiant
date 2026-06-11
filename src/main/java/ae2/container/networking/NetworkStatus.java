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

package ae2.container.networking;

import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.energy.IEnergyService;
import ae2.api.networking.energy.IPassiveEnergyGenerator;
import ae2.me.service.EnergyService;
import ae2.tile.misc.TileVibrationChamber;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.network.PacketBuffer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class NetworkStatus {
    private static final int MAX_MACHINE_GROUPS = 4096;
    private static final int MIN_MACHINE_GROUP_BYTES = 18;

    private double averagePowerInjection;
    private double averagePowerUsage;
    private double storedPower;
    private double maxStoredPower;
    private boolean infiniteAveragePowerInjection;
    private boolean infiniteAveragePowerUsage;
    private boolean infiniteStoredPower;
    private boolean infiniteMaxStoredPower;
    private double channelPower;
    private int channelsUsed;

    private List<MachineGroup> groupedMachines = ObjectLists.emptyList();

    public static NetworkStatus fromGrid(IGrid grid) {
        IEnergyService energyService = grid.getEnergyService();

        NetworkStatus status = new NetworkStatus();
        status.averagePowerInjection = energyService.getAvgPowerInjection();
        status.averagePowerUsage = energyService.getAvgPowerUsage();
        status.storedPower = energyService.getStoredPower();
        status.maxStoredPower = energyService.getMaxStoredPower();
        if (energyService instanceof EnergyService aeEnergyService && aeEnergyService.isCreativePowerModeActive()) {
            status.infiniteAveragePowerInjection = true;
            status.infiniteAveragePowerUsage = true;
            status.infiniteStoredPower = true;
            status.infiniteMaxStoredPower = true;
        }
        status.channelPower = energyService.getChannelPowerUsage();
        status.channelsUsed = grid.getPathingService().getUsedChannels();

        Map<MachineGroupKey, MachineGroup> groupedMachines = new Object2ObjectOpenHashMap<>();
        for (var machineClass : grid.getMachineClasses()) {
            for (IGridNode machine : grid.getMachineNodes(machineClass)) {
                var key = getKey(machine);
                if (key == null) {
                    continue;
                }

                var group = groupedMachines.computeIfAbsent(key, MachineGroup::new);
                group.setCount(group.getCount() + 1);
                group.setIdlePowerUsage(group.getIdlePowerUsage() + machine.getIdlePowerUsage());

                var passiveEnergyGenerator = machine.getService(IPassiveEnergyGenerator.class);
                if (passiveEnergyGenerator != null && !passiveEnergyGenerator.isSuppressed()) {
                    group.setPowerGenerationCapacity(
                        group.getPowerGenerationCapacity() + passiveEnergyGenerator.getRate());
                }

                if (machine.getOwner() instanceof TileVibrationChamber vibrationChamber) {
                    group.setPowerGenerationCapacity(
                        group.getPowerGenerationCapacity() + vibrationChamber.getMaxEnergyRate());
                }
            }
        }
        status.groupedMachines = List.copyOf(groupedMachines.values());

        return status;
    }

    @Nullable
    private static MachineGroupKey getKey(IGridNode machine) {
        var visualRepresentation = machine.getVisualRepresentation();
        if (visualRepresentation == null) {
            return null;
        }

        return new MachineGroupKey(visualRepresentation, !machine.meetsChannelRequirements());
    }

    public static NetworkStatus read(PacketBuffer data) {
        NetworkStatus status = new NetworkStatus();
        status.averagePowerInjection = data.readDouble();
        status.averagePowerUsage = data.readDouble();
        status.storedPower = data.readDouble();
        status.maxStoredPower = data.readDouble();
        status.infiniteAveragePowerInjection = data.readBoolean();
        status.infiniteAveragePowerUsage = data.readBoolean();
        status.infiniteStoredPower = data.readBoolean();
        status.infiniteMaxStoredPower = data.readBoolean();
        status.channelPower = data.readDouble();
        status.channelsUsed = data.readVarInt();

        int count = data.readVarInt();
        if (count < 0 || count > MAX_MACHINE_GROUPS || count > data.readableBytes() / MIN_MACHINE_GROUP_BYTES) {
            throw new IllegalArgumentException("Invalid network status machine group count: " + count);
        }

        ObjectList<MachineGroup> machines = new ObjectArrayList<>(count);
        for (int i = 0; i < count; i++) {
            machines.add(MachineGroup.read(data));
        }
        status.groupedMachines = List.copyOf(machines);

        return status;
    }

    public void write(PacketBuffer data) {
        data.writeDouble(averagePowerInjection);
        data.writeDouble(averagePowerUsage);
        data.writeDouble(storedPower);
        data.writeDouble(maxStoredPower);
        data.writeBoolean(infiniteAveragePowerInjection);
        data.writeBoolean(infiniteAveragePowerUsage);
        data.writeBoolean(infiniteStoredPower);
        data.writeBoolean(infiniteMaxStoredPower);
        data.writeDouble(channelPower);
        data.writeVarInt(channelsUsed);
        data.writeVarInt(groupedMachines.size());
        for (MachineGroup machine : groupedMachines) {
            machine.write(data);
        }
    }

    public double getAveragePowerInjection() {
        return averagePowerInjection;
    }

    public boolean isInfiniteAveragePowerInjection() {
        return infiniteAveragePowerInjection;
    }

    public double getAveragePowerUsage() {
        return averagePowerUsage;
    }

    public boolean isInfiniteAveragePowerUsage() {
        return infiniteAveragePowerUsage;
    }

    public double getStoredPower() {
        return storedPower;
    }

    public boolean isInfiniteStoredPower() {
        return infiniteStoredPower;
    }

    public double getMaxStoredPower() {
        return maxStoredPower;
    }

    public boolean isInfiniteMaxStoredPower() {
        return infiniteMaxStoredPower;
    }

    public double getChannelPower() {
        return channelPower;
    }

    public int getChannelsUsed() {
        return channelsUsed;
    }

    public List<MachineGroup> getGroupedMachines() {
        return groupedMachines;
    }
}

