package ae2.api.networking.crafting;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.IGridNode;
import ae2.api.networking.security.IActionHost;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEKey;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * The source of a crafting simulation request. This allows the crafting simulation to keep track of the current grid
 * across multiple ticks to simulate item extraction or to explore more patterns.
 * <p>
 * Returning null in one of the functions will just prevent extraction or exploration of patterns, likely leading to an
 * unsuccessful {@link ICraftingPlan}.
 */
public interface ICraftingSimulationRequester {
    /**
     * Return the current action source, used to extract items.
     */
    @Nullable
    IActionSource getActionSource();

    /**
     * Return the current grid node, used to access the current grid state.
     */
    @Nullable
    default IGridNode getGridNode() {
        var actionSource = getActionSource();
        if (actionSource != null) {
            return actionSource.machine().map(IActionHost::getActionableNode).orElse(null);
        }
        return null;
    }

    default List<IPatternDetails> getAdditionalPatterns() {
        return List.of();
    }

    default List<ICraftingProvider> getAdditionalProviders() {
        return List.of();
    }

    /**
     * Returns the material closure of the given output if the requester already has it cached, or null to let the
     * calculation derive it itself.
     * <p>
     * The returned keys are copied from the network inventory before the job runs, so an incomplete answer makes the
     * simulation see less than the network holds and report missing ingredients. Implementations must therefore only
     * return a closure covering every key the request can consume, not just the keys one demand touches, and must
     * return null unless they are certain of it.
     */
    @Nullable
    default Collection<AEKey> getSimulationSubset(AEKey output) {
        return null;
    }
}
