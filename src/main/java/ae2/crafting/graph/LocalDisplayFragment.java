package ae2.crafting.graph;

import ae2.api.crafting.IPatternDetails;
import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKey2LongMap;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.CraftingTreeNode;
import ae2.crafting.execution.CraftingSupplierLocation;
import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable display-only projection of one legacy compatibility unit.
 */
public record LocalDisplayFragment(AEKey output, long amount, long missing, List<Process> processes) {
    public LocalDisplayFragment {
        processes = Collections.unmodifiableList(processes);
    }

    public record Process(long times, @Nullable IPatternDetails pattern,
                          List<PatternContainerGroup> machines,
                          Map<PatternContainerGroup, List<CraftingSupplierLocation>> locations,
                          List<LocalDisplayFragment> inputs) {

        public Process {
            var copiedLocations = new Object2ObjectLinkedOpenHashMap<PatternContainerGroup, List<CraftingSupplierLocation>>();
            for (var machine : machines) {
                var machineLocations = locations.get(machine);
                if (machineLocations != null && !machineLocations.isEmpty()) {
                    copiedLocations.put(machine, machineLocations);
                }
            }
            machines = Collections.unmodifiableList(machines);
            locations = Object2ObjectMaps.unmodifiable(copiedLocations);
            inputs = Collections.unmodifiableList(inputs);
        }
    }

    public static final class CaptureContext {
        private final AEKey2LongMap remainingMissing = new AEKey2LongMap.OpenHashMap();

        public CaptureContext() {
        }

        public CaptureContext(KeyCounter missingBudget) {
            for (var entry : missingBudget) {
                remainingMissing.put(entry.getKey(), entry.getLongValue());
            }
        }
    }

    public static LocalDisplayFragment capture(CraftingTreeNode node, long amount) {
        return capture(node, amount, Set.of());
    }

    public static LocalDisplayFragment capture(CraftingTreeNode node, long amount, Set<AEKey> boundaryKeys) {
        return capture(node, amount, boundaryKeys, new CaptureContext());
    }

    public static LocalDisplayFragment capture(CraftingTreeNode node, long amount, Set<AEKey> boundaryKeys,
                                               CaptureContext context) {
        return capture(node, amount, boundaryKeys, new ReferenceOpenHashSet<>(),
            context.remainingMissing);
    }

    private static LocalDisplayFragment capture(CraftingTreeNode node, long amount, Set<AEKey> boundaryKeys,
                                                Set<CraftingTreeNode> path,
                                                AEKey2LongMap remainingMissing) {
        long missing = allocateMissing(node, amount, remainingMissing);
        if (!path.add(node)) {
            return new LocalDisplayFragment(node.getWhat(), amount, missing, List.of());
        }
        try {
            var processes = node.getDisplayNodes();
            if (processes == null || processes.isEmpty()) {
                return new LocalDisplayFragment(node.getWhat(), amount, missing, List.of());
            }
            var captured = new ObjectArrayList<Process>(processes.size());
            for (var process : processes) {
                long processTimes = process.getTreeDisplayTimes();
                if (processTimes <= 0) continue;
                var inputs = new ObjectArrayList<LocalDisplayFragment>();
                for (var entry : process.getNodes().object2LongEntrySet()) {
                    var child = entry.getKey();
                    if (boundaryKeys.contains(child.getWhat())) {
                        // Boundary leaves are supplied by the graph snapshot and are not duplicated here.
                        continue;
                    }
                    long childAmount = LongMath.saturatedMultiply(child.getAmount(), entry.getLongValue());
                    childAmount = LongMath.saturatedMultiply(childAmount, processTimes);
                    if (childAmount <= 0) continue;
                    inputs.add(capture(child, childAmount, boundaryKeys, path, remainingMissing));
                }
                captured.add(new Process(processTimes, process.getDetails(), List.of(), Map.of(), inputs));
            }
            return new LocalDisplayFragment(node.getWhat(), amount, missing, captured);
        } finally {
            path.remove(node);
        }
    }

    private static long allocateMissing(CraftingTreeNode node, long amount,
                                        AEKey2LongMap remainingMissing) {
        long remaining = remainingMissing.getLong(node.getWhat());
        long allocated = Math.clamp(amount, 0, remaining);
        remainingMissing.addTo(node.getWhat(), -allocated);
        return allocated;
    }
}
