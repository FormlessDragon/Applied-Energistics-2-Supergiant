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

import ae2.container.me.common.IncrementalUpdateHelper;
import ae2.crafting.execution.CraftingCpuLogic;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.PacketBuffer;

import java.util.Collections;
import java.util.List;

public record CraftingStatus(boolean fullStatus, long elapsedTime, long remainingItemCount, long startItemCount,
                             List<CraftingStatusEntry> entries, boolean suspended) {
    private static final int MAX_ENTRY_COUNT = 4096;
    private static final int MIN_ENTRY_BYTES = 5;

    public static final CraftingStatus EMPTY = new CraftingStatus(true, 0, 0, 0, Collections.emptyList(), false);

    public CraftingStatus(boolean fullStatus, long elapsedTime, long remainingItemCount, long startItemCount,
                          List<CraftingStatusEntry> entries, boolean suspended) {
        this.fullStatus = fullStatus;
        this.elapsedTime = elapsedTime;
        this.remainingItemCount = remainingItemCount;
        this.startItemCount = startItemCount;
        this.entries = ImmutableList.copyOf(entries);
        this.suspended = suspended;
    }

    public static CraftingStatus read(PacketBuffer buffer) {
        boolean fullStatus = buffer.readBoolean();
        long elapsedTime = buffer.readVarLong();
        long remainingItemCount = buffer.readVarLong();
        long startItemCount = buffer.readVarLong();
        if (elapsedTime < 0 || remainingItemCount < 0 || startItemCount < 0) {
            throw new IllegalArgumentException("Crafting status contains negative amounts");
        }

        int size = buffer.readInt();
        if (size < 0 || size > MAX_ENTRY_COUNT || size > buffer.readableBytes() / MIN_ENTRY_BYTES) {
            throw new IllegalArgumentException("Invalid crafting status entry count: " + size);
        }

        var entries = ImmutableList.<CraftingStatusEntry>builderWithExpectedSize(size);
        for (int i = 0; i < size; i++) {
            entries.add(CraftingStatusEntry.read(buffer));
        }
        boolean suspended = buffer.readBoolean();
        return new CraftingStatus(fullStatus, elapsedTime, remainingItemCount, startItemCount, entries.build(),
            suspended);
    }

    public static CraftingStatus create(IncrementalUpdateHelper changes, CraftingCpuLogic logic) {
        boolean full = changes.isFullUpdate();

        var newEntries = ImmutableList.<CraftingStatusEntry>builder();
        for (var what : changes) {
            long storedCount = logic.getStored(what);
            long activeCount = logic.getWaitingFor(what);
            long pendingCount = logic.getPendingOutputs(what);

            var sentStack = what;
            if (!full && changes.getSerial(what) != null) {
                sentStack = null;
            }

            var entry = new CraftingStatusEntry(
                changes.getOrAssignSerial(what),
                sentStack,
                storedCount,
                activeCount,
                pendingCount);
            newEntries.add(entry);

            if (entry.isDeleted()) {
                changes.removeSerial(what);
            }
        }

        var elapsedTimeTracker = logic.getElapsedTimeTracker();
        long startItemCount = elapsedTimeTracker.getStartedWorkUnits();
        long remainingItemCount = elapsedTimeTracker.getRemainingWorkUnits();

        return new CraftingStatus(
            full,
            elapsedTimeTracker.getElapsedTime(),
            remainingItemCount,
            startItemCount,
            newEntries.build(),
            logic.isJobSuspended());
    }

    public void write(PacketBuffer buffer) {
        buffer.writeBoolean(fullStatus);
        buffer.writeVarLong(elapsedTime);
        buffer.writeVarLong(remainingItemCount);
        buffer.writeVarLong(startItemCount);
        buffer.writeInt(entries.size());
        for (var entry : entries) {
            entry.write(buffer);
        }
        buffer.writeBoolean(suspended);
    }
}
