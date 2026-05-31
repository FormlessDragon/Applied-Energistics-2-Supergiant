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

import ae2.api.stacks.AEItemKey;
import net.minecraft.network.PacketBuffer;

import java.util.Comparator;

public class MachineGroup {
    public static final Comparator<MachineGroup> COMPARATOR = Comparator.comparing(MachineGroup::isMissingChannel)
                                                                        .thenComparingInt(MachineGroup::getCount)
                                                                        .reversed();

    private final MachineGroupKey key;
    private double idlePowerUsage;
    private double powerGenerationCapacity;
    private int count;

    MachineGroup(MachineGroupKey key) {
        this.key = key;
    }

    static MachineGroup read(PacketBuffer data) {
        MachineGroup entry = new MachineGroup(MachineGroupKey.fromPacket(data));
        entry.idlePowerUsage = data.readDouble();
        entry.powerGenerationCapacity = data.readDouble();
        entry.count = data.readVarInt();
        return entry;
    }

    void write(PacketBuffer data) {
        key.write(data);
        data.writeDouble(idlePowerUsage);
        data.writeDouble(powerGenerationCapacity);
        data.writeVarInt(count);
    }

    public AEItemKey getDisplay() {
        return key.display();
    }

    public boolean isMissingChannel() {
        return key.missingChannel();
    }

    public double getIdlePowerUsage() {
        return idlePowerUsage;
    }

    void setIdlePowerUsage(double idlePowerUsage) {
        this.idlePowerUsage = idlePowerUsage;
    }

    public double getPowerGenerationCapacity() {
        return powerGenerationCapacity;
    }

    public void setPowerGenerationCapacity(double powerGenerationCapacity) {
        this.powerGenerationCapacity = powerGenerationCapacity;
    }

    public int getCount() {
        return count;
    }

    void setCount(int count) {
        this.count = count;
    }
}

