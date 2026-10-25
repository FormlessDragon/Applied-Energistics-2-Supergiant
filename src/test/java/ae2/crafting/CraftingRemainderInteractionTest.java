package ae2.crafting;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.container.ContainerConstants;
import ae2.crafting.execution.CraftingCpuHelper;
import ae2.crafting.inv.NetworkCraftingSimulationState;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

import static ae2.crafting.CraftingRemainderFixtures.Fixture;
import static ae2.crafting.CraftingRemainderFixtures.Rule;
import static ae2.crafting.CraftingRemainderFixtures.Slot;
import static ae2.crafting.CraftingRemainderFixtures.at;
import static ae2.crafting.CraftingRemainderFixtures.charge;
import static ae2.crafting.CraftingRemainderFixtures.damage;
import static ae2.crafting.CraftingRemainderFixtures.exact;
import static ae2.crafting.CraftingRemainderFixtures.item;
import static ae2.crafting.CraftingRemainderFixtures.remaining;
import static ae2.crafting.CraftingRemainderFixtures.tool;
import static ae2.crafting.CraftingRemainderFixtures.variant;
import static ae2.crafting.CraftingTestNetwork.crafts;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Interactions that single-slot wear and single-pattern cycling do not exercise.
 */
class CraftingRemainderInteractionTest extends CraftingRecipeTestSupport {
    private Rule paired(Fixture f) {
        return f.jointRule("paired_substitutions", item(Items.CARROT), new Slot[]{tool(), tool()},
            grid -> grid.getStackInSlot(0).getItemDamage() == grid.getStackInSlot(1).getItemDamage(), grid -> {
                var out = remaining(grid);
                out.set(0, grid.getStackInSlot(0).copy());
                out.set(1, grid.getStackInSlot(1).copy());
                return out;
            });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void twoSlotsMayNeedToSubstituteTogetherToFormAValidGrid(int cache) throws Exception {
        memo(cache);
        var f = new Fixture();
        var rule = paired(f);
        f.stock(damage(7), 2);
        assertTrue(f.reachable(f.net.stock, rule.output.what(), 2));
        var plan = f.net.plan(rule.output.what(), 2);
        assertAll(
            () -> complete(f.net, plan, rule.output.what(), 2),
            () -> materials(plan.usedItems(), damage(7), 2),
            () -> f.assertExecutable(plan),
            () -> f.assertTree(plan));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cpuAcceptsJointSubstitutionsAndRejectsMismatchedPairs(boolean mismatched) {
        var f = new Fixture();
        var rule = paired(f);
        f.stock(damage(7), mismatched ? 1 : 2);
        if (mismatched) f.stock(damage(8), 1);
        assertEquals(!mismatched, f.reachable(f.net.stock, rule.output.what(), 1));
        var inventory = new NetworkCraftingSimulationState(f.net.stock);
        var outputs = new KeyCounter();
        var returned = new KeyCounter();
        var table = CraftingCpuHelper.extractPatternInputs(rule.pattern, inventory, CraftingTestNetwork.world(),
            outputs, returned, 1);
        if (mismatched) {
            assertNull(table);
            assertEquals(1, inventory.getAvailableAmount(damage(7)));
            assertEquals(1, inventory.getAvailableAmount(damage(8)));
            materials(outputs);
            materials(returned);
        } else {
            assertNotNull(table, "the supplied complete pair matches the real recipe");
            var grid = new InventoryCrafting(ContainerConstants.EMPTY_CONTAINER, 3, 3);
            rule.pattern.fillCraftingGrid(table, grid::setInventorySlotContents);
            assertTrue(rule.recipe.matches(grid, CraftingTestNetwork.world()));
            materials(returned, damage(7), 2);
        }
    }

    @ParameterizedTest
    @EnumSource(ExtraDemand.class)
    void remainderDemandCanRequireMoreCraftsThanThePrimaryOutputDemand(ExtraDemand kind) throws Exception {
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var carrot = item(Items.CARROT);
        var bowl = item(Items.BOWL);
        var gold = item(Items.GOLD_INGOT);
        boolean stacked = kind == ExtraDemand.STACKED_RETURN;
        var producer = f.rule("extra_byproduct_demand", carrot, false,
            stacked ? new Slot[]{exact(apple), exact(bowl)} : new Slot[]{exact(apple)}, grid -> {
                var out = at(grid, stacked ? 1 : 8, bowl);
                if (stacked) out.get(1).setCount(2);
                return out;
            });
        f.rule("more_remainders_than_primary", gold, false,
            stacked ? new Slot[]{exact(carrot), exact(bowl), exact(bowl), exact(bowl)}
                : new Slot[]{exact(carrot), exact(bowl), exact(bowl)}, CraftingRemainderFixtures::remaining);
        f.stock(apple, 2);
        if (stacked) f.stock(bowl, 1);
        assertTrue(f.reachable(f.net.stock, gold, 1), "two producer crafts are an independent witness");
        var plan = f.net.plan(gold, 1);
        assertAll(
            () -> complete(f.net, plan, gold, 1),
            () -> assertEquals(2, crafts(plan, producer.pattern)),
            () -> materials(plan.usedItems(), apple, 2, bowl, stacked ? 1 : 0),
            () -> f.assertExecutable(plan),
            () -> f.assertTree(plan));
    }

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void theSameRegisteredRecipeCanChangeItsRemainderBetweenCalculations(boolean initiallyReturns, boolean refresh) throws Exception {
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var carrot = item(Items.CARROT);
        var enabled = new AtomicBoolean(initiallyReturns);
        var rule = f.rule("changing_remainder_rule", carrot, false, new Slot[]{exact(apple), tool()},
            grid -> at(grid, 1, enabled.get() ? damage(0) : null));
        f.stock(apple, 2);
        f.stock(damage(0), 2);
        var first = f.net.plan(carrot, 2);
        complete(f.net, first, carrot, 2);
        materials(first.usedItems(), apple, 2, damage(0), initiallyReturns ? 1 : 2);
        var frozen = CraftingTreeAudit.wireData(CraftingTreeCyclesTest.tree(first));
        enabled.set(!initiallyReturns);
        if (refresh) {
            f.net.removePattern(rule.pattern);
            f.net.addPattern(rule.pattern, 0);
        }
        var next = f.net.plan(carrot, 2);
        assertAll(
            () -> complete(f.net, next, carrot, 2),
            () -> materials(next.usedItems(), apple, 2, damage(0), initiallyReturns ? 2 : 1),
            () -> f.assertExecutable(next),
            () -> f.assertTree(next),
            () -> assertEquals(frozen, CraftingTreeAudit.wireData(CraftingTreeCyclesTest.tree(first))));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void damageOrderingDoesNotProveWhichToolsSurviveTheNextCraft(int cache) throws Exception {
        memo(cache);
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var carrot = item(Items.CARROT);
        f.rule("non_monotonic_survival", carrot, true, new Slot[]{exact(apple), tool(), tool()}, grid -> {
            var out = remaining(grid);
            for (int slot = 1; slot <= 2; slot++) {
                int state = grid.getStackInSlot(slot).getItemDamage();
                if (state > 0) out.set(slot, damage(state == 2 ? 1 : 2).toStack());
            }
            return out;
        });
        f.stock(apple, 2);
        f.stock(damage(0), 2); // These are consumed. The higher-damage tool can cycle indefinitely.
        f.stock(damage(2), 1);
        assertTrue(f.reachable(f.net.stock, carrot, 2));
        var plan = f.net.plan(carrot, 2);
        assertAll(
            () -> complete(f.net, plan, carrot, 2),
            () -> materials(plan.usedItems(), apple, 2, damage(0), 2, damage(2), 1),
            () -> f.assertExecutable(plan),
            () -> f.assertTree(plan));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void aToolCirculatingThroughDifferentPatternsDoesNotAllocatePerCraft(int cache) throws Exception {
        memo(cache);
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var potato = item(Items.POTATO);
        var carrot = item(Items.CARROT);
        var bread = item(Items.BREAD);
        var gold = item(Items.GOLD_INGOT);
        var left = f.rule("cross_pattern_left", carrot, true, new Slot[]{exact(apple), tool()},
            grid -> at(grid, 1, damage(1)));
        var right = f.rule("cross_pattern_right", bread, true, new Slot[]{exact(potato), tool()},
            grid -> at(grid, 1, damage(0)));
        var join = f.rule("cross_pattern_join", gold, false, new Slot[]{exact(carrot), exact(bread)},
            CraftingRemainderFixtures::remaining);
        f.stock(apple, 4096);
        f.stock(potato, 4096);
        f.stock(damage(0), 1);
        var small = f.net.plan(gold, 2);
        f.assertExecutable(small);
        f.assertTree(small);
        long smallAllocation = 0;
        for (int count : new int[]{128, 4096}) {
            long before = CraftingRealPatternPlanningTest.craftingAllocatedBytes();
            var plan = f.net.plan(gold, count);
            long allocated = CraftingRealPatternPlanningTest.craftingAllocatedBytes() - before;
            System.out.printf("cross-pattern-return memo=%d crafts=%d allocated=%.3fMiB%n", cache, count,
                allocated / 1048576.0);
            complete(f.net, plan, gold, count);
            materials(plan.usedItems(), apple, count, potato, count, damage(0), 1);
            assertEquals(count, crafts(plan, left.pattern));
            assertEquals(count, crafts(plan, right.pattern));
            assertEquals(count, crafts(plan, join.pattern));
            if (count == 128) smallAllocation = allocated;
            else assertTrue(allocated < smallAllocation * 4 + 2L * 1024 * 1024,
                "same graph and two tool states: allocation grew from " + smallAllocation + " to " + allocated);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 9})
    void aTransientStartupAndTwoDifferentToolPeriodsPreserveAllStateCounts(int initialDamage) throws Exception {
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var carrot = item(Items.CARROT);
        var first = variant(damage(initialDamage), key -> key.getItem() == Items.WOODEN_PICKAXE, Items.WOODEN_PICKAXE);
        var second = variant(charge(0), key -> key.getItem() == Items.PAPER && key.toStack().hasTagCompound(), Items.PAPER);
        var rule = f.rule("two_periods_and_prefix", carrot, true, new Slot[]{exact(apple), first, second}, grid -> {
            int state = grid.getStackInSlot(1).getItemDamage();
            var out = at(grid, 1, damage(state == 9 ? 0 : 1 - state));
            var cell = grid.getStackInSlot(2);
            int charge = cell.hasTagCompound() ? cell.getTagCompound().getInteger("charge") : 0;
            out.set(2, charge((charge + 1) % 3).toStack());
            return out;
        });
        f.stock(apple, 7);
        f.stock(damage(initialDamage), 1);
        f.stock(charge(0), 1);
        assertTrue(f.reachable(f.net.stock, carrot, 7));
        var plan = f.net.plan(carrot, 7);
        complete(f.net, plan, carrot, 7);
        materials(plan.usedItems(), apple, 7, damage(initialDamage), 1, charge(0), 1);
        f.assertExecutable(plan);
        f.assertTree(plan);
        assertEquals(7, crafts(plan, rule.pattern));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void twoConsumersOfTheSameRemainderDoNotTurnItIntoAdditionalStockLeaves(boolean seeded) throws Exception {
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var bowl = item(Items.BOWL);
        var carrot = item(Items.CARROT);
        var bread = item(Items.BREAD);
        var potato = item(Items.POTATO);
        var gold = item(Items.GOLD_INGOT);
        f.rule("shared_return_producer", carrot, false,
            seeded ? new Slot[]{exact(apple), exact(bowl)} : new Slot[]{exact(apple)}, grid -> {
                var out = at(grid, 8, bowl);
                out.get(8).setCount(seeded ? 3 : 2);
                return out;
            });
        f.rule("shared_return_left", bread, false, new Slot[]{exact(bowl)}, CraftingRemainderFixtures::remaining);
        f.rule("shared_return_right", potato, false, new Slot[]{exact(bowl)}, CraftingRemainderFixtures::remaining);
        f.rule("join_shared_returns", gold, false, new Slot[]{exact(carrot), exact(bread), exact(potato)},
            CraftingRemainderFixtures::remaining);
        f.stock(apple, 1);
        if (seeded) f.stock(bowl, 1);
        assertTrue(f.reachable(f.net.stock, gold, 1));
        var plan = f.net.plan(gold, 1);
        complete(f.net, plan, gold, 1);
        materials(plan.usedItems(), apple, 1, bowl, seeded ? 1 : 0);
        f.assertExecutable(plan);
        f.assertTree(plan);
        var declaredOutputs = new HashSet<AEKey>();
        plan.patternTimes().keySet().forEach(p -> p.getOutputs().forEach(o -> declaredOutputs.add(o.what())));
        var raw = new KeyCounter();
        CraftingTreeAudit.walk(CraftingTreeCyclesTest.tree(plan), node -> {
            if (node.inputs().isEmpty() && node.feedback() < 0 && !declaredOutputs.contains(node.output().what())) {
                raw.add(node.output().what(), node.output().amount() - node.missing());
            }
        }, proc -> {
        });
        assertEquals(amounts(plan.usedItems()), amounts(raw),
            "a remainder shared by sibling consumers is produced material, not additional physical stock");
    }

    enum ExtraDemand {EMPTY_SLOT, STACKED_RETURN}
}
