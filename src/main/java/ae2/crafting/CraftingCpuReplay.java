package ae2.crafting;

import ae2.api.config.Actionable;
import ae2.api.crafting.IAssemblerPattern;
import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKey2LongMap;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.container.ContainerConstants;
import ae2.crafting.inv.ICraftingInventory;
import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.function.ObjLongConsumer;

/**
 * Replays a plan the way a crafting CPU runs it. The CPU starts with the used, emitted and missing items of the plan
 * and visits its tasks in turn, in the order of {@link ae2.crafting.execution.ExecutingCraftingJob#tasks}, pushing one
 * craft per visit whose inputs it holds, see {@link ae2.crafting.execution.CraftingCpuLogic}. Like
 * {@link ae2.crafting.execution.CraftingCpuHelper#extractPatternInputs}, an input takes its possible stacks, and the keys
 * of their fuzzy groups the input accepts, in order until it has enough.
 */
final class CraftingCpuReplay {
    private static final int MAX_PASSES = 1_000_000;

    private final World level;
    private final CraftingCalculation calculation;
    private final AEKey2LongMap inventory = new AEKey2LongMap.OpenHashMap();
    // Capture the CPU map's final order once. Repeated passes only need the remaining tasks in that order.
    private final ObjectArrayList<Task> tasks;
    private final Reference2ObjectOpenHashMap<IPatternDetails.IInput, ObjectArrayList<GenericStack>> candidates =
        new Reference2ObjectOpenHashMap<>();
    private AEKey2LongMap passDelta;
    private AEKey2LongMap passTaken;
    private final CraftingSeedReservations seeds;
    private final Map<IPatternDetails, KeyCounter> remainderAllocations = new Object2ObjectOpenHashMap<>();
    // A Taken value is consumed before the next takeOnce call. Reuse its maps across replay passes.
    private AEKey2LongMap takenKeys;
    private AEKey2LongMap takenContainers;
    // Every key the replay can hold, by fuzzy group, and the candidates of each input among them
    private final Object2ObjectOpenHashMap<Object, ObjectArrayList<AEKey>> keysByGroup = new Object2ObjectOpenHashMap<>();
    private Taken taken;
    private AEKey2LongMap passReserve;
    private AEKey2LongMap batchDelta;
    private Object2ObjectOpenHashMap<IPatternDetails, KeyCounter> inputAllocations;
    private boolean independentCycleBatches;

    private CraftingCpuReplay(CraftingCalculation calculation, ICraftingPlan plan, KeyCounter extra) {
        this.calculation = calculation;
        this.level = calculation.getLevel();
        ObjLongConsumer<AEKey> addToInventory = this.inventory::addTo;
        plan.usedItems().forEach(addToInventory);
        plan.emittedItems().forEach(addToInventory);
        plan.missingItems().forEach(addToInventory);
        extra.forEach(addToInventory);
        // Keep the same default capacity and insertion sequence as ExecutingCraftingJob.tasks.
        var taskMap = new Object2ObjectOpenHashMap<IPatternDetails, Task>();
        for (var entry : plan.patternTimes().object2LongEntrySet()) {
            taskMap.put(entry.getKey(), new Task(entry.getKey(), entry.getLongValue()));
        }
        this.tasks = new ObjectArrayList<>(taskMap.values());
        this.seeds = new CraftingSeedReservations(taskMap.keySet(), pattern -> taskMap.get(pattern).remaining);
        for (var key : this.inventory.keySet()) {
            index(key);
        }
        for (int i = 0; i < this.tasks.size(); i++) {
            var task = this.tasks.get(i);
            for (int outputIndex = 0; outputIndex < task.outputs.size(); outputIndex++) {
                var output = task.outputs.get(outputIndex);
                index(output.what());
            }
            for (var input : task.inputs) {
                for (var possible : input.possibleInputs()) {
                    index(possible.what());
                    var container = input.getRemainingKey(possible.what());
                    if (container != null) {
                        index(container);
                    }
                }
            }
        }
    }

    /**
     * @return whether the CPU runs every craft of the plan and ends up with the requested output
     */
    static boolean runs(ICraftingPlan plan, CraftingCalculation calculation) throws InterruptedException {
        calculation.handlePausing();
        var replay = new CraftingCpuReplay(calculation, plan, new KeyCounter());
        if (replay.orderFree()) {
            return replay.balances(plan);
        }
        boolean runs = replay.run() && replay.coversOutput(plan);
        if (runs) replay.recordAllocations(plan);
        return runs;
    }

    /**
     * Native graph balance proves material feasibility, but fixed assembler recipes still need their callbacks.
     */
    static boolean validatesNativeRecipes(ICraftingPlan plan, CraftingCalculation calculation) throws InterruptedException {
        for (var pattern : plan.patternTimes().keySet()) {
            if (pattern instanceof IAssemblerPattern assembler) {
                calculation.handlePausing();
                if (!hasFixedResultWithoutRemainders(pattern.getInputs(), assembler, calculation.getLevel())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The items the CPU would have to be given on top of the plan to run it, found by running the plan and, whenever
     * no craft can run, adding everything the stuck crafts lack. Null when the replay gives up.
     */
    static @Nullable KeyCounter lacking(ICraftingPlan plan, CraftingCalculation calculation) throws InterruptedException {
        var extra = new KeyCounter();
        for (int attempt = 0; attempt < 1024; attempt++) {
            calculation.handlePausing();
            var replay = new CraftingCpuReplay(calculation, plan, extra);
            if (replay.run()) {
                var output = plan.finalOutput();
                long shortOfOutput = output.amount() - replay.inventory.getLong(output.what());
                if (shortOfOutput <= 0) {
                    replay.recordAllocations(plan);
                    return extra;
                }
                // Crafts may take some of the output given to them as well, so check again
                extra.add(output.what(), shortOfOutput);
                continue;
            }
            var shortages = replay.shortages();
            if (shortages.isEmpty()) {
                return null;
            }
            extra.addAll(shortages);
        }
        return null;
    }

    private static boolean hasFixedResultWithoutRemainders(IPatternDetails.IInput[] inputs,
                                                           IAssemblerPattern assembler, World level) {
        if (CraftingCalculation.isCalculationThread()) {
            return CraftingCalculation.runOnMainThread(() -> hasFixedResultWithoutRemainders(inputs, assembler, level));
        }
        var table = new KeyCounter[inputs.length];
        for (int slot = 0; slot < inputs.length; slot++) {
            var input = inputs[slot];
            table[slot] = new KeyCounter();
            var possible = input.possibleInputs();
            if (possible.length == 1) {
                table[slot].add(possible[0].what(),
                    LongMath.saturatedMultiply(possible[0].amount(), input.getMultiplier()));
            }
        }
        var grid = assemble(assembler, table, level);
        if (grid == null) return false;
        var remainders = assembler.getRemainingItems(grid);
        for (int slot = 0; slot < remainders.size(); slot++) {
            if (!remainders.get(slot).isEmpty()) return false;
        }
        return true;
    }

    private boolean coversOutput(ICraftingPlan plan) {
        var output = plan.finalOutput();
        return this.inventory.getLong(output.what()) >= output.amount();
    }

    private static void addEncodedInputs(Task task, KeyCounter consumed, long crafts) {
        if (crafts <= 0) return;
        for (var input : task.inputs) {
            var possible = input.possibleInputs();
            if (possible.length == 0) continue;
            consumed.add(possible[0].what(), LongMath.saturatedMultiply(crafts,
                LongMath.saturatedMultiply(input.getMultiplier(), possible[0].amount())));
        }
    }

    private static @Nullable InventoryCrafting assemble(IAssemblerPattern assembler, KeyCounter[] table, World level) {
        var grid = new InventoryCrafting(ContainerConstants.EMPTY_CONTAINER, 3, 3);
        assembler.fillCraftingGrid(table, grid::setInventorySlotContents);
        var result = GenericStack.fromItemStack(assembler.assemble(grid, level));
        return assembler.getPrimaryOutput().equals(result) ? grid : null;
    }

    private void recordAllocations(ICraftingPlan plan) {
        if (plan instanceof CraftingPlan concrete) {
            concrete.recordInputAllocations(this.inputAllocations == null ? Map.of() : this.inputAllocations);
            concrete.recordRemainderAllocations(this.remainderAllocations);
        }
    }

    private void index(AEKey key) {
        var group = this.keysByGroup.computeIfAbsent(key.getPrimaryKey(), g -> new ObjectArrayList<>(1));
        if (!group.contains(key)) {
            group.add(key);
            this.candidates.clear();
        }
    }

    private boolean balances(ICraftingPlan plan) {
        // This is the terminal totals-only path; the replay is discarded after returning the result.
        var balance = this.inventory;
        for (int i = 0; i < this.tasks.size(); i++) {
            var task = this.tasks.get(i);
            long times = task.remaining;
            for (var input : task.inputs) {
                var possible = input.possibleInputs();
                if (possible.length == 0) continue;
                balance.addTo(possible[0].what(), -LongMath.saturatedMultiply(
                    LongMath.saturatedMultiply(possible[0].amount(), input.getMultiplier()), times));
            }
            for (int outputIndex = 0; outputIndex < task.outputs.size(); outputIndex++) {
                var output = task.outputs.get(outputIndex);
                balance.addTo(output.what(), LongMath.saturatedMultiply(output.amount(), times));
            }
        }
        var values = balance.values().iterator();
        while (values.hasNext()) {
            if (values.nextLong() < 0) return false;
        }
        var output = plan.finalOutput();
        return balance.getLong(output.what()) >= output.amount();
    }

    /*
     * Without a cycle among the crafts, inputs with a choice of stacks, or containers, no craft can keep another from
     * ever running: every key is made by crafts that do not wait for its consumers, and what one consumer takes is not
     * what another needs instead. Whether the plan runs then only depends on its totals.
     */
    private boolean orderFree() {
        if (this.seeds.hasCycles()) return false;
        for (int i = 0; i < this.tasks.size(); i++) {
            var task = this.tasks.get(i);
            var pattern = task.pattern;
            if (pattern instanceof IAssemblerPattern assembler
                && (assembler.canSubstitute() || assembler.canSubstituteFluids())) return false;
            for (var input : task.inputs) {
                var possible = input.possibleInputs();
                if (possible.length > 1 || candidates(input).size() > 1) return false;
                if (possible.length == 1 && input.getRemainingKey(possible[0].what()) != null) return false;
            }
        }
        // Fixed encoded inputs do not guarantee that the recipe still matches or has no recipe-specific
        // remainders. Only run the callbacks once the structural checks allow the totals-only path.
        for (int i = 0; i < this.tasks.size(); i++) {
            var task = this.tasks.get(i);
            if (task.pattern instanceof IAssemblerPattern assembler
                && !hasFixedResultWithoutRemainders(task.inputs, assembler, this.level)) return false;
        }
        return true;
    }

    /*
     * Each pass visits every task once and pushes one craft of it if its inputs are there. A pass the CPU just ran the
     * same way twice runs the same way again as long as what it takes stays there and no idle task gets what it waits
     * for, so it is repeated at once that often: a catalyst or container passed between crafts costs a few passes, not
     * one per craft.
     */
    private boolean run() throws InterruptedException {
        this.independentCycleBatches = fixedInputChoices();
        // Totals-only validation never needs replay scratch state.
        this.passDelta = new AEKey2LongMap.OpenHashMap(this.tasks.size());
        this.passTaken = new AEKey2LongMap.OpenHashMap(this.tasks.size());
        this.passReserve = new AEKey2LongMap.OpenHashMap();
        this.batchDelta = new AEKey2LongMap.OpenHashMap();
        this.takenKeys = new AEKey2LongMap.OpenHashMap(4);
        this.takenContainers = new AEKey2LongMap.OpenHashMap(1);
        this.taken = new Taken(this.takenKeys, this.takenContainers);
        var crafted = new ObjectArrayList<Task>(this.tasks.size());
        var previousCrafted = new ObjectArrayList<Task>(this.tasks.size());
        var previousDelta = new AEKey2LongMap.OpenHashMap(this.tasks.size());
        var idle = new ObjectArrayList<Task>();
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            crafted.clear();
            idle.clear();
            this.passDelta.clear();
            this.passTaken.clear();
            this.passReserve.clear();
            boolean exact = true;
            for (int taskIndex = 0; taskIndex < this.tasks.size(); taskIndex++) {
                this.calculation.handlePausing();
                var task = this.tasks.get(taskIndex);
                if (task.remaining <= 0) continue;
                var taken = takeOnce(task);
                if (taken == null) {
                    idle.add(task);
                    continue;
                }
                apply(task, taken);
                task.remaining--;
                crafted.add(task);
                boolean sameGrid = task.lastAssemblerResult == null ? taken.exact
                    : task.previousAssemblerResult != null
                        && task.lastAssemblerResult.inputs().equals(task.previousAssemblerResult.inputs());
                task.previousAssemblerResult = task.lastAssemblerResult;
                exact &= sameGrid && !repeatIndependentCycle(task, taken);
                if (this.tasks.size() == 1 && task.lastAssemblerResult != null) {
                    var next = task.remaining > 0 ? task.assemblerSimulation.select(view(task), false) : null;
                    if (next != null) {
                        var batch = CraftingAssemblerBatch.plan(task.assemblerSimulation, next, view(task), task.remaining);
                        if (task.consumed == null) {
                            task.consumed = new KeyCounter();
                            addEncodedInputs(task, task.consumed, task.total - task.remaining);
                            if (this.inputAllocations == null) this.inputAllocations = new Object2ObjectOpenHashMap<>();
                            this.inputAllocations.put(task.pattern, task.consumed);
                        }
                        task.consumed.addAll(batch.consumed);
                        batch.delta.forEach((key, amount) -> {
                            index(key);
                            this.inventory.addTo(key, amount);
                        });
                        if (!batch.returned.isEmpty())
                            this.remainderAllocations.computeIfAbsent(task.pattern, p -> new KeyCounter())
                                                     .addAll(batch.returned);
                        task.remaining -= batch.crafts;
                        exact = false;
                    }
                }
            }
            if (crafted.isEmpty()) {
                return idle.isEmpty();
            }
            if (exact && crafted.equals(previousCrafted) && this.passDelta.equals(previousDelta)) {
                repeatPass(crafted, idle);
            }
            previousCrafted.clear();
            previousCrafted.addAll(crafted);
            previousDelta.clear();
            previousDelta.putAll(this.passDelta);
            int remaining = 0;
            for (int i = 0; i < this.tasks.size(); i++) {
                var task = this.tasks.get(i);
                if (task.remaining > 0) this.tasks.set(remaining++, task);
            }
            this.tasks.size(remaining);
        }
        return false;
    }

    /**
     * A component containing just this recipe cannot wait on another member to return its seed. Finish its available
     * sequential crafts in one arithmetic step; consumers outside the component already preserve its seed. This
     * avoids billions of alternating grow/consume passes, without allocating a schedule or assuming instant CPU IO.
     */
    private boolean repeatIndependentCycle(Task task, Taken taken) {
        if (!this.independentCycleBatches || !taken.exact || task.remaining <= 0 || task.pattern instanceof IAssemblerPattern
            || !this.seeds.isSinglePatternCycle(task.pattern)) return false;
        for (var input : task.inputs) {
            if (candidates(input).size() != 1) return false;
        }
        var delta = this.batchDelta;
        delta.clear();
        taken.keys.forEachEntry((key, amount) -> delta.addTo(key, -amount));
        taken.containers.forEachEntry(delta::addTo);
        for (var output : task.outputs) delta.addTo(output.what(), output.amount());
        long repeats = task.remaining;
        for (var entry : taken.keys.object2LongEntrySet()) {
            var key = entry.getKey();
            long available = this.inventory.getLong(key) - this.seeds.reserved(task.pattern, key);
            long required = entry.getLongValue();
            if (available < required) return false;
            long loss = -delta.getLong(key);
            if (loss > 0) repeats = Math.min(repeats, (available - required) / loss + 1);
        }
        if (repeats <= 0) return false;
        for (var entry : delta.object2LongEntrySet()) {
            long change = LongMath.saturatedMultiply(entry.getLongValue(), repeats);
            this.inventory.addTo(entry.getKey(), change);
            this.passDelta.addTo(entry.getKey(), change);
        }
        task.remaining -= repeats;
        repeatRemainders(task, repeats);
        if (task.consumed != null) addEncodedInputs(task, task.consumed, repeats);
        return true;
    }

    private boolean fixedInputChoices() {
        for (var task : this.tasks) {
            if (task.pattern instanceof IAssemblerPattern) return false;
            for (var input : task.inputs) {
                if (candidates(input).size() != 1) return false;
            }
        }
        return true;
    }

    private void repeatPass(ObjectArrayList<Task> crafted, ObjectArrayList<Task> idle) {
        // A task visited before the crafts that made its inputs waits for the next pass, which then runs differently
        for (int patternIndex = 0; patternIndex < idle.size(); patternIndex++) {
            var task = idle.get(patternIndex);
            if (takeOnce(task) != null) {
                return;
            }
        }
        long repeats = Long.MAX_VALUE;
        for (int patternIndex = 0; patternIndex < crafted.size(); patternIndex++) {
            repeats = Math.min(repeats, crafted.get(patternIndex).remaining);
        }
        for (var entry : this.passDelta.object2LongEntrySet()) {
            long delta = entry.getLongValue();
            if (delta >= 0) continue;
            // Every repeat starts with at least what the pass took, so each of its crafts finds its inputs
            long spare = this.inventory.getLong(entry.getKey()) - this.passTaken.getLong(entry.getKey())
                - this.passReserve.getLong(entry.getKey());
            repeats = Math.min(repeats, spare < 0 ? 0 : spare / -delta);
        }
        // An idle task must stay idle: stop before any of its short inputs could be covered
        for (int patternIndex = 0; patternIndex < idle.size(); patternIndex++) {
            var task = idle.get(patternIndex);
            for (var input : task.inputs) {
                var possible = input.possibleInputs();
                if (possible.length == 0) continue;
                long held = 0;
                long gained = 0;
                var options = candidates(input);
                for (int candidateIndex = 0; candidateIndex < options.size(); candidateIndex++) {
                    var candidate = options.get(candidateIndex);
                    held = LongMath.saturatedAdd(held, Math.max(0, this.inventory.getLong(candidate.what())
                        - this.seeds.reserved(task.pattern, candidate.what())));
                    gained = LongMath.saturatedAdd(gained, Math.max(0, this.passDelta.getLong(candidate.what())));
                }
                long need = LongMath.saturatedMultiply(possible[0].amount(), input.getMultiplier());
                if (gained > 0 && held < need) {
                    repeats = Math.min(repeats, (need - 1 - held) / gained);
                }
            }
        }
        if (repeats <= 0 || repeats == Long.MAX_VALUE) {
            return;
        }
        for (int patternIndex = 0; patternIndex < crafted.size(); patternIndex++) {
            var task = crafted.get(patternIndex);
            task.remaining -= repeats;
            repeatRemainders(task, repeats);
            if (task.consumed != null) {
                if (task.lastAssemblerResult != null) {
                    for (var input : task.lastAssemblerResult.consumed()) {
                        task.consumed.add(input.getKey(), LongMath.saturatedMultiply(input.getLongValue(), repeats));
                    }
                } else {
                    addEncodedInputs(task, task.consumed, repeats);
                }
            }
        }
        for (var entry : this.passDelta.object2LongEntrySet()) {
            this.inventory.addTo(entry.getKey(), LongMath.saturatedMultiply(entry.getLongValue(), repeats));
        }
    }

    private void apply(Task task, Taken taken) {
        if (!taken.containers.isEmpty()) {
            var returned = this.remainderAllocations.computeIfAbsent(task.pattern, p -> new KeyCounter());
            taken.containers.forEachEntry(returned::add);
            if (task.lastReturns == null) task.lastReturns = new KeyCounter();
            task.lastReturns.clear();
            taken.containers.forEachEntry(task.lastReturns::add);
        } else if (task.lastReturns != null) task.lastReturns.clear();
        // Store only deviations from encoded inputs, aggregated per recipe/key. Fixed-input plans need no trace,
        // and repeated passes add a count rather than one allocation record per craft.
        if (!taken.exact && task.consumed == null) {
            task.consumed = new KeyCounter();
            addEncodedInputs(task, task.consumed, task.total - task.remaining);
            if (this.inputAllocations == null) this.inputAllocations = new Object2ObjectOpenHashMap<>();
            this.inputAllocations.put(task.pattern, task.consumed);
        }
        if (task.consumed != null) taken.keys.forEachEntry(task.consumed::add);
        taken.keys.forEachEntry((key, amount) -> this.passReserve.put(key,
            Math.max(this.passReserve.getLong(key), this.seeds.reserved(task.pattern, key))));
        taken.keys.forEachEntry(this::applyTaken);
        taken.containers.forEachEntry(this::applyContainer);
        for (int i = 0; i < task.outputs.size(); i++) {
            var output = task.outputs.get(i);
            this.inventory.addTo(output.what(), output.amount());
            this.passDelta.addTo(output.what(), output.amount());
        }
    }

    private void repeatRemainders(Task task, long repeats) {
        if (task.lastReturns == null || task.lastReturns.isEmpty()) return;
        var returned = this.remainderAllocations.get(task.pattern);
        task.lastReturns.forEach((key, count) -> returned.add(key, LongMath.saturatedMultiply(count, repeats)));
    }

    private void applyTaken(AEKey key, long amount) {
        this.inventory.addTo(key, -amount);
        this.passDelta.addTo(key, -amount);
        this.passTaken.addTo(key, amount);
    }

    private void applyContainer(AEKey key, long amount) {
        index(key);
        this.inventory.addTo(key, amount);
        this.passDelta.addTo(key, amount);
    }

    // What one craft takes from the current inventory, or null when it cannot run
    private @Nullable Taken takeOnce(Task task) {
        var pattern = task.pattern;
        if (pattern instanceof IAssemblerPattern && CraftingCalculation.isCalculationThread()) {
            return CraftingCalculation.runOnMainThread(() -> takeOnce(task));
        }
        var keys = this.takenKeys;
        var containers = this.takenContainers;
        keys.clear();
        containers.clear();
        if (pattern instanceof IAssemblerPattern assembler) {
            if (task.assemblerSimulation == null)
                task.assemblerSimulation = new CraftingAssemblerSimulation(assembler, this.level);
            var result = task.assemblerSimulation.select(view(task), false);
            if (result == null) return null;
            task.lastAssemblerResult = result;
            result.consumed().forEach(keys::addTo);
            result.returned().forEach(containers::addTo);
            boolean exact = true;
            for (int i = 0; i < result.inputs().size(); i++) {
                for (var stack : result.inputs().get(i)) {
                    if (!stack.what().equals(task.inputs[i].possibleInputs()[0].what())) exact = false;
                }
            }
            this.taken.exact = exact;
            return this.taken;
        }
        var inputs = task.inputs;
        boolean exact = true;
        for (IPatternDetails.IInput input : inputs) {
            if (input.possibleInputs().length == 0) continue;
            // Counted in templates, each one stack of its candidate, see CraftingCpuHelper#extractPatternInputs
            long need = input.getMultiplier();
            var first = input.possibleInputs()[0].what();
            var options = candidates(input);
            for (int candidateIndex = 0; candidateIndex < options.size(); candidateIndex++) {
                var candidate = options.get(candidateIndex);
                long per = candidate.amount();
                long available = this.inventory.getLong(candidate.what()) - keys.getLong(candidate.what())
                    - this.seeds.reserved(pattern, candidate.what());
                long templates = Math.min(Math.max(0, available) / per, need);
                if (templates <= 0) continue;
                if (candidate.what() != first || templates < need) {
                    exact = false;
                }
                keys.addTo(candidate.what(), templates * per);
                var container = input.getRemainingKey(candidate.what());
                if (container != null) {
                    containers.addTo(container, templates);
                }
                need -= templates;
                if (need <= 0) break;
            }
            if (need > 0) {
                return null;
            }
        }
        this.taken.exact = exact;
        return this.taken;
    }

    private ICraftingInventory view(Task task) {
        if (task.view == null) task.view = new ICraftingInventory() {
            public void insert(AEKey key, long amount, Actionable mode) {
                throw new UnsupportedOperationException();
            }

            public long extract(AEKey key, long amount, Actionable mode) {
                if (mode != Actionable.SIMULATE) throw new UnsupportedOperationException();
                return Math.clamp(inventory.getLong(key) - seeds.reserved(task.pattern, key), 0, amount);
            }

            public Iterable<AEKey> findFuzzyTemplates(AEKey key) {
                var group = keysByGroup.get(key.getPrimaryKey());
                return group == null ? List.of() : group;
            }
        };
        return task.view;
    }

    // What each planned craft that cannot run lacks for one craft, on the first possible stack of each short input
    private KeyCounter shortages() {
        var result = new KeyCounter();
        var reserved = this.takenKeys;
        for (int i = 0; i < this.tasks.size(); i++) {
            var task = this.tasks.get(i);
            if (task.remaining <= 0) continue;
            if (task.assemblerSimulation != null) {
                var choice = task.assemblerSimulation.select(view(task), true);
                if (choice != null) {
                    choice.consumed().forEach((key, amount) -> {
                        long shortage = amount - view(task).extract(key, amount, Actionable.SIMULATE);
                        if (shortage > 0) result.add(key, shortage);
                    });
                    continue;
                }
            }
            reserved.clear();
            for (var input : task.inputs) {
                var possible = input.possibleInputs();
                if (possible.length == 0) continue;
                long need = input.getMultiplier();
                var options = candidates(input);
                for (int candidateIndex = 0; candidateIndex < options.size(); candidateIndex++) {
                    var candidate = options.get(candidateIndex);
                    long available = this.inventory.getLong(candidate.what()) - reserved.getLong(candidate.what());
                    long templates = Math.min(Math.max(0, available) / candidate.amount(), need);
                    if (templates > 0) {
                        reserved.addTo(candidate.what(), templates * candidate.amount());
                        need -= templates;
                    }
                    if (need <= 0) break;
                }
                if (need > 0) {
                    result.add(possible[0].what(), LongMath.saturatedMultiply(need, possible[0].amount()));
                }
            }
        }
        return result;
    }

    // The possible stacks of an input in order, each followed by the other keys of its fuzzy group it accepts
    private ObjectArrayList<GenericStack> candidates(IPatternDetails.IInput input) {
        var result = this.candidates.get(input);
        if (result == null) {
            var list = new ObjectArrayList<GenericStack>(input.possibleInputs().length);
            for (var possible : input.possibleInputs()) {
                list.add(possible);
                var group = this.keysByGroup.get(possible.what().getPrimaryKey());
                if (group == null) continue;
                for (int keyIndex = 0; keyIndex < group.size(); keyIndex++) {
                    var key = group.get(keyIndex);
                    if (key != possible.what() && input.isValid(key, this.level)) {
                        list.add(new GenericStack(key, possible.amount()));
                    }
                }
            }
            result = list;
            this.candidates.put(input, result);
        }
        return result;
    }

    /**
     * What one craft takes. Exact when every input took its first possible stack alone, so the next craft takes the
     * same as long as that stack lasts; otherwise an input that moved on to a later stack may come back to an earlier
     * one as soon as a craft makes it.
     */
    private static final class Taken {
        final AEKey2LongMap keys;
        final AEKey2LongMap containers;
        boolean exact;

        Taken(AEKey2LongMap keys, AEKey2LongMap containers) {
            this.keys = keys;
            this.containers = containers;
        }
    }

    private static final class Task {
        final IPatternDetails pattern;
        final IPatternDetails.IInput[] inputs;
        final List<GenericStack> outputs;
        long remaining;
        final long total;
        KeyCounter consumed;
        KeyCounter lastReturns;
        CraftingAssemblerSimulation assemblerSimulation;
        CraftingAssemblerSimulation.Result lastAssemblerResult;
        CraftingAssemblerSimulation.Result previousAssemblerResult;
        ICraftingInventory view;

        Task(IPatternDetails pattern, long remaining) {
            this.pattern = pattern;
            this.inputs = pattern.getInputs();
            var outputs = pattern.getOutputs();
            this.outputs = outputs instanceof RandomAccess ? outputs : new ObjectArrayList<>(outputs);
            this.remaining = remaining;
            this.total = remaining;
        }
    }
}
