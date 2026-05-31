package ae2.parts.automation;

import ae2.api.behaviors.StackImportStrategy;
import ae2.api.behaviors.StackTransferContext;

import java.util.List;

/**
 * Simply iterates over a list of {@link StackImportStrategy} and exposes them as a single strategy. First come, first
 * served.
 */
public class StackImportFacade implements StackImportStrategy {
    private final List<StackImportStrategy> strategies;

    public StackImportFacade(List<StackImportStrategy> strategies) {
        this.strategies = strategies;
    }

    @Override
    public boolean transfer(StackTransferContext context) {
        for (var strategy : strategies) {
            if (strategy.transfer(context)) {
                return true;
            }
        }
        return true;
    }
}
