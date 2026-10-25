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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Allocation contracts, search completeness, and extraction rollback, independently of planner heuristics.
 */
class CraftingAssemblerAllocationBoundaryTest extends CraftingRecipeTestSupport {
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
