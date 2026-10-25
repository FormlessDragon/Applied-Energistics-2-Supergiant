package ae2.crafting;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.container.ContainerConstants;
import ae2.crafting.pattern.AECraftingPattern;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

import static ae2.crafting.CraftingPlanningTestSupport.amounts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Small whole-grid oracle. It never uses condensed IInput, planner remainders, reservations or CPU replay.
 */
final class CraftingRemainderFixtures {
    private static final AtomicInteger IDS = new AtomicInteger();

    static AEItemKey item(Item item) {
        return AEItemKey.of(item);
    }

    static AEItemKey damage(int value) {
        return AEItemKey.of(new ItemStack(Items.WOODEN_PICKAXE, 1, value));
    }

    static AEItemKey charge(int value) {
        var stack = new ItemStack(Items.PAPER);
        var tag = new NBTTagCompound();
        tag.setString("tool", "charge_cell");
        tag.setInteger("charge", value);
        stack.setTagCompound(tag);
        return AEItemKey.of(stack);
    }

    static Slot exact(AEItemKey key) {
        return new Slot(key, key::equals, new Item[]{key.getItem()});
    }

    static Slot variant(AEItemKey key, Predicate<AEItemKey> accepts, Item... types) {
        return new Slot(key, accepts, types);
    }

    static Slot tool() {
        return variant(damage(0), key -> key.getItem() == Items.WOODEN_PICKAXE, Items.WOODEN_PICKAXE);
    }

    static NonNullList<ItemStack> remaining(InventoryCrafting grid) {
        return NonNullList.withSize(grid.getSizeInventory(), ItemStack.EMPTY);
    }

    static NonNullList<ItemStack> at(InventoryCrafting grid, int slot, AEItemKey key) {
        var result = remaining(grid);
        if (key != null) result.set(slot, key.toStack());
        return result;
    }

    private static void adjust(Map<AEKey, Long> items, AEKey key, long delta) {
        long value = items.getOrDefault(key, 0L) + delta;
        if (value == 0) items.remove(key);
        else items.put(key, value);
    }

    record Slot(AEItemKey encoded, Predicate<AEItemKey> accepts, Item[] ingredientItems) {
    }

    static final class Rule {
        final AECraftingPattern pattern;
        final ShapedRecipes recipe;
        final Slot[] slots;
        final boolean substitutes;
        final GenericStack output;
        final AtomicInteger callbacks = new AtomicInteger();

        Rule(String name, AEItemKey output, int outputCount, boolean substitutes, Slot[] slots,
             Function<InventoryCrafting, NonNullList<ItemStack>> returns) {
            this(name, output, outputCount, substitutes, slots, grid -> true, returns);
        }

        Rule(String name, AEItemKey output, int outputCount, boolean substitutes, Slot[] slots,
             Predicate<InventoryCrafting> combination,
             Function<InventoryCrafting, NonNullList<ItemStack>> returns) {
            this.slots = slots;
            this.substitutes = substitutes;
            this.output = new GenericStack(output, outputCount);
            var ingredients = NonNullList.withSize(9, Ingredient.EMPTY);
            var encodedSlots = new ItemStack[9];
            Arrays.fill(encodedSlots, ItemStack.EMPTY);
            for (int i = 0; i < slots.length; i++) {
                if (slots[i] == null) continue;
                ingredients.set(i, Ingredient.fromItems(slots[i].ingredientItems()));
                encodedSlots[i] = slots[i].encoded().toStack();
            }
            recipe = new ShapedRecipes("", 3, 3, ingredients, output.toStack(outputCount)) {
                @Override
                public boolean matches(InventoryCrafting grid, World world) {
                    callbacks.incrementAndGet();
                    for (int i = 0; i < 9; i++) {
                        var slot = i < slots.length ? slots[i] : null;
                        var key = AEItemKey.of(grid.getStackInSlot(i));
                        if (slot == null ? key != null : key == null || !slot.accepts().test(key)) return false;
                    }
                    return combination.test(grid);
                }

                @Override
                public NonNullList<ItemStack> getRemainingItems(InventoryCrafting grid) {
                    callbacks.incrementAndGet();
                    return returns.apply(grid);
                }
            };
            recipe.setRegistryName("ae2_remainder_test", name + "_" + IDS.getAndIncrement());
            ForgeRegistries.RECIPES.register(recipe);
            var encoded = new ItemStack(Items.PAPER);
            AECraftingPattern.encode(encoded, recipe, encodedSlots, output.toStack(outputCount), substitutes, false);
            pattern = new AECraftingPattern(AEItemKey.of(encoded), CraftingTestNetwork.world());
        }
    }

    static final class Fixture {
        final CraftingTestNetwork net = new CraftingTestNetwork();
        final List<Rule> rules = new ArrayList<>();

        Rule rule(String name, AEItemKey output, boolean substitutes, Slot[] slots,
                  Function<InventoryCrafting, NonNullList<ItemStack>> returns) {
            return rule(name, output, 1, substitutes, slots, returns);
        }

        Rule rule(String name, AEItemKey output, int count, boolean substitutes, Slot[] slots,
                  Function<InventoryCrafting, NonNullList<ItemStack>> returns) {
            var rule = new Rule(name, output, count, substitutes, slots, returns);
            rules.add(rule);
            net.addPattern(rule.pattern, -rules.size());
            return rule;
        }

        Rule jointRule(String name, AEItemKey output, Slot[] slots, Predicate<InventoryCrafting> combination,
                       Function<InventoryCrafting, NonNullList<ItemStack>> returns) {
            var rule = new Rule(name, output, 1, true, slots, combination, returns);
            rules.add(rule);
            net.addPattern(rule.pattern, -rules.size());
            return rule;
        }

        void stock(AEKey key, long count) {
            net.stock.set(key, count);
        }

        boolean reachable(KeyCounter initial, AEKey target, long requested) {
            return search(amounts(initial), target, requested, null, null);
        }

        /**
         * Exhausts finite state graphs; the state budget fails explicitly instead of guessing unreachability.
         */
        boolean reachableExhaustively(KeyCounter initial, AEKey target, long requested) {
            return search(amounts(initial), target, requested, null, null, true);
        }

        void assertExecutable(ICraftingPlan plan) {
            var supplied = new KeyCounter();
            supplied.addAll(plan.usedItems());
            supplied.addAll(plan.missingItems());
            supplied.addAll(plan.emittedItems());
            assertTrue(search(amounts(supplied), plan.finalOutput().what(), plan.finalOutput().amount(),
                plan, null), "no whole-grid execution uses these planned tasks and supplied materials");
        }

        void assertTree(ICraftingPlan plan) throws Exception {
            var tree = CraftingTreeCyclesTest.tree(plan);
            assertNotNull(tree);
            assertEquals(plan.finalOutput(), tree.output());
            CraftingTreeAudit.structure(tree, null, new ArrayList<>(), ((CraftingPlan) plan).remainderAllocations());
            assertEquals(amounts(plan.missingItems()), amounts(CraftingTreeAudit.missing(tree)));
            var displayed = new HashMap<IPatternDetails, Map<AEKey, Long>>();
            CraftingTreeAudit.walk(tree, n -> {
            }, p -> {
                if (p.pattern() == null) return;
                var inputs = displayed.computeIfAbsent(p.pattern(), k -> new HashMap<>());
                for (var input : p.inputs()) inputs.merge(input.output().what(), input.output().amount(), Long::sum);
            });
            var supplied = new KeyCounter();
            supplied.addAll(plan.usedItems());
            supplied.addAll(plan.missingItems());
            supplied.addAll(plan.emittedItems());
            assertTrue(search(amounts(supplied), plan.finalOutput().what(), plan.finalOutput().amount(),
                plan, displayed), "tree's per-recipe material allocation cannot execute as complete crafting grids");
            CraftingTreeAudit.roundTrip(tree);
        }

        private boolean search(Map<AEKey, Long> stock, AEKey target, long requested, ICraftingPlan plan,
                               Map<IPatternDetails, Map<AEKey, Long>> allocation) {
            return search(stock, target, requested, plan, allocation, false);
        }

        private boolean search(Map<AEKey, Long> stock, AEKey target, long requested, ICraftingPlan plan,
                               Map<IPatternDetails, Map<AEKey, Long>> allocation, boolean exhaustive) {
            var pending = new ArrayList<Long>();
            for (var rule : rules) pending.add(plan == null ? -1L : plan.patternTimes().getLong(rule.pattern));
            var queue = new ArrayDeque<State>();
            var seen = new HashSet<Signature>();
            queue.add(new State(Map.copyOf(stock), List.copyOf(pending), allocation, 0));
            while (!queue.isEmpty()) {
                var state = queue.removeFirst();
                if (!seen.add(new Signature(state.inventory, state.pending, state.allocation))) continue;
                assertTrue(seen.size() <= 20_000, "oracle fixture exceeded its small-state budget");
                if (state.inventory.getOrDefault(target, 0L) >= requested
                    && (plan == null || state.pending.stream().allMatch(n -> n == 0))
                    && (state.allocation == null || state.allocation.values().stream().allMatch(Map::isEmpty)))
                    return true;
                if (!exhaustive && state.depth >= 14) continue;
                for (int ruleIndex = 0; ruleIndex < rules.size(); ruleIndex++) {
                    if (state.pending.get(ruleIndex) == 0) continue;
                    var rule = rules.get(ruleIndex);
                    var grid = new InventoryCrafting(ContainerConstants.EMPTY_CONTAINER, 3, 3);
                    expand(queue, state, ruleIndex, rule, 0, grid, new HashMap<>(state.inventory), new HashMap<>());
                }
            }
            return false;
        }

        private void expand(ArrayDeque<State> queue, State state, int index, Rule rule, int slotIndex,
                            InventoryCrafting grid, Map<AEKey, Long> left, Map<AEKey, Long> taken) {
            if (slotIndex == rule.slots.length) {
                if (!rule.recipe.matches(grid, CraftingTestNetwork.world())) return;
                Map<IPatternDetails, Map<AEKey, Long>> allocation = state.allocation;
                if (allocation != null) {
                    var counts = new HashMap<>(allocation.getOrDefault(rule.pattern, Map.of()));
                    for (var entry : taken.entrySet()) {
                        if (counts.getOrDefault(entry.getKey(), 0L) < entry.getValue()) return;
                        adjust(counts, entry.getKey(), -entry.getValue());
                    }
                    allocation = new HashMap<>(allocation);
                    allocation.put(rule.pattern, Map.copyOf(counts));
                }
                var next = new HashMap<>(left);
                for (var returned : rule.recipe.getRemainingItems(grid)) {
                    if (!returned.isEmpty()) adjust(next, AEItemKey.of(returned), returned.getCount());
                }
                var actualOutput = rule.recipe.getCraftingResult(grid);
                adjust(next, AEItemKey.of(actualOutput), actualOutput.getCount());
                var pending = new ArrayList<>(state.pending);
                if (pending.get(index) > 0) pending.set(index, pending.get(index) - 1);
                queue.addLast(new State(Map.copyOf(next), List.copyOf(pending), allocation, state.depth + 1));
                return;
            }
            var slot = rule.slots[slotIndex];
            if (slot == null) {
                expand(queue, state, index, rule, slotIndex + 1, grid, left, taken);
                return;
            }
            for (var entry : new ArrayList<>(left.entrySet())) {
                if (entry.getValue() <= 0 || !(entry.getKey() instanceof AEItemKey key)
                    || !slot.accepts().test(key) || !rule.substitutes && !slot.encoded().equals(key)) continue;
                grid.setInventorySlotContents(slotIndex, key.toStack());
                adjust(left, key, -1);
                adjust(taken, key, 1);
                expand(queue, state, index, rule, slotIndex + 1, grid, left, taken);
                adjust(taken, key, -1);
                adjust(left, key, 1);
                grid.setInventorySlotContents(slotIndex, ItemStack.EMPTY);
            }
        }
    }

    private record State(Map<AEKey, Long> inventory, List<Long> pending,
                         Map<IPatternDetails, Map<AEKey, Long>> allocation, int depth) {
    }

    private record Signature(Map<AEKey, Long> inventory, List<Long> pending,
                             Map<IPatternDetails, Map<AEKey, Long>> allocation) {
    }
}
