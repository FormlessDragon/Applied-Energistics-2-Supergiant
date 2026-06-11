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

package ae2.container.me.crafting;

import ae2.api.stacks.AEKey;
import net.minecraft.network.PacketBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Objects;

public record CraftingStatusEntry(long serial, @Nullable AEKey what, long storedAmount, long activeAmount,
                                  long pendingAmount) implements Comparable<CraftingStatusEntry> {
    private static final Comparator<CraftingStatusEntry> COMPARATOR = Comparator
        .comparingLong((CraftingStatusEntry e) -> e.activeAmount() + e.pendingAmount())
        .thenComparingLong(CraftingStatusEntry::storedAmount)
        .reversed();

    public static CraftingStatusEntry read(PacketBuffer buffer) {
        long serial = buffer.readVarLong();
        long activeAmount = buffer.readVarLong();
        long storedAmount = buffer.readVarLong();
        long pendingAmount = buffer.readVarLong();
        if (activeAmount < 0 || storedAmount < 0 || pendingAmount < 0) {
            throw new IllegalArgumentException("Crafting status entry contains negative amounts");
        }

        var what = AEKey.readOptionalKey(buffer);
        return new CraftingStatusEntry(serial, what, storedAmount, activeAmount, pendingAmount);
    }

    public boolean isDeleted() {
        return this.storedAmount == 0 && this.activeAmount == 0 && this.pendingAmount == 0;
    }

    public void write(PacketBuffer buffer) {
        buffer.writeVarLong(this.serial);
        buffer.writeVarLong(this.activeAmount);
        buffer.writeVarLong(this.storedAmount);
        buffer.writeVarLong(this.pendingAmount);
        AEKey.writeOptionalKey(buffer, this.what);
    }

    @Override
    public int compareTo(@NotNull CraftingStatusEntry other) {
        return COMPARATOR.compare(this, other);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CraftingStatusEntry(
            long serial1, AEKey what1, long amount, long activeAmount1, long pendingAmount1
        ))) {
            return false;
        }
        return serial == serial1
            && storedAmount == amount
            && activeAmount == activeAmount1
            && pendingAmount == pendingAmount1
            && Objects.equals(what, what1);
    }

}

