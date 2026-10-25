package ae2.crafting;

import ae2.api.config.Actionable;
import ae2.api.crafting.IAssemblerPattern;
import ae2.api.crafting.IPatternDetails;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.container.ContainerConstants;
import ae2.crafting.inv.ICraftingInventory;
import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.inventory.InventoryCrafting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.function.ToLongFunction;

/**
 * Keeps circulating inputs available until their cycle no longer needs to dispatch. Growing loops retain one
 * craft's seed; loops that only partially return an input retain enough to finish their remaining crafts.
 * A consumer outside the cycle may take only the surplus. Members may circulate their own seeds, including
 * outputs currently in a machine. Reservations disappear after the last consuming task is dispatched.
 * <p>
 * The bipartite pattern/material graph avoids a producer-by-consumer cross product. Iterative Tarjan takes
 * O(patterns + materials + slots) space; construction is linear apart from sorting competing claims per material.
 * Neither construction nor retained state depends on craft counts.
 */
public final class CraftingSeedReservations {
    private final Object2IntOpenHashMap<IPatternDetails> components = new Object2IntOpenHashMap<>();
    private final Object2ObjectOpenHashMap<AEKey, Seed> seeds = new Object2ObjectOpenHashMap<>();
    private final ToLongFunction<IPatternDetails> remaining;
    private final ObjectOpenHashSet<IPatternDetails> singlePatternCycles = new ObjectOpenHashSet<>();

    public CraftingSeedReservations(Collection<IPatternDetails> patterns, ToLongFunction<IPatternDetails> remaining) {
        this.remaining = remaining;
        this.components.defaultReturnValue(-1);
        var active = new ArrayList<IPatternDetails>();
        for (var pattern : patterns) if (remaining.applyAsLong(pattern) > 0) active.add(pattern);
        var edges = new ArrayList<IntArrayList>();
        for (int i = 0; i < active.size(); i++) edges.add(new IntArrayList());
        var materials = new Object2IntOpenHashMap<AEKey>();
        materials.defaultReturnValue(-1);
        var returns = new ArrayList<KeyCounter>(active.size());
        for (int i = 0; i < active.size(); i++) {
            var pattern = active.get(i);
            for (var output : pattern.getOutputs()) edges.get(material(materials, edges, output.what())).add(i);
            var returned = returnedInputs(pattern);
            returns.add(returned);
            for (var key : returned.keySet()) edges.get(material(materials, edges, key)).add(i);
            for (var input : pattern.getInputs()) {
                for (var possible : input.possibleInputs()) {
                    edges.get(i).add(material(materials, edges, possible.what()));
                }
            }
        }
        var component = components(edges);
        int[] members = new int[edges.size()];
        for (int i = 0; i < active.size(); i++) members[component[i]]++;
        for (int i = 0; i < active.size(); i++) {
            var pattern = active.get(i);
            var claims = new KeyCounter();
            for (var input : pattern.getInputs()) {
                for (var possible : input.possibleInputs()) {
                    if (component[i] == component[materials.getInt(possible.what())]) {
                        claims.add(possible.what(), LongMath.saturatedMultiply(possible.amount(), input.getMultiplier()));
                    }
                }
            }
            if (!claims.isEmpty()) {
                this.components.put(pattern, component[i]);
                if (members[component[i]] == 1) {
                    this.singlePatternCycles.add(pattern);
                    var returned = new KeyCounter();
                    for (var output : pattern.getOutputs()) returned.add(output.what(), output.amount());
                    returned.addAll(returns.get(i));
                    // A partially returned input shrinks on each craft. Keep enough to finish all pending crafts,
                    // including the last craft's seed, or outsiders can leave a balanced but unstartable tail.
                    for (var claim : claims) {
                        long loss = Math.max(0, claim.getLongValue() - returned.get(claim.getKey()));
                        claim.setValue(LongMath.saturatedAdd(claim.getLongValue(),
                            LongMath.saturatedMultiply(loss, remaining.applyAsLong(pattern) - 1)));
                    }
                }
                int id = component[i];
                claims.forEach((key, amount) -> this.seeds.computeIfAbsent(key, k -> new Seed(id))
                    .claims.add(new Claim(pattern, amount)));
            }
        }
        for (var seed : this.seeds.values()) seed.claims.sort(Comparator.comparingLong(Claim::amount).reversed());
    }

    private static KeyCounter returnedInputs(IPatternDetails pattern) {
        var result = new KeyCounter();
        if (pattern instanceof IAssemblerPattern assembler) {
            // Containers may be stacked or returned to an originally empty slot. Slot-local probes lose both.
            var returned = CraftingCalculation.defaultRemainders(pattern, () -> {
                var inputs = pattern.getInputs();
                var table = new KeyCounter[inputs.length];
                for (int slot = 0; slot < inputs.length; slot++) {
                    table[slot] = new KeyCounter();
                    var possible = inputs[slot].possibleInputs();
                    if (possible.length > 0) table[slot].add(possible[0].what(),
                        LongMath.saturatedMultiply(possible[0].amount(), inputs[slot].getMultiplier()));
                }
                var grid = new InventoryCrafting(ContainerConstants.EMPTY_CONTAINER, 3, 3);
                assembler.fillCraftingGrid(table, grid::setInventorySlotContents);
                return assembler.getRemainingItems(grid);
            });
            for (var stack : returned) {
                var generic = GenericStack.fromItemStack(stack);
                if (generic != null) result.add(generic.what(), generic.amount());
            }
        } else {
            for (var input : pattern.getInputs()) {
                for (var possible : input.possibleInputs()) {
                    var container = input.getRemainingKey(possible.what());
                    if (container != null) result.add(container, input.getMultiplier());
                }
            }
        }
        return result;
    }

    private static int material(Object2IntOpenHashMap<AEKey> materials, ArrayList<IntArrayList> edges, AEKey key) {
        int id = materials.getInt(key);
        if (id < 0) {
            id = edges.size();
            materials.put(key, id);
            edges.add(new IntArrayList());
        }
        return id;
    }

    private static int[] components(ArrayList<IntArrayList> edges) {
        int size = edges.size();
        int[] index = new int[size], low = new int[size], component = new int[size], next = new int[size];
        Arrays.fill(index, -1);
        Arrays.fill(component, -1);
        var path = new IntArrayList();
        var stack = new IntArrayList();
        int serial = 0, id = 0;
        for (int root = 0; root < size; root++) {
            if (index[root] >= 0) continue;
            path.add(root);
            stack.add(root);
            index[root] = low[root] = serial++;
            while (!path.isEmpty()) {
                int node = path.getInt(path.size() - 1);
                var inputs = edges.get(node);
                if (next[node] < inputs.size()) {
                    int child = inputs.getInt(next[node]++);
                    if (index[child] < 0) {
                        path.add(child);
                        stack.add(child);
                        index[child] = low[child] = serial++;
                    } else if (component[child] < 0) {
                        low[node] = Math.min(low[node], index[child]);
                    }
                } else {
                    path.removeInt(path.size() - 1);
                    if (!path.isEmpty()) {
                        int parent = path.getInt(path.size() - 1);
                        low[parent] = Math.min(low[parent], low[node]);
                    }
                    if (low[node] == index[node]) {
                        int member;
                        do {
                            member = stack.removeInt(stack.size() - 1);
                            component[member] = id;
                        } while (member != node);
                        id++;
                    }
                }
            }
        }
        return component;
    }

    public long reserved(IPatternDetails consumer, AEKey key) {
        var seed = this.seeds.get(key);
        if (seed == null || this.components.getInt(consumer) == seed.component) return 0;
        while (seed.first < seed.claims.size()) {
            var claim = seed.claims.get(seed.first);
            if (this.remaining.applyAsLong(claim.pattern) > 0) return claim.amount;
            seed.first++;
        }
        return 0;
    }

    boolean hasCycles() {
        return !this.seeds.isEmpty();
    }

    boolean isSinglePatternCycle(IPatternDetails pattern) {
        return this.singlePatternCycles.contains(pattern);
    }

    public ICraftingInventory protect(IPatternDetails consumer, ICraftingInventory inventory) {
        if (this.seeds.isEmpty()) return inventory;
        return new ICraftingInventory() {
            @Override
            public void insert(AEKey what, long amount, Actionable mode) {
                inventory.insert(what, amount, mode);
            }

            @Override
            public long extract(AEKey what, long amount, Actionable mode) {
                long reserve = reserved(consumer, what);
                if (reserve > 0) {
                    amount = Math.clamp(inventory.extract(what, Long.MAX_VALUE, Actionable.SIMULATE) - reserve, 0, amount);
                }
                return inventory.extract(what, amount, mode);
            }

            @Override
            public Iterable<AEKey> findFuzzyTemplates(AEKey input) {
                return inventory.findFuzzyTemplates(input);
            }
        };
    }

    private record Claim(IPatternDetails pattern, long amount) {
    }

    private static final class Seed {
        final int component;
        final ArrayList<Claim> claims = new ArrayList<>();
        int first;

        Seed(int component) {
            this.component = component;
        }
    }
}
