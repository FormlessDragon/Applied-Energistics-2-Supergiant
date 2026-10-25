package ae2.crafting;

import ae2.api.config.Actionable;
import ae2.api.crafting.IAssemblerPattern;
import ae2.api.crafting.IPatternDetails;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.container.ContainerConstants;
import ae2.crafting.execution.CraftingCpuHelper;
import ae2.crafting.inv.ICraftingInventory;
import ae2.crafting.inv.NetworkCraftingSimulationState;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static ae2.crafting.CraftingRemainderFixtures.Fixture;
import static ae2.crafting.CraftingRemainderFixtures.Slot;
import static ae2.crafting.CraftingRemainderFixtures.at;
import static ae2.crafting.CraftingRemainderFixtures.damage;
import static ae2.crafting.CraftingRemainderFixtures.exact;
import static ae2.crafting.CraftingRemainderFixtures.item;
import static ae2.crafting.CraftingRemainderFixtures.remaining;
import static ae2.crafting.CraftingRemainderFixtures.tool;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Allocation contracts, search completeness, and extraction rollback, independently of planner heuristics.
 */
class CraftingAssemblerAllocationBoundaryTest extends CraftingRecipeTestSupport {
    private static CraftingAssemblerSimulation.Result selected(CraftingAssemblerSimulation simulation,
                                                               ICraftingInventory inventory, boolean allowMissing) {
        var result = simulation.select(inventory, allowMissing);
        assertNotNull(result);
        return result;
    }

    @Test
    void recipeEvaluationIsSharedWithinOneCalculationButNotWithLaterCalculationsOrExecution() {
        var f = new Fixture();
        var returning = new AtomicBoolean(true);
        var rule = f.rule("calculation_reuse", item(Items.CARROT), false, new Slot[]{tool()},
            grid -> returning.get() ? at(grid, 0, damage(0)) : remaining(grid));
        f.stock(damage(0), 1);
        var calculation = f.net.calculation(rule.output.what(), 1);
        materials(calculation.assemblerSimulation(rule.pattern).defaultRemainders(), damage(0), 1);
        rule.callbacks.set(0);
        var first = selected(calculation.assemblerSimulation(rule.pattern),
            new NetworkCraftingSimulationState(f.net.stock), false);
        assertFalse(calculation.assemblerSimulation(rule.pattern).hasFixedResultWithoutRemainders());
        assertEquals(0, rule.callbacks.get(), "selection and replay validation should reuse the encoded evaluation");
        returning.set(false);
        var later = f.net.calculation(rule.output.what(), 1);
        assertTrue(later.assemblerSimulation(rule.pattern).hasFixedResultWithoutRemainders());
        assertTrue(rule.callbacks.get() > 0, "a new calculation must evaluate the recipe again");
        materials(first.returned(), damage(0), 1);
        rule.callbacks.set(0);
        var returned = new KeyCounter();
        assertNotNull(CraftingCpuHelper.extractPatternInputs(rule.pattern,
            new NetworkCraftingSimulationState(f.net.stock), CraftingTestNetwork.world(),
            new KeyCounter(), returned, 1));
        assertTrue(returned.isEmpty());
        assertTrue(rule.callbacks.get() > 0, "actual execution must evaluate current recipe behavior independently");
    }

    @Test
    void scratchReuseResetsDestructiveAdaptersAndKeepsEarlierResultsIndependent() {
        var f = new Fixture();
        var rule = f.rule("destructive_adapter", item(Items.CARROT), true, new Slot[]{tool()},
            grid -> at(grid, 0, AEItemKey.of(grid.getStackInSlot(0))));
        var adapter = (IAssemblerPattern) Proxy.newProxyInstance(IAssemblerPattern.class.getClassLoader(),
            new Class<?>[]{IAssemblerPattern.class}, (proxy, method, args) -> {
                if (method.getName().equals("fillCraftingGrid")) {
                    var table = (KeyCounter[]) args[0];
                    assertEquals(1, CraftingTestNetwork.total(table[0]), "previous adapter consumption leaked into this grid");
                    var key = table[0].iterator().next().getKey();
                    ((IAssemblerPattern.CraftingGridAccessor) args[1]).set(0, ((AEItemKey) key).toStack());
                    table[0].remove(key, 1);
                    return null;
                }
                if (method.getName().equals("assemble")) {
                    assertTrue(((InventoryCrafting) args[0]).getStackInSlot(8).isEmpty(), "an old empty-slot write survived");
                }
                var result = method.invoke(rule.pattern, args);
                if (method.getName().equals("getRemainingItems")) {
                    ((InventoryCrafting) args[0]).setInventorySlotContents(8, item(Items.STICK).toStack());
                }
                return result;
            });
        f.stock(damage(0), 1);
        f.stock(damage(7), 1);
        var inventory = new NetworkCraftingSimulationState(f.net.stock);
        var simulation = new CraftingAssemblerSimulation(adapter, CraftingTestNetwork.world());
        var first = selected(simulation, inventory, false);
        materials(first.consumed(), damage(0), 1);
        inventory.extract(damage(0), 1, Actionable.MODULATE);
        inventory.insert(damage(8), 1, Actionable.MODULATE);
        materials(selected(simulation, inventory, false).returned(), damage(7), 1);
        materials(first.consumed(), damage(0), 1);
        materials(first.returned(), damage(0), 1);
        materials(first.output(), rule.output.what(), 1, damage(0), 1);
    }

    @Test
    void repeatedSelectionTracksStockCountsNewVariantsAndMissingMode() {
        var f = new Fixture();
        var rule = f.jointRule("cached_pair", item(Items.CARROT), new Slot[]{tool(), tool()},
            grid -> grid.getStackInSlot(0).getItemDamage() == grid.getStackInSlot(1).getItemDamage(), grid -> {
                var returned = remaining(grid);
                returned.set(0, grid.getStackInSlot(0).copy());
                returned.set(1, grid.getStackInSlot(1).copy());
                return returned;
            });
        f.stock(damage(0), 2);
        var inventory = new NetworkCraftingSimulationState(f.net.stock);
        var simulation = new CraftingAssemblerSimulation(rule.pattern, CraftingTestNetwork.world());
        materials(selected(simulation, inventory, false).consumed(), damage(0), 2);
        rule.callbacks.set(0);
        for (int repeat = 0; repeat < 8; repeat++) {
            materials(selected(simulation, inventory, false).consumed(), damage(0), 2);
        }
        assertEquals(0, rule.callbacks.get(), "an unchanged grid choice must not repeat recipe callbacks");
        inventory.extract(damage(0), 1, Actionable.MODULATE);
        assertNull(simulation.select(inventory, false), "the same variants with less stock cannot fill both slots");
        materials(selected(simulation, inventory, true).consumed(), damage(0), 2);
        assertNull(simulation.select(inventory, false), "missing-mode choices cannot supply real stock");
        inventory.insert(damage(7), 2, Actionable.MODULATE);
        materials(selected(simulation, inventory, false).consumed(), damage(7), 2);
    }

    @Test
    void fullyStockedMissingModeSharesItsExhaustiveChoiceWithRealStockMode() {
        var f = new Fixture();
        var rule = f.jointRule("cached_stocked_choices", item(Items.CARROT), new Slot[]{tool(), tool()},
            grid -> true, grid -> {
                var returned = remaining(grid);
                returned.set(0, grid.getStackInSlot(0).copy());
                returned.set(1, grid.getStackInSlot(1).copy());
                return returned;
            });
        // More grids than the per-grid result cache holds, so a second search would repeat callbacks.
        for (int variant = 0; variant < 17; variant++) f.stock(damage(variant), 2);
        var inventory = new NetworkCraftingSimulationState(f.net.stock);
        var simulation = new CraftingAssemblerSimulation(rule.pattern, CraftingTestNetwork.world());
        materials(selected(simulation, inventory, true).consumed(), damage(0), 2);
        rule.callbacks.set(0);
        materials(selected(simulation, inventory, false).consumed(), damage(0), 2);
        assertEquals(0, rule.callbacks.get(), "the same fully stocked choice must not repeat an exhaustive search");
    }

    @Test
    void missingModeTracksEncodedStockEvenWhenFuzzyEnumerationOmitsIt() {
        var f = new Fixture();
        var rule = f.jointRule("cached_encoded_stock", item(Items.CARROT), new Slot[]{tool(), tool()},
            grid -> true, grid -> {
                var returned = remaining(grid);
                for (int slot = 0; slot < 2; slot++) {
                    if (grid.getStackInSlot(slot).getItemDamage() == 0)
                        returned.set(slot, grid.getStackInSlot(slot).copy());
                }
                return returned;
            });
        f.stock(damage(0), 2);
        f.stock(damage(7), 2);
        var inventory = new NetworkCraftingSimulationState(f.net.stock);
        var partialEnumeration = new ICraftingInventory() {
            public void insert(AEKey key, long amount, Actionable mode) {
                inventory.insert(key, amount, mode);
            }

            public long extract(AEKey key, long amount, Actionable mode) {
                return inventory.extract(key, amount, mode);
            }

            public Iterable<AEKey> findFuzzyTemplates(AEKey key) {
                return List.of(damage(7));
            }
        };
        var simulation = new CraftingAssemblerSimulation(rule.pattern, CraftingTestNetwork.world());
        materials(selected(simulation, partialEnumeration, true).consumed(), damage(0), 2);
        inventory.extract(damage(0), 2, Actionable.MODULATE);
        materials(selected(simulation, partialEnumeration, true).consumed(), damage(7), 2);
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 64})
    void aValidJointCombinationAtTheEndOfTheCandidateSetIsStillUsable(int alternatives) throws Exception {
        var f = new Fixture();
        var rule = f.jointRule("late_joint_combination", item(Items.CARROT), new Slot[]{tool(), tool()}, grid -> {
            int a = grid.getStackInSlot(0).getItemDamage();
            int b = grid.getStackInSlot(1).getItemDamage();
            // Every variant works alone against the encoded 0,0 grid. With the actual inventory only N,N works.
            return a == 0 || b == 0 || a == alternatives && b == alternatives;
        }, grid -> {
            var out = remaining(grid);
            out.set(0, grid.getStackInSlot(0).copy());
            out.set(1, grid.getStackInSlot(1).copy());
            return out;
        });
        for (int state = 1; state <= alternatives; state++) f.stock(damage(state), 2);
        assertTrue(f.reachable(f.net.stock, rule.output.what(), 1));
        var plan = f.net.plan(rule.output.what(), 1);
        assertAll(
            () -> complete(f.net, plan, rule.output.what(), 1),
            () -> materials(plan.usedItems(), damage(alternatives), 2),
            () -> f.assertExecutable(plan),
            () -> f.assertTree(plan),
            () -> assertNotNull(CraftingCpuHelper.extractPatternInputs(rule.pattern,
                new NetworkCraftingSimulationState(f.net.stock), CraftingTestNetwork.world(),
                new KeyCounter(), new KeyCounter(), 1), "CPU must find the independently verified matching pair"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void thirdPartyCondensedInputsMayUseDifferentVariantsWithinTheirMultiplier(boolean mixed) {
        var f = new Fixture();
        var rule = f.rule("condensed_adapter", item(Items.CARROT), true, new Slot[]{tool(), tool()}, grid -> {
            var out = remaining(grid);
            out.set(0, grid.getStackInSlot(0).copy());
            out.set(1, grid.getStackInSlot(1).copy());
            return out;
        });
        var input = new IPatternDetails.IInput() {
            public GenericStack[] possibleInputs() {
                return new GenericStack[]{new GenericStack(damage(0), 1)};
            }

            public long getMultiplier() {
                return 2;
            }

            public boolean isValid(AEKey key, World world) {
                return key instanceof AEItemKey item && item.getItem() == Items.WOODEN_PICKAXE;
            }

            public AEKey getRemainingKey(AEKey key) {
                return key;
            }
        };
        var adapter = (IAssemblerPattern) Proxy.newProxyInstance(IAssemblerPattern.class.getClassLoader(),
            new Class<?>[]{IAssemblerPattern.class}, (proxy, method, args) -> {
                if (method.getName().equals("getInputs")) return new IPatternDetails.IInput[]{input};
                if (method.getName().equals("fillCraftingGrid")) {
                    var table = (KeyCounter[]) args[0];
                    var grid = (IAssemblerPattern.CraftingGridAccessor) args[1];
                    for (int slot = 0; slot < 2; slot++) {
                        AEKey selected = null;
                        for (var entry : table[0])
                            if (entry.getLongValue() > 0) {
                                selected = entry.getKey();
                                break;
                            }
                        assertNotNull(selected, "adapter needs two physical items");
                        grid.set(slot, ((AEItemKey) selected).toStack());
                        table[0].remove(selected, 1);
                    }
                    return null;
                }
                return method.invoke(rule.pattern, args);
            });
        f.stock(damage(1), mixed ? 1 : 2);
        if (mixed) f.stock(damage(2), 1);
        assertTrue(f.reachable(f.net.stock, rule.output.what(), 1));
        var outputs = new KeyCounter();
        var returns = new KeyCounter();
        var table = CraftingCpuHelper.extractPatternInputs(adapter, new NetworkCraftingSimulationState(f.net.stock),
            CraftingTestNetwork.world(), outputs, returns, 1);
        assertNotNull(table, "IInput multiplier counts templates, not a demand for identical variants");
        var grid = new InventoryCrafting(ContainerConstants.EMPTY_CONTAINER, 3, 3);
        adapter.fillCraftingGrid(table, grid::setInventorySlotContents);
        assertTrue(rule.recipe.matches(grid, CraftingTestNetwork.world()));
        materials(returns, damage(1), mixed ? 1 : 2, damage(2), mixed ? 1 : 0);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void aPartialExtractionRestoresEarlierSlotsAndDoesNotPublishReturns(int failingSlot) {
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var bowl = item(Items.BOWL);
        var rule = f.rule("partial_extract", item(Items.CARROT), false,
            new Slot[]{exact(apple), exact(damage(0)), exact(bowl)}, grid -> at(grid, 1, damage(0)));
        for (var key : new AEKey[]{apple, damage(0), bowl}) f.stock(key, 2);
        var inventory = new NetworkCraftingSimulationState(f.net.stock);
        var faulty = new ICraftingInventory() {
            int slot;

            public void insert(AEKey key, long amount, Actionable mode) {
                inventory.insert(key, amount, mode);
            }

            public long extract(AEKey key, long amount, Actionable mode) {
                return inventory.extract(key, mode == Actionable.MODULATE && slot++ == failingSlot ? amount - 1 : amount, mode);
            }

            public Iterable<AEKey> findFuzzyTemplates(AEKey key) {
                return inventory.findFuzzyTemplates(key);
            }
        };
        var outputs = new KeyCounter();
        var returns = new KeyCounter();
        outputs.add(item(Items.STICK), 7);
        returns.add(item(Items.STICK), 9);
        assertNull(CraftingCpuHelper.extractPatternInputs(rule.pattern, faulty, CraftingTestNetwork.world(), outputs, returns, 2));
        for (var key : new AEKey[]{apple, damage(0), bowl}) assertEquals(2, inventory.getAvailableAmount(key));
        materials(outputs, item(Items.STICK), 7);
        materials(returns, item(Items.STICK), 9);
    }
}
