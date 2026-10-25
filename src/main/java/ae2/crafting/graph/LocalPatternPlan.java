package ae2.crafting.graph;

import ae2.api.crafting.IPatternDetails;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.CraftingCalculation;
import ae2.crafting.inv.CraftingSimulationDiff;
import ae2.crafting.inv.CraftingSimulationState;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;

/**
 * Frozen result of planning one compatibility unit. It contains no live legacy crafting tree.
 */
public final class LocalPatternPlan {
    private final IPatternDetails pattern;
    private final long craftTimes;
    private final KeyCounter boundaryDemands;
    private final CraftingSimulationDiff diff;
    private final CraftingCalculation.CalculationDelta calculationDelta;
    private final LocalDisplayFragment displayFragment;
    private boolean committed;

    public LocalPatternPlan(IPatternDetails pattern, long craftTimes, KeyCounter boundaryDemands,
                            CraftingSimulationDiff diff,
                            CraftingCalculation.CalculationDelta calculationDelta,
                            LocalDisplayFragment displayFragment) {
        this.pattern = pattern;
        this.craftTimes = craftTimes;
        this.boundaryDemands = copy(boundaryDemands);
        this.diff = diff;
        this.calculationDelta = copy(calculationDelta);
        // LocalDisplayFragment is an immutable record already frozen by its compact constructor.
        this.displayFragment = displayFragment;
    }

    public IPatternDetails pattern() {
        return this.pattern;
    }

    public long craftTimes() {
        return this.craftTimes;
    }

    public KeyCounter boundaryDemands() {
        return copy(this.boundaryDemands);
    }

    public LocalDisplayFragment displayFragment() {
        return displayFragment;
    }

    /**
     * Whether the frozen inventory diff of this plan can still be applied to the given parent state. Reusing a
     * previously discovered plan is only safe when the execution inventory still satisfies its extracted inputs.
     */
    public boolean canApplyTo(CraftingSimulationState parent) {
        return this.diff.canApplyTo(parent);
    }

    public void commit(CraftingCalculation calculation, CraftingSimulationState inventory) {
        if (this.committed) {
            throw new IllegalStateException("Local pattern plan was already committed");
        }
        var previousMarker = calculation.createCalculationMarker();
        try {
            calculation.mergeCalculationDelta(this.calculationDelta);
            this.diff.applyTo(inventory);
            this.committed = true;
        } catch (RuntimeException | Error failure) {
            calculation.restoreCalculationMarker(previousMarker);
            throw failure;
        }
    }

    private static KeyCounter copy(KeyCounter source) {
        var result = KeyCounter.checked();
        result.addAll(source);
        return result;
    }

    private static CraftingCalculation.CalculationDelta copy(
        CraftingCalculation.CalculationDelta source) {
        var recursiveDisplayRequests = new Reference2LongOpenHashMap<>(source.recursiveDisplayRequests());
        return new CraftingCalculation.CalculationDelta(
            copy(source.missingItems()),
            copy(source.recursiveMissingSeeds()),
            new ObjectOpenHashSet<>(source.realSeededRecursiveRequests()),
            new ObjectOpenHashSet<>(source.realRecursiveSeeds()),
            new ObjectOpenHashSet<>(source.realSeededRecursiveKeys()),
            new ObjectOpenHashSet<>(source.recursiveFinalOutputInputs()),
            copy(source.recursiveReserveCandidates()),
            recursiveDisplayRequests,
            source.intermediateFinalOutputAmount());
    }
}
