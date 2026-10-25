package ae2.crafting;

import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.KeyCounter;
import net.minecraft.init.Items;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;

import static ae2.crafting.CraftingRemainderFixtures.Fixture;
import static ae2.crafting.CraftingRemainderFixtures.Rule;
import static ae2.crafting.CraftingRemainderFixtures.Slot;
import static ae2.crafting.CraftingRemainderFixtures.at;
import static ae2.crafting.CraftingRemainderFixtures.damage;
import static ae2.crafting.CraftingRemainderFixtures.exact;
import static ae2.crafting.CraftingRemainderFixtures.item;
import static ae2.crafting.CraftingRemainderFixtures.tool;
import static ae2.crafting.CraftingTestNetwork.crafts;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingRemainderLifecycleTest extends CraftingRecipeTestSupport {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void replacementToolsAreAcquiredByObservedLifetimeInBulk(boolean craftable) {
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var carrot = item(Items.CARROT);
        var stick = item(Items.STICK);
        int requested = 300_003;
        var rule = f.rule("finite_lifetime", carrot, true, new Slot[]{exact(apple), tool()}, grid -> {
            int next = grid.getStackInSlot(1).getItemDamage() + 1;
            return at(grid, 1, next < 3 ? damage(next) : null);
        });
        Rule maker = null;
        if (craftable) {
            maker = f.rule("bulk_replacement", damage(0), false, new Slot[]{exact(stick), exact(stick)},
                CraftingRemainderFixtures::remaining);
            f.stock(stick, requested / 3 * 2);
        }
        f.stock(apple, requested);
        long before = CraftingRealPatternPlanningTest.craftingAllocatedBytes();
        var plan = f.net.plan(carrot, requested);
        long allocated = CraftingRealPatternPlanningTest.craftingAllocatedBytes() - before;
        assertTrue(allocated < 16L * 1024 * 1024, "replacement planning allocated per tool: " + allocated);
        if (craftable) {
            complete(f.net, plan, carrot, requested);
            assertEquals(requested / 3, crafts(plan, maker.pattern));
            materials(plan.usedItems(), apple, requested, stick, requested / 3 * 2);
            assertTrue(maker.callbacks.get() < 100, "replacement recipes must be batched");
        } else {
            materials(plan.missingItems(), damage(0), requested / 3);
            materials(plan.usedItems(), apple, requested);
        }
        assertEquals(requested, crafts(plan, rule.pattern));
        System.out.printf("replacement-tool craftable=%s crafts=%d allocated=%.3fMiB callbacks=%d%n",
            craftable, requested, allocated / 1048576.0, rule.callbacks.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 59, 257})
    void periodicToolStatesAreBatchedByCycleLength(int period) throws Exception {
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var carrot = item(Items.CARROT);
        var rule = f.rule("periodic_tool", carrot, true, new Slot[]{exact(apple), tool()},
            grid -> at(grid, 1, damage((grid.getStackInSlot(1).getItemDamage() + 1) % period)));
        int requested = 1_000_003;
        f.stock(apple, requested);
        f.stock(damage(0), 1);
        long before = CraftingRealPatternPlanningTest.craftingAllocatedBytes();
        var plan = f.net.plan(carrot, requested);
        long allocated = CraftingRealPatternPlanningTest.craftingAllocatedBytes() - before;
        assertTrue(allocated < (8L * 1024 * 1024 + period * 128L * 1024), "periodic planning allocated per craft: " + allocated);
        System.out.printf("periodic-tool period=%d crafts=%d allocated=%.3fMiB callbacks=%d%n",
            period, requested, allocated / 1048576.0, rule.callbacks.get());
        complete(f.net, plan, carrot, requested);
        materials(plan.usedItems(), apple, requested, damage(0), 1);
        assertEquals(requested, crafts(plan, rule.pattern));
        assertTrue(rule.callbacks.get() < period * 100 + 500,
            "a repeated state cycle must not execute callbacks per craft: " + rule.callbacks.get());
        var inputs = ((CraftingPlan) plan).inputAllocations().get(rule.pattern);
        assertNotNull(inputs);
        for (int state = 0; state < period; state++) {
            long count = requested / period + (state < requested % period ? 1 : 0);
            assertEquals(count, inputs.get(damage(state)));
            assertEquals(count, ((CraftingPlan) plan).remainderAllocations().get(rule.pattern).get(damage((state + 1) % period)));
        }
        var tree = CraftingTreeCyclesTest.tree(plan);
        CraftingTreeAudit.structure(tree, null, new ArrayList<>(), ((CraftingPlan) plan).remainderAllocations());
        CraftingTreeAudit.roundTrip(tree);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(booleans = {false, true})
    void treeDistinguishesReturnedMaterialsFromPhysicalStock(boolean changesState) throws Exception {
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var carrot = item(Items.CARROT);
        var rule = f.rule("tree_returns", carrot, true, new Slot[]{exact(apple), tool()},
            grid -> at(grid, 1, damage(changesState ? grid.getStackInSlot(1).getItemDamage() + 1 : 0)));
        f.stock(apple, 2);
        f.stock(damage(0), 1);
        var plan = f.net.plan(carrot, 2);
        complete(f.net, plan, carrot, 2);
        long calls = rule.callbacks.get();
        var tree = CraftingTreeCyclesTest.tree(plan);
        assertEquals(calls, rule.callbacks.get(), "display construction executed recipe callbacks");
        var raw = new KeyCounter();
        CraftingTreeAudit.walk(tree, n -> {
            if (n.inputs().isEmpty() && n.feedback() < 0) raw.add(n.output().what(), n.output().amount() - n.missing());
        }, p -> {
        });
        assertAll(
            () -> assertEquals(amounts(plan.usedItems()), amounts(raw),
                "returned intermediate states must not appear as extra physical inventory leaves"),
            () -> f.assertTree(plan));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(ints = {0, 10_000})
    void warmInventoryChangesDoNotReuseOldVariantAllocationsOrAlterAnOldTree(int cache) throws Exception {
        memo(cache);
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var carrot = item(Items.CARROT);
        var rule = f.rule("inventory_changes", carrot, true, new Slot[]{exact(apple), tool()},
            grid -> at(grid, 1, AEItemKey.of(grid.getStackInSlot(1))));
        f.stock(apple, 2);
        f.stock(damage(0), 1);
        var first = f.net.plan(carrot, 2);
        complete(f.net, first, carrot, 2);
        var frozen = CraftingTreeAudit.wireData(CraftingTreeCyclesTest.tree(first));
        f.stock(damage(0), 0);
        f.stock(damage(7), 1);
        var second = f.net.plan(carrot, 2);
        assertAll(
            () -> complete(f.net, second, carrot, 2),
            () -> materials(second.usedItems(), apple, 2, damage(7), 1),
            () -> f.assertExecutable(second),
            () -> f.assertTree(second));
        f.stock(damage(7), 0);
        var absent = f.net.plan(carrot, 2);
        materials(absent.missingItems(), damage(0), 1);
        long calls = rule.callbacks.get();
        assertEquals(frozen, CraftingTreeAudit.wireData(CraftingTreeCyclesTest.tree(first)));
        assertEquals(calls, rule.callbacks.get());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(ints = {0, 10_000})
    void replacingAReturningPatternWithAConsumingPatternInvalidatesItsOldCost(int cache) throws Exception {
        memo(cache);
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var carrot = item(Items.CARROT);
        var old = f.rule("old_reusable", carrot, false, new Slot[]{exact(apple), tool()},
            grid -> at(grid, 1, damage(0)));
        f.stock(apple, 2);
        f.stock(damage(0), 2);
        var first = f.net.plan(carrot, 2);
        materials(first.usedItems(), apple, 2, damage(0), 1);
        var frozen = CraftingTreeAudit.wireData(CraftingTreeCyclesTest.tree(first));
        f.net.removePattern(old.pattern);
        f.rules.clear();
        var replacement = f.rule("new_consumed", carrot, false, new Slot[]{exact(apple), tool()},
            CraftingRemainderFixtures::remaining);
        var second = f.net.plan(carrot, 2);
        assertAll(
            () -> complete(f.net, second, carrot, 2),
            () -> materials(second.usedItems(), apple, 2, damage(0), 2),
            () -> assertEquals(0, crafts(second, old.pattern)),
            () -> assertEquals(2, crafts(second, replacement.pattern)),
            () -> f.assertExecutable(second),
            () -> f.assertTree(second),
            () -> assertEquals(frozen, CraftingTreeAudit.wireData(CraftingTreeCyclesTest.tree(first))));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(ints = {0, 10_000})
    void failedPreferredRecipeDoesNotLeakItsToolReturnsIntoTheFallback(int cache) throws Exception {
        memo(cache);
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var potato = item(Items.POTATO);
        var carrot = item(Items.CARROT);
        var preferred = f.rule("needs_two_tools", carrot, false,
            new Slot[]{exact(apple), tool(), tool()}, grid -> {
                var out = at(grid, 1, damage(0));
                out.set(2, damage(0).toStack());
                return out;
            });
        var fallback = f.rule("uses_other_raw", carrot, false, new Slot[]{exact(apple), exact(potato)},
            CraftingRemainderFixtures::remaining);
        f.stock(apple, 1);
        f.stock(potato, 1);
        f.stock(damage(0), 1);
        assertTrue(f.reachable(f.net.stock, carrot, 1));
        var plan = f.net.plan(carrot, 1);
        assertAll(
            () -> complete(f.net, plan, carrot, 1),
            () -> materials(plan.usedItems(), apple, 1, potato, 1),
            () -> assertEquals(0, crafts(plan, preferred.pattern)),
            () -> assertEquals(1, crafts(plan, fallback.pattern)),
            () -> f.assertExecutable(plan),
            () -> f.assertTree(plan));
    }
}
