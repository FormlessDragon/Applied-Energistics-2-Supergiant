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

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingPlan;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.network.PacketBuffer;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record CraftingPlanSummary(long usedBytes, boolean simulation, List<CraftingPlanSummaryEntry> entries) {

    public static CraftingPlanSummary read(PacketBuffer buffer) {
        long bytesUsed = buffer.readVarLong();
        boolean simulation = buffer.readBoolean();
        int entryCount = buffer.readVarInt();
        ImmutableList.Builder<CraftingPlanSummaryEntry> entries = ImmutableList.builder();
        for (int i = 0; i < entryCount; i++) {
            entries.add(CraftingPlanSummaryEntry.read(buffer));
        }
        return new CraftingPlanSummary(bytesUsed, simulation, entries.build());
    }

    public static CraftingPlanSummary fromJob(IGrid grid, IActionSource actionSource, ICraftingPlan job) {
        Object2ObjectMap<AEKey, KeyStats> plan = new Object2ObjectOpenHashMap<>();
        var hiddenOutputs = getHiddenTemporaryOutputs(job);

        for (var used : job.usedItems()) {
            mapping(plan, used.getKey()).stored += used.getLongValue();
        }
        for (var missing : job.missingItems()) {
            if (hiddenOutputs.contains(missing.getKey())) {
                continue;
            }
            mapping(plan, missing.getKey()).missing += missing.getLongValue();
        }
        for (var emitted : job.emittedItems()) {
            if (hiddenOutputs.contains(emitted.getKey())) {
                continue;
            }
            var entry = mapping(plan, emitted.getKey());
            entry.stored += emitted.getLongValue();
            entry.crafting += emitted.getLongValue();
        }
        for (Object2LongMap.Entry<appeng.api.crafting.IPatternDetails> entry : job.patternTimes().object2LongEntrySet()) {
            for (var out : entry.getKey().getOutputs()) {
                if (hiddenOutputs.contains(out.what())) {
                    continue;
                }
                mapping(plan, out.what()).crafting += out.amount() * entry.getLongValue();
            }
        }

        List<CraftingPlanSummaryEntry> entries = new ObjectArrayList<>();
        var cachedInventory = grid.getStorageService().getCachedInventory();
        var finalOutput = job.finalOutput().what();
        for (var out : plan.entrySet()) {
            long missingAmount = out.getValue().missing;
            long storedAmount = out.getValue().stored;
            long craftAmount = out.getValue().crafting;
            long intermediateCraftAmount = 0;
            long inventoryAmount = cachedInventory.get(out.getKey());
            boolean finalOutputEntry = out.getKey().equals(finalOutput);
            if (finalOutputEntry) {
                craftAmount = job.finalOutput().amount();
                intermediateCraftAmount = job.intermediateFinalOutputAmount();
            }
            entries.add(new CraftingPlanSummaryEntry(out.getKey(), missingAmount, storedAmount, craftAmount,
                intermediateCraftAmount, inventoryAmount, finalOutputEntry));
        }

        Collections.sort(entries);
        return new CraftingPlanSummary(job.bytes(), job.simulation(), List.copyOf(entries));
    }

    private static ObjectOpenHashSet<AEKey> getHiddenTemporaryOutputs(ICraftingPlan job) {
        var hiddenOutputs = new ObjectOpenHashSet<AEKey>();
        if (job instanceof CraftingPlan craftingPlan) {
            for (var provider : craftingPlan.temporaryProviders()) {
                for (var pattern : provider.getAvailablePatterns()) {
                    for (var output : pattern.getOutputs()) {
                        hiddenOutputs.add(output.what());
                    }
                }
            }
        }
        return hiddenOutputs;
    }

    private static KeyStats mapping(Object2ObjectMap<AEKey, KeyStats> plan, AEKey key) {
        Objects.requireNonNull(key, "Key may not be null");
        return plan.computeIfAbsent(key, ignored -> new KeyStats());
    }

    public boolean hasMissingEntries() {
        for (var entry : this.entries) {
            if (entry.missingAmount() > 0) {
                return true;
            }
        }
        return false;
    }

    public void write(PacketBuffer buffer) {
        buffer.writeVarLong(this.usedBytes);
        buffer.writeBoolean(this.simulation);
        buffer.writeVarInt(this.entries.size());
        for (CraftingPlanSummaryEntry entry : this.entries) {
            entry.write(buffer);
        }
    }

    private static class KeyStats {
        private long stored;
        private long missing;
        private long crafting;
    }
}
