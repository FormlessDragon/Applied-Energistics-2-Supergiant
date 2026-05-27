package appeng.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PseudoPatternDetails;

import java.util.List;

public final class TemporaryPseudoCraftingProvider implements ICraftingProvider {
    private final IPatternDetails pattern;

    public TemporaryPseudoCraftingProvider(List<GenericStack> inputs, List<GenericStack> outputs) {
        var patternStack = PatternDetailsHelper.encodeProcessingPattern(inputs, outputs);
        var definition = AEItemKey.of(patternStack);
        if (definition == null) {
            throw new IllegalArgumentException("Failed to create temporary pseudo pattern definition.");
        }
        this.pattern = PseudoPatternDetails.wrap(new AEProcessingPattern(definition));
    }

    public TemporaryPseudoCraftingProvider(IPatternDetails pattern) {
        this.pattern = PseudoPatternDetails.wrap(PseudoPatternDetails.unwrap(pattern));
    }

    public IPatternDetails pattern() {
        return this.pattern;
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
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        return PseudoPatternDetails.unwrap(patternDetails).getDefinition().equals(
            PseudoPatternDetails.unwrap(this.pattern).getDefinition());
    }

    @Override
    public boolean isBusy() {
        return false;
    }
}
