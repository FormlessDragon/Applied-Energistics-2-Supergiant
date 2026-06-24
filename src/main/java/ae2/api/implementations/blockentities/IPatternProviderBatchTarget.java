package ae2.api.implementations.blockentities;

import ae2.api.crafting.IPatternDetails;
import ae2.api.stacks.KeyCounter;
import net.minecraft.util.EnumFacing;

/**
 * Optional extension for crafting machines that can receive multiple identical pattern pushes from a pattern provider
 * in one operation.
 */
public interface IPatternProviderBatchTarget {
    /**
     * Returns how many copies of {@code patternDetails} can currently be accepted from {@code ejectionDirection}.
     * Implementations must return a Value in the range {@code 0..maxMultiplier}.
     */
    int getMaxPatternPushMultiplier(IPatternDetails patternDetails, KeyCounter[] inputs, int maxMultiplier,
                                    EnumFacing ejectionDirection);
}
