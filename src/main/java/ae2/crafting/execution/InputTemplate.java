package ae2.crafting.execution;

import ae2.api.crafting.IPatternDetails;
import ae2.api.stacks.AEKey;

/**
 * Represents a single "unit" of input for a slot in a crafting/processing pattern. How many units of input are required
 * for a pattern is returned by {@link IPatternDetails.IInput#getMultiplier()}.
 */
public record InputTemplate(AEKey key, long amount) {
}
