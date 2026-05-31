package ae2.helpers.patternprovider;

import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.PatternDetailsTooltip;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.pattern.AEProcessingPattern;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.world.World;

import java.util.List;

public final class PseudoPatternDetails implements IPatternDetails {
    private final IPatternDetails delegate;

    private PseudoPatternDetails(IPatternDetails delegate) {
        this.delegate = delegate;
    }

    public static IPatternDetails wrap(IPatternDetails details) {
        if (!(details instanceof AEProcessingPattern)) {
            return details;
        }
        return new PseudoPatternDetails(details);
    }

    public static boolean isPseudo(IPatternDetails details) {
        return details instanceof PseudoPatternDetails;
    }

    public static IPatternDetails unwrap(IPatternDetails details) {
        return details instanceof PseudoPatternDetails pseudo ? pseudo.delegate : details;
    }

    @Override
    public AEItemKey getDefinition() {
        return delegate.getDefinition();
    }

    @Override
    public IInput[] getInputs() {
        return delegate.getInputs();
    }

    @Override
    public List<GenericStack> getOutputs() {
        return delegate.getOutputs();
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return delegate.supportsPushInputsToExternalInventory();
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
        delegate.pushInputsToExternalInventory(inputHolder, inputSink);
    }

    @Override
    public PatternDetailsTooltip getTooltip(World level, ITooltipFlag flags) {
        return delegate.getTooltip(level, flags);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PseudoPatternDetails other && this.delegate.equals(other.delegate);
    }

    @Override
    public int hashCode() {
        return 31 * this.delegate.hashCode() + 1;
    }
}
