package ae2.api.networking.crafting;

/**
 * Optional extension for {@link ICraftingRequester automated crafting requesters} that can explicitly opt in to
 * submitting plans with missing ingredients.
 */
public interface ICraftingForceStartRequester extends ICraftingRequester {

    /**
     * @return true if this requester allows the given plan to start even though it has missing ingredients.
     */
    boolean canForceStartCrafting(ICraftingPlan plan);
}
