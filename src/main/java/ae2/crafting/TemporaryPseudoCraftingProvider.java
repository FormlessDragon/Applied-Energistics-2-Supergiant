package ae2.crafting;

import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.pattern.AEProcessingPattern;
import ae2.helpers.patternprovider.PseudoPatternDetails;

import java.util.List;

public record TemporaryPseudoCraftingProvider(IPatternDetails pattern) implements ICraftingProvider {
    public TemporaryPseudoCraftingProvider(List<GenericStack> inputs, List<GenericStack> outputs) {
        var patternStack = PatternDetailsHelper.encodeProcessingPattern(inputs, outputs);
        var definition = AEItemKey.of(patternStack);
        if (definition == null) {
            throw new IllegalArgumentException("Failed to create temporary pseudo pattern definition.");
        }
        this(PseudoPatternDetails.wrap(new AEProcessingPattern(definition)));
    }

    public TemporaryPseudoCraftingProvider(IPatternDetails pattern) {
        var basePattern = PseudoPatternDetails.unwrap(pattern);
        if (!(basePattern instanceof AEProcessingPattern)) {
            throw new IllegalArgumentException("Temporary pseudo patterns must be processing patterns.");
        }
        this.pattern = PseudoPatternDetails.wrap(basePattern);
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return List.of(this.pattern);
    }

    @Override
    public int getPatternPriority() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, int multiplier) {
        return PseudoPatternDetails.unwrap(patternDetails).getDefinition().equals(
            PseudoPatternDetails.unwrap(this.pattern).getDefinition());
    }

    @Override
    public boolean canMergePatternPush(IPatternDetails patternDetails) {
        return true;
    }

    @Override
    public int getMaxPatternPushMultiplier(IPatternDetails patternDetails, int maxMultiplier) {
        return PseudoPatternDetails.unwrap(patternDetails).getDefinition().equals(
            PseudoPatternDetails.unwrap(this.pattern).getDefinition()) ? maxMultiplier : 0;
    }

    @Override
    public boolean isBusy() {
        return false;
    }
}
