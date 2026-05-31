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

import ae2.api.config.Actionable;
import ae2.api.config.FuzzyMode;
import ae2.api.crafting.IPatternDetails;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.CraftingCalculation;
import ae2.crafting.CraftingPlan;
import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

public abstract class CraftingSimulationState implements ICraftingSimulationState {
    /**
     * Partial cache of the parent's items, never modified.
     */
    private final KeyCounter unmodifiedCache;
    /**
     * Partial cache of the parent's items, but modifiable. The different between this cache and the unmodified cache is
     * the items that were injected/extracted.
     */
    private final KeyCounter modifiableCache;
    /**
     * List of items to emit.
     */
    private final KeyCounter emittedItems;
    /**
     * Virtual outputs produced by pseudo patterns during planning. They may satisfy later pseudo-pattern inputs, but do
     * not count as real simulated inventory or as network extraction requirements.
     */
    private final KeyCounter pseudoItems;
    private final Object2LongMap<IPatternDetails> crafts = new Object2LongOpenHashMap<>();
    /**
     * Minimum amount of each item that needs to be extracted from the network. This is the maximum of (unmodified -
     * modifiable).
     */
    private final KeyCounter requiredExtract;
    /**
     * Byte count.
     */
    private double bytes = 0;

    protected CraftingSimulationState() {
        this.unmodifiedCache = new KeyCounter();
        this.modifiableCache = new KeyCounter();
        this.emittedItems = new KeyCounter();
        this.pseudoItems = new KeyCounter();
        this.requiredExtract = new KeyCounter();
        this.crafts.defaultReturnValue(0);
    }

    public static CraftingPlan buildCraftingPlan(CraftingSimulationState state,
                                                 CraftingCalculation calculation, long calculatedAmount) {
        return new CraftingPlan(
            new GenericStack(calculation.getOutput(), calculatedAmount),
            (long) Math.ceil(state.bytes),
            calculation.isSimulation(),
            calculation.hasMultiplePaths(),
            state.requiredExtract,
            state.emittedItems,
            calculation.getMissingItems(),
            calculation.getIntermediateFinalOutputAmount(),
            state.crafts,
            calculation.getTree(),
            calculation.getTemporaryProviders());
    }

    protected abstract long simulateExtractParent(AEKey what);

    protected abstract Iterable<AEKey> findFuzzyParent(AEKey input);

    private void cacheFuzzy(AEKey what) {
        if (unmodifiedCache.findFuzzy(what, FuzzyMode.IGNORE_ALL).isEmpty()) {
            boolean insertedAny = false;

            for (var keyToCache : findFuzzyParent(what)) {
                // not cached yet.
                var extracted = simulateExtractParent(keyToCache);
                if (extracted != 0) {
                    insertedAny = true;
                }
                modifiableCache.add(keyToCache, extracted);
                unmodifiedCache.add(keyToCache, extracted);
            }

            if (!insertedAny) {
                unmodifiedCache.add(what, 0);
            }
        }
    }

    @Override
    public void insert(AEKey what, long amount, Actionable mode) {
        cacheFuzzy(what);

        if (mode == Actionable.MODULATE) {
            modifiableCache.add(what, amount);
        }
    }

    private void updateRequiredExtract(AEKey key, long delta) {
        if (delta > 0) {
            long max = Math.max(delta, this.requiredExtract.get(key));
            this.requiredExtract.set(key, max);
        }
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode) {
        cacheFuzzy(what);

        var cachedAmount = modifiableCache.get(what);
        if (cachedAmount == 0)
            return 0;

        long extracted = Math.min(cachedAmount, amount);
        if (mode == Actionable.MODULATE) {
            modifiableCache.remove(what, extracted);
        }

        updateRequiredExtract(what, unmodifiedCache.get(what) - modifiableCache.get(what));

        return extracted;
    }

    @Nullable
    @Override
    public Iterable<AEKey> findFuzzyTemplates(AEKey input) {
        if (input == null)
            return Collections.emptyList();
        cacheFuzzy(input);

        return Iterables.transform(
            Iterables.filter(modifiableCache.findFuzzy(input, FuzzyMode.IGNORE_ALL), entry -> entry.getLongValue() > 0),
            it.unimi.dsi.fastutil.objects.Object2LongMap.Entry::getKey);
    }

    @Override
    public void emitItems(AEKey what, long amount) {
        this.emittedItems.add(what, amount);
    }

    public void insertPseudo(AEKey what, long amount, Actionable mode) {
        if (mode == Actionable.MODULATE) {
            this.pseudoItems.add(what, amount);
        }
    }

    /**
     * Extracts virtual pseudo outputs. This intentionally does not update requiredExtract because pseudo outputs are
     * never pulled from network storage.
     */
    public long extractPseudo(AEKey what, long amount, Actionable mode) {
        var available = this.pseudoItems.get(what);
        if (available == 0) {
            return 0;
        }

        long extracted = Math.min(available, amount);
        if (mode == Actionable.MODULATE) {
            this.pseudoItems.remove(what, extracted);
        }
        return extracted;
    }

    @Override
    public void addBytes(double bytes) {
        this.bytes += bytes;
    }

    @Override
    public void addCrafting(IPatternDetails details, long crafts) {
        this.crafts.merge(details, crafts, Long::sum);
    }

    public long getAvailablePseudoAmount(AEKey what) {
        return this.pseudoItems.get(what);
    }

    public long getOriginalAmount(AEKey what) {
        cacheFuzzy(what);
        return this.unmodifiedCache.get(what);
    }

    public long getAvailableAmount(AEKey what) {
        cacheFuzzy(what);
        return this.modifiableCache.get(what);
    }

    public void ignore(AEKey stack) {
        cacheFuzzy(stack);
        unmodifiedCache.set(stack, 0);
        modifiableCache.set(stack, 0);
    }

    public void applyDiff(CraftingSimulationState parent) {
        // It's important to apply this here to ensure that the extract below doesn't make us count some stacks twice.
        for (var entry : requiredExtract) {
            var key = entry.getKey();
            // To compute the new parent max difference during the processing of the child's queries:
            // Take current parent difference, and add required extract (= max difference observed in the child).
            long delta = parent.unmodifiedCache.get(key) - parent.modifiableCache.get(key) + entry.getLongValue();
            parent.updateRequiredExtract(key, delta);
        }

        for (var entry : modifiableCache) {
            var unmodified = unmodifiedCache.get(entry.getKey());
            long sizeDelta = entry.getLongValue() - unmodified;

            if (sizeDelta > 0) {
                parent.insert(entry.getKey(), sizeDelta, Actionable.MODULATE);
            } else if (sizeDelta < 0) {
                long newStackSize = -sizeDelta;
                var reallyExtracted = parent.extract(entry.getKey(), newStackSize, Actionable.MODULATE);

                if (reallyExtracted != -sizeDelta) {
                    throw new IllegalStateException("Failed to extract from parent. This is a bug!");
                }
            }
        }

        for (var toEmit : emittedItems) {
            parent.emitItems(toEmit.getKey(), toEmit.getLongValue());
        }

        for (var pseudoItem : pseudoItems) {
            parent.insertPseudo(pseudoItem.getKey(), pseudoItem.getLongValue(), Actionable.MODULATE);
        }

        parent.addBytes(bytes);

        for (Object2LongMap.Entry<IPatternDetails> entry : crafts.object2LongEntrySet()) {
            parent.addCrafting(entry.getKey(), entry.getLongValue());
        }
    }
}
