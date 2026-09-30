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
import ae2.integration.data.LiteCraftTreeNode;
import ae2.integration.data.LiteCraftTreeProc;
import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A plan that bundles the independent sub-plans of one batch order into a single crafting job.
 * <p>
 * The crafting terminal does not order one pattern that pretends to be the whole recipe. Every material the transfer
 * could not supply becomes the root of its own real calculation, and this plan merges those sub-plans so that one CPU
 * job crafts all of them at once. The caller has already merged identical materials into a single sub-plan, so no
 * material is demanded twice.
 * <p>
 * The job still reports the output of the transferred recipe as its final output. That output is a placeholder: the
 * player crafts it by hand from the delivered materials, so a {@link TemporaryPseudoCraftingProvider} fakes its
 * production instead of consuming anything.
 */
public final class BatchCraftingPlan implements ICraftingPlan, TemporaryProviderCarrier, CraftingPlanDisplaySource {
    private final GenericStack finalOutput;
    private final long bytes;
    private final boolean multiplePaths;
    private final KeyCounter usedItems;
    private final KeyCounter emittedItems;
    private final KeyCounter missingItems;
    private final long intermediateFinalOutputAmount;
    private final Object2LongMap<IPatternDetails> patternTimes;
    private final List<ICraftingProvider> temporaryProviders;
    private final List<ICraftingPlan> subPlans;

    private BatchCraftingPlan(GenericStack finalOutput, long bytes, boolean multiplePaths, KeyCounter usedItems,
                              KeyCounter emittedItems, KeyCounter missingItems, long intermediateFinalOutputAmount,
                              Object2LongMap<IPatternDetails> patternTimes,
                              List<ICraftingProvider> temporaryProviders, List<ICraftingPlan> subPlans) {
        this.finalOutput = finalOutput;
        this.bytes = bytes;
        this.multiplePaths = multiplePaths;
        this.usedItems = usedItems;
        this.emittedItems = emittedItems;
        this.missingItems = missingItems;
        this.intermediateFinalOutputAmount = intermediateFinalOutputAmount;
        this.patternTimes = patternTimes;
        this.temporaryProviders = temporaryProviders;
        this.subPlans = subPlans;
    }

    /**
     * Merges the sub-plans of one batch into a single submit-ready plan.
     *
     * @param finalOutput        the faked output of the transferred recipe
     * @param subPlans           one submit-capable plan per distinct missing material
     * @param temporaryProviders the providers of the placeholder patterns the CPU has to register
     */
    public static BatchCraftingPlan combine(GenericStack finalOutput, List<ICraftingPlan> subPlans,
                                            List<ICraftingProvider> temporaryProviders) {
        if (subPlans.isEmpty()) {
            throw new IllegalArgumentException("A batch plan needs at least one sub-plan");
        }

        var usedItems = new KeyCounter();
        var emittedItems = new KeyCounter();
        var missingItems = new KeyCounter();
        var patternTimes = new Object2LongLinkedOpenHashMap<IPatternDetails>();
        long bytes = 0;
        long intermediateFinalOutputAmount = 0;
        boolean multiplePaths = false;
        for (var subPlan : subPlans) {
            if (subPlan.simulation()) {
                throw new IllegalArgumentException("A batch plan cannot contain a simulation plan");
            }

            usedItems.addAll(subPlan.usedItems());
            emittedItems.addAll(subPlan.emittedItems());
            missingItems.addAll(subPlan.missingItems());
            for (var entry : subPlan.patternTimes().object2LongEntrySet()) {
                patternTimes.addTo(entry.getKey(), entry.getLongValue());
            }
            bytes = LongMath.saturatedAdd(bytes, subPlan.bytes());
            intermediateFinalOutputAmount = LongMath.saturatedAdd(intermediateFinalOutputAmount,
                subPlan.intermediateFinalOutputAmount());
            multiplePaths |= subPlan.multiplePaths();
        }
        // The placeholder pattern of the faked output is one more task the CPU has to hold in its plan. Its inputs are
        // only needed while it is pushed, they are returned to the CPU afterwards, so the sub-plans stay their owners.
        for (var provider : temporaryProviders) {
            for (var pattern : provider.getAvailablePatterns()) {
                patternTimes.addTo(pattern, 1);
            }
        }
        bytes = LongMath.saturatedAdd(bytes, 8);

        return new BatchCraftingPlan(finalOutput, bytes, multiplePaths, usedItems, emittedItems, missingItems,
            intermediateFinalOutputAmount, patternTimes, List.copyOf(temporaryProviders), List.copyOf(subPlans));
    }

    /**
     * The materials this order crafts, one per distinct material the transfer was missing. These are the items the
     * player wants at the top of the item list, because the reported output only stands in for the recipe.
     */
    public List<GenericStack> batchTargets() {
        var targets = new ObjectArrayList<GenericStack>(this.subPlans.size());
        for (var subPlan : this.subPlans) {
            targets.add(subPlan.finalOutput());
        }
        return List.copyOf(targets);
    }

    @Override
    public GenericStack finalOutput() {
        return this.finalOutput;
    }

    @Override
    public long bytes() {
        return this.bytes;
    }

    @Override
    public boolean simulation() {
        return false;
    }

    @Override
    public boolean multiplePaths() {
        return this.multiplePaths;
    }

    @Override
    public KeyCounter usedItems() {
        return this.usedItems;
    }

    @Override
    public KeyCounter emittedItems() {
        return this.emittedItems;
    }

    @Override
    public KeyCounter missingItems() {
        return this.missingItems;
    }

    @Override
    public long intermediateFinalOutputAmount() {
        return this.intermediateFinalOutputAmount;
    }

    @Override
    public Object2LongMap<IPatternDetails> patternTimes() {
        return this.patternTimes;
    }

    @Override
    public List<ICraftingProvider> temporaryProviders() {
        return this.temporaryProviders;
    }

    /**
     * The tree of a batch order shows the faked output at its root and every material of the order as one of its
     * sub-trees, because that is how the job is handed to the CPU.
     */
    @Override
    public CompletableFuture<LiteCraftTreeNode> requestDisplayTree() {
        var trees = new ObjectArrayList<CompletableFuture<LiteCraftTreeNode>>(this.subPlans.size());
        for (var subPlan : this.subPlans) {
            if (subPlan instanceof CraftingPlanDisplaySource source) {
                trees.add(source.requestDisplayTree());
            }
        }

        if (trees.isEmpty()) {
            return CompletableFuture.completedFuture(buildRoot(List.of()));
        }

        return CompletableFuture
            .allOf(trees.toArray(CompletableFuture[]::new))
            .thenApply(ignored -> {
                var subTrees = new ObjectArrayList<LiteCraftTreeNode>(trees.size());
                for (var tree : trees) {
                    subTrees.add(tree.join());
                }
                return buildRoot(subTrees);
            });
    }

    private LiteCraftTreeNode buildRoot(List<LiteCraftTreeNode> subTrees) {
        List<LiteCraftTreeProc> inputs = subTrees.isEmpty()
            ? List.of()
            : List.of(new LiteCraftTreeProc(List.copyOf(subTrees), List.of(), Map.of()));
        return new LiteCraftTreeNode(null, this.finalOutput, inputs, this.missingItems.get(this.finalOutput.what()));
    }
}
