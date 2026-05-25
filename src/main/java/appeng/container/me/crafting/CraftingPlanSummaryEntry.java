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

package appeng.container.me.crafting;

import appeng.api.stacks.AEKey;
import net.minecraft.network.PacketBuffer;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public record CraftingPlanSummaryEntry(AEKey what, long missingAmount, long storedAmount,
                                       long craftAmount, long inventoryAmount,
                                       boolean finalOutput) implements Comparable<CraftingPlanSummaryEntry> {
    private static final Comparator<CraftingPlanSummaryEntry> COMPARATOR = Comparator
        .comparing(CraftingPlanSummaryEntry::missingAmount)
        .thenComparing(CraftingPlanSummaryEntry::craftAmount)
        .thenComparing(CraftingPlanSummaryEntry::storedAmount)
        .reversed();

    public static CraftingPlanSummaryEntry read(PacketBuffer buffer) {
        var what = AEKey.readKey(buffer);
        long missingAmount = buffer.readVarLong();
        long storedAmount = buffer.readVarLong();
        long craftAmount = buffer.readVarLong();
        long inventoryAmount = buffer.readVarLong();
        boolean finalOutput = buffer.readBoolean();
        return new CraftingPlanSummaryEntry(what, missingAmount, storedAmount, craftAmount, inventoryAmount,
            finalOutput);
    }

    @Override
    public int compareTo(@NotNull CraftingPlanSummaryEntry other) {
        return COMPARATOR.compare(this, other);
    }

    public void write(PacketBuffer buffer) {
        AEKey.writeKey(buffer, this.what);
        buffer.writeVarLong(this.missingAmount);
        buffer.writeVarLong(this.storedAmount);
        buffer.writeVarLong(this.craftAmount);
        buffer.writeVarLong(this.inventoryAmount);
        buffer.writeBoolean(this.finalOutput);
    }

    public long inventoryUsageAmount() {
        long amount = this.storedAmount + this.missingAmount;
        if (amount < 0) {
            return Long.MAX_VALUE;
        }
        return amount;
    }
}
