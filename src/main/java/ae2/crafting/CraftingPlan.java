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

package ae2.crafting;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.graph.CraftingGraphDisplaySnapshot;
import ae2.integration.data.LiteCraftTreeNode;
import ae2.api.stacks.AEKey;
import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CraftingPlan implements ICraftingPlan, TemporaryProviderCarrier, CraftingPlanDisplaySource {
    private final GenericStack finalOutput;
    private final long bytes;
    private final boolean simulation;
    private final boolean multiplePaths;
    private final KeyCounter usedItems;
    private final KeyCounter emittedItems;
    private final KeyCounter missingItems;
    private final long intermediateFinalOutputAmount;
    private final Object2LongMap<IPatternDetails> patternTimes;
    private final @Nullable CraftingTreeNode tree;
    private final List<ICraftingProvider> temporaryProviders;
    private final @Nullable CraftingGraphDisplaySnapshot.Builder graphDisplayBuilder;
    private final @Nullable CraftingCalculation calculation;
    private volatile @Nullable CraftingPlanDisplay display;
    private final CraftingAttemptMetrics attemptMetrics;

    public CraftingPlan(GenericStack finalOutput, long bytes, boolean simulation, boolean multiplePaths,
                        KeyCounter usedItems, KeyCounter emittedItems, KeyCounter missingItems,
                        long intermediateFinalOutputAmount, Object2LongMap<IPatternDetails> patternTimes,
                        @Nullable CraftingTreeNode tree, List<ICraftingProvider> temporaryProviders,
                        @Nullable CraftingGraphDisplaySnapshot.Builder graphDisplayBuilder,
                        CraftingAttemptMetrics attemptMetrics,
                        @Nullable CraftingCalculation calculation) {
        this.finalOutput = finalOutput;
        this.bytes = bytes;
        this.simulation = simulation;
        this.multiplePaths = multiplePaths;
        this.usedItems = usedItems;
        this.emittedItems = emittedItems;
        this.missingItems = missingItems;
        this.intermediateFinalOutputAmount = intermediateFinalOutputAmount;
        this.patternTimes = patternTimes;
        this.tree = tree;
        this.temporaryProviders = temporaryProviders;
        this.graphDisplayBuilder = graphDisplayBuilder;
        this.attemptMetrics = attemptMetrics;
        this.calculation = calculation;
    }

    /**
     * The same plan with other contents, for a plan repaired after the calculation. Its crafting tree is still the one
     * calculated: the tree screen shows the crafts the way they were found, its amounts held to the repaired ones
     * where the tree is allocated by them.
     */
    CraftingPlan withContents(KeyCounter usedItems, KeyCounter emittedItems, KeyCounter missingItems,
                              Object2LongMap<IPatternDetails> patternTimes, CraftingCalculation calculation) {
        // Additional crafts also need storage; retain the original bound when a repair only reduces crafts.
        double additionalBytes = 0;
        for (var entry : patternTimes.object2LongEntrySet()) {
            long added = entry.getLongValue() - this.patternTimes.getLong(entry.getKey());
            if (added <= 0) continue;
            additionalBytes += added;
            for (var input : entry.getKey().getInputs()) {
                if (input.possibleInputs().length == 0) continue;
                var first = input.possibleInputs()[0];
                additionalBytes += (double) added * first.amount() * input.getMultiplier()
                    / first.what().getType().getAmountPerByte() * 8;
            }
        }
        long repairedBytes = LongMath.saturatedAdd(bytes, (long) Math.ceil(additionalBytes));
        long intermediate = patternTimes.equals(this.patternTimes) ? this.intermediateFinalOutputAmount
            : Math.max(0, getFinalOutputCrafted(finalOutput.what(), patternTimes) - finalOutput.amount());
        return new CraftingPlan(finalOutput, repairedBytes, simulation, multiplePaths, usedItems, emittedItems, missingItems,
            intermediate, patternTimes, tree, temporaryProviders, graphDisplayBuilder, attemptMetrics,
            calculation);
    }

    /**
     * How much of the final output the crafts make. What they make beyond the request first returns to the CPU, for
     * the crafts that take it.
     */
    private static long getFinalOutputCrafted(AEKey finalOutput, Object2LongMap<IPatternDetails> patternTimes) {
        long crafted = 0;
        for (var entry : patternTimes.object2LongEntrySet()) {
            for (var output : entry.getKey().getOutputs()) {
                if (finalOutput.matches(output)) {
                    crafted = LongMath.saturatedAdd(crafted,
                        LongMath.saturatedMultiply(entry.getLongValue(), output.amount()));
                }
            }
        }
        return crafted;
    }

    public GenericStack finalOutput() {
        return finalOutput;
    }

    public long bytes() {
        return bytes;
    }

    public boolean simulation() {
        return simulation;
    }

    public boolean multiplePaths() {
        return multiplePaths;
    }

    public KeyCounter usedItems() {
        return usedItems;
    }

    public KeyCounter emittedItems() {
        return emittedItems;
    }

    public KeyCounter missingItems() {
        return missingItems;
    }

    public long intermediateFinalOutputAmount() {
        return intermediateFinalOutputAmount;
    }

    public Object2LongMap<IPatternDetails> patternTimes() {
        return patternTimes;
    }

    public @Nullable CraftingTreeNode tree() {
        return tree;
    }

    @Override
    public List<ICraftingProvider> temporaryProviders() {
        return temporaryProviders;
    }

    public @Nullable CraftingGraphDisplaySnapshot.Builder graphDisplayBuilder() {
        return graphDisplayBuilder;
    }

    public CraftingAttemptMetrics.Snapshot attemptMetrics() {
        return attemptMetrics.snapshot();
    }

    @Override
    public CompletableFuture<LiteCraftTreeNode> requestDisplayTree() {
        var display = this.display;
        if (display == null) {
            synchronized (this) {
                display = this.display;
                if (display == null) {
                    this.display = display = new CraftingPlanDisplay(finalOutput, patternTimes, usedItems, missingItems,
                        tree, graphDisplayBuilder, attemptMetrics, calculation);
                }
            }
        }
        return display.requestTree();
    }
}
