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

package ae2.crafting.inv;

import ae2.api.config.FuzzyMode;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import com.google.common.collect.Iterables;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Currently, extracts the whole network contents when the job starts. Lazily extracting is unfortunately not possible
 * as long as the crafting simulation operates from a separate thread: any world access from this thread will deadlock
 * the server.
 */
public class NetworkCraftingSimulationState extends CraftingSimulationState {
    /**
     * Networks with at most this many distinct available keys are copied in full: the copy is cheap and avoids the
     * graph-peek/subset machinery entirely.
     */
    public static final int SNAPSHOT_SUBSET_THRESHOLD = 512;

    private final KeyCounter list;

    /**
     * @param cachedInventory the cached network inventory (server thread only). The caller fetches it exactly once,
     *                        because fetching may trigger an expensive full rebuild when the cache is dirty.
     */
    public NetworkCraftingSimulationState(KeyCounter cachedInventory) {
        this.list = fullSnapshot(cachedInventory);
    }

    /**
     * Snapshots only the given keys. The copied entries are always entries of the full inventory, so trimming can
     * never cost more than copying everything, while the size stays proportional to the crafting structure. Must be
     * called on the server thread before the job is submitted.
     *
     * @param fuzzyKeys the subset of {@code wantedKeys} whose whole fuzzy group the crafting tree can consume, i.e.
     *                  inputs of pattern slots with ingredient substitution. All other keys, and every key read by
     *                  the graph executor, are extracted exactly and only need their own entry.
     */
    public NetworkCraftingSimulationState(KeyCounter cachedInventory, Collection<AEKey> wantedKeys,
                                          Set<AEKey> fuzzyKeys) {
        this.list = subsetSnapshot(cachedInventory, wantedKeys, fuzzyKeys);
    }

    private static KeyCounter fullSnapshot(KeyCounter cachedInventory) {
        var result = KeyCounter.saturating(cachedInventory.size());
        for (var entry : cachedInventory) {
            if (entry.getLongValue() > 0) {
                result.add(entry.getKey(), entry.getLongValue());
            }
        }
        return result;
    }

    private static KeyCounter subsetSnapshot(KeyCounter cachedInventory, Collection<AEKey> wantedKeys,
                                             Set<AEKey> fuzzyKeys) {
        var result = KeyCounter.saturating(wantedKeys.size() + fuzzyKeys.size());
        for (var key : wantedKeys) {
            if (fuzzyKeys.contains(key)) {
                for (var variant : cachedInventory.findFuzzy(key, FuzzyMode.IGNORE_ALL)) {
                    // set(), not add(): several wanted keys can share one fuzzy group, and a repeated visit must
                    // never accumulate the network's amount past what it really holds.
                    if (variant.getLongValue() > 0) {
                        result.set(variant.getKey(), variant.getLongValue());
                    }
                }
            } else {
                long amount = cachedInventory.get(key);
                if (amount > 0) {
                    result.set(key, amount);
                }
            }
        }
        return result;
    }

    /**
     * Number of entries in the snapshot, for performance logging.
     */
    public int getSnapshotEntryCount() {
        return this.list.size();
    }

    @Override
    protected long simulateExtractParent(AEKey what) {
        return Math.min(list.get(what), Long.MAX_VALUE);
    }

    @Override
    protected Iterable<AEKey> findFuzzyParent(AEKey input) {
        return Iterables.transform(list.findFuzzy(input, FuzzyMode.IGNORE_ALL), Map.Entry::getKey);
    }
}
