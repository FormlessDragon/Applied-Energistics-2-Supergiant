package ae2.crafting;

import ae2.api.crafting.IPatternDetails;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.graph.CraftingGraphDisplaySnapshot;
import ae2.crafting.graph.LocalDisplayFragment;
import ae2.integration.data.CraftingGraphDisplayTreeConverter;
import ae2.integration.data.CraftingTreeCycles;
import ae2.integration.data.LiteCraftTreeNode;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class CraftingPlanDisplay {
    private static final VarHandle THREAD_IDS;
    private static final VarHandle TREE;
    private static volatile int threadIds;

    static {
        try {
            var lookup = MethodHandles.lookup();
            THREAD_IDS = lookup.findStaticVarHandle(CraftingPlanDisplay.class, "threadIds", int.class);
            TREE = lookup.findVarHandle(CraftingPlanDisplay.class, "tree", CompletableFuture.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @SuppressWarnings("unused")
    private volatile @Nullable CompletableFuture<LiteCraftTreeNode> tree;

    private final GenericStack output;
    private final Object2LongMap<IPatternDetails> patternTimes;
    private final KeyCounter usedItems;
    private final KeyCounter missingItems;
    private final @Nullable CraftingTreeNode legacyTree;
    private final @Nullable CraftingGraphDisplaySnapshot.Builder graphDisplayBuilder;
    private final CraftingAttemptMetrics attemptMetrics;
    private final @Nullable CraftingCalculation calculation;

    private static @Nullable LocalDisplayFragment hydrateMachineInfo(@Nullable LocalDisplayFragment fragment,
                                                                     CraftingCalculation calculation) {
        if (fragment == null) {
            return null;
        }
        var processes = new ObjectArrayList<LocalDisplayFragment.Process>(fragment.processes().size());
        for (int processIndex = 0; processIndex < fragment.processes().size(); processIndex++) {
            var process = fragment.processes().get(processIndex);
            var machines = process.machines();
            var locations = process.locations();
            if (process.pattern() != null && machines.isEmpty()) {
                var info = calculation.getMachineInfo(process.pattern());
                machines = info.groups();
                locations = info.locations();
            }
            var inputs = new ObjectArrayList<LocalDisplayFragment>(process.inputs().size());
            for (int inputIndex = 0; inputIndex < process.inputs().size(); inputIndex++) {
                var input = process.inputs().get(inputIndex);
                inputs.add(hydrateMachineInfo(input, calculation));
            }
            processes.add(new LocalDisplayFragment.Process(process.times(), process.pattern(), machines, locations,
                inputs));
        }
        return new LocalDisplayFragment(fragment.output(), fragment.amount(), fragment.missing(), processes);
    }

    public CraftingPlanDisplay(GenericStack output, Object2LongMap<IPatternDetails> patternTimes,
                               KeyCounter usedItems, KeyCounter missingItems,
                               @Nullable CraftingTreeNode legacyTree,
                               @Nullable CraftingGraphDisplaySnapshot.Builder graphDisplayBuilder,
                               CraftingAttemptMetrics attemptMetrics,
                               @Nullable CraftingCalculation calculation) {
        this.output = output;
        this.patternTimes = patternTimes;
        this.usedItems = usedItems;
        this.missingItems = missingItems;
        this.legacyTree = legacyTree;
        this.graphDisplayBuilder = graphDisplayBuilder;
        this.attemptMetrics = attemptMetrics;
        this.calculation = calculation;
    }

    public CompletableFuture<LiteCraftTreeNode> requestTree() {
        var current = this.tree;
        if (current != null) return current;

        var created = new CompletableFuture<LiteCraftTreeNode>();
        if (!TREE.compareAndSet(this, null, created)) return this.tree;
        try {
            ExecutorHolder.INSTANCE.execute(() -> {
                try {
                    created.complete(materialize());
                } catch (RuntimeException failure) {
                    created.completeExceptionally(failure);
                }
            });
        } catch (RuntimeException failure) {
            created.completeExceptionally(failure);
        }
        return created;
    }

    private LiteCraftTreeNode materialize() {
        long start = System.nanoTime();
        try {
            var builder = this.graphDisplayBuilder;
            if (builder != null) {
                // Build the immutable graph snapshot lazily on first GUI open, then hydrate machine info.
                return CraftingTreeCycles.annotate(CraftingGraphDisplayTreeConverter.toLiteTree(
                    hydrateMachineInfo(builder.build()), output.amount(), missingItems));
            }
            if (legacyTree != null) {
                var allocator = new LiteCraftTreeNode.PatternTimesAllocator(patternTimes, usedItems, missingItems);
                var root = LiteCraftTreeNode.of(legacyTree, null, output.amount(), allocator);
                // A repaired plan can craft more than the tree requested, see CraftingPlan#withContents
                var withUnshown = allocator.withUnshownCrafts();
                if (withUnshown != null) {
                    root = LiteCraftTreeNode.of(legacyTree, null, output.amount(), withUnshown);
                }
                return CraftingTreeCycles.annotate(root);
            }
            throw new IllegalStateException("Crafting plan has no display source");
        } finally {
            attemptMetrics.recordDisplayMaterialization(System.nanoTime() - start);
        }
    }

    private CraftingGraphDisplaySnapshot hydrateMachineInfo(CraftingGraphDisplaySnapshot snapshot) {
        var calculation = this.calculation;
        if (calculation == null) {
            return snapshot;
        }
        var nodes = new ObjectArrayList<CraftingGraphDisplaySnapshot.Node>(snapshot.nodes().size());
        for (int nodeIndex = 0; nodeIndex < snapshot.nodes().size(); nodeIndex++) {
            var node = snapshot.nodes().get(nodeIndex);
            var machines = node.machines();
            var locations = node.machineLocations();
            if (node.executed() && node.pattern() != null && machines.isEmpty()) {
                var info = calculation.getMachineInfo(node.pattern());
                machines = info.groups();
                locations = info.locations();
            }
            nodes.add(new CraftingGraphDisplaySnapshot.Node(node.what(), node.pattern(), node.outputPerCraft(),
                node.demandAmount(), node.craftTimes(), node.executed(), node.inputs(), machines, locations,
                hydrateMachineInfo(node.localDisplay(), calculation), node.external(), node.missing(),
                node.componentId(), node.componentOrder()));
        }
        return new CraftingGraphDisplaySnapshot(snapshot.rootWhat(), snapshot.requestedAmount(), nodes);
    }

    private static final class ExecutorHolder {
        private static final ThreadPoolExecutor INSTANCE = new ThreadPoolExecutor(
            1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8), task -> {
            var thread = new Thread(task, "AE2 Crafting Tree Display " + ((int) THREAD_IDS.getAndAdd(1) + 1));
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }
}
