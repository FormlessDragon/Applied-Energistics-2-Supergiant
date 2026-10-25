package ae2.crafting.graph;

import ae2.api.crafting.IPatternDetails;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.CraftingCalculation;
import ae2.crafting.inv.CraftingSimulationDiff;
import ae2.crafting.inv.CraftingSimulationState;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Frozen transaction for one recursive component that cannot use the native closed-form solver.
 */
public final class LocalComponentPlan {
    public record Entry(int nodeIndex, @Nullable IPatternDetails pattern, long craftTimes,
                        LocalDisplayFragment display) {
    }

    private final List<Entry> entries;
    private final KeyCounter boundaryDemands;
    private final CraftingSimulationDiff diff;
    private final CraftingCalculation.CalculationDelta delta;
    private boolean committed;

    public LocalComponentPlan(List<Entry> entries, KeyCounter boundaryDemands,
                              CraftingSimulationDiff diff, CraftingCalculation.CalculationDelta delta) {
        // Entries are built by the caller and never shared afterwards; a single shallow freeze is enough.
        // Each LocalDisplayFragment is an immutable record already frozen by its compact constructor.
        this.entries = Collections.unmodifiableList(entries);
        this.boundaryDemands = copy(boundaryDemands);
        this.diff = diff;
        this.delta = copy(delta);
    }

    public List<Entry> entries() {
        return entries;
    }

    public KeyCounter boundaryDemands() {
        return copy(boundaryDemands);
    }

    public void commit(CraftingCalculation calculation, CraftingSimulationState inventory) {
        if (committed) throw new IllegalStateException("Local component plan was already committed");
        var previousMarker = calculation.createCalculationMarker();
        try {
            calculation.mergeCalculationDelta(delta);
            diff.applyTo(inventory);
            committed = true;
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
