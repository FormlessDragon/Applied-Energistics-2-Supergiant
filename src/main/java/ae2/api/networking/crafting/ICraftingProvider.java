/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package ae2.api.networking.crafting;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.IGridNodeService;
import ae2.api.networking.IManagedGridNode;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;

import java.util.List;
import java.util.Set;

/**
 * Allows a node to provide crafting patterns and emitable items to the network.
 */
public interface ICraftingProvider extends IGridNodeService {
    /**
     * This convenience method can be used when the crafting options or emitable items have changed to request an update
     * of the crafting service's cache.This only works if the given managed grid node provides this service.
     */
    static void requestUpdate(IManagedGridNode managedNode) {
        var node = managedNode.getNode();
        if (node != null) {
            node.grid().getCraftingService().refreshNodeCraftingProvider(node);
        }
    }

    /**
     * Return the patterns offered by this provider. {@link #pushPattern} will be called if they need to be crafted.
     */
    List<? extends IPatternDetails> getAvailablePatterns();

    /**
     * Return the priority for the patterns offered by this provider. The crafting calculation will prioritize patterns
     * with the highest priority.
     */
    default int getPatternPriority() {
        return 0;
    }

    /**
     * Instruct a provider to craft one of the patterns.
     *
     * @param patternDetails details
     * @param inputHolder    the requested stacks, for each input slot of the pattern
     * @param multiplier     the number of pattern pushes merged into this request
     * @return if the pattern was successfully pushed.
     */
    boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, int multiplier);

    /**
     * Return true if this provider wants to use the merged pattern push path for the given pattern.
     * Returning false does not mean the pattern cannot be pushed; it means the crafting CPU must use the normal
     * one-pattern push path instead.
     */
    boolean canMergePatternPush(IPatternDetails patternDetails);

    /**
     * Return the maximum number of pattern pushes that can currently be merged for the given pattern.
     * This method may only be called after {@link #canMergePatternPush(IPatternDetails)} returned true for the same
     * pattern. Returning 0 means this provider is currently unavailable for this pattern and no normal one-pattern
     * fallback should be attempted for this provider in the current pass.
     */
    int getMaxPatternPushMultiplier(IPatternDetails patternDetails, int maxMultiplier);

    /**
     * @return if this is true, the crafting engine will refuse to send patterns to this provider.
     */
    boolean isBusy();

    /**
     * Return the emitable items offered by this provider. They should be crafted and inserted into the network when
     * {@link ICraftingService#isRequesting} is true.
     */
    default Set<AEKey> getEmitableItems() {
        return Set.of();
    }
}
