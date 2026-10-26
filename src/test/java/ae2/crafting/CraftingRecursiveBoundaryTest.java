package ae2.crafting;

import ae2.api.stacks.AEKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Set;

import static ae2.crafting.CraftingAdversarialFixtures.Fixture;
import static ae2.crafting.CraftingAdversarialFixtures.balanced;
import static ae2.crafting.CraftingTreeCyclesTest.tree;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingRecursiveBoundaryTest extends CraftingPlanningTestSupport {
    @ParameterizedTest
    @CsvSource({"0,0,0", "0,1,1", "1,0,1", "1,1,1"})
    void sharedCatalystRequiresEveryIndependentSeedExactlyOnce(int a, int b, int tool) throws Exception {
        var f = new Fixture(a * 100 + b * 10 + tool);
        f.recipe("grow-a", new Object[]{"a", 1, "tool", 1}, new Object[]{"a", 2, "tool", 1});
        f.recipe("grow-b", new Object[]{"b", 1, "tool", 1}, new Object[]{"b", 2, "tool", 1});
        f.recipe("root", new Object[]{"a", 3, "b", 2}, new Object[]{"result", 1});
        f.stock("a", a);
        f.stock("b", b);
        f.stock("tool", tool);
        f.register(false);
        var before = amounts(f.net.stock);
        for (long request : new long[]{17, 1_000_000_000L, 17}) {
            var plan = f.net.plan(f.key("result"), request);
            assertEquals(before, amounts(f.net.stock));
            materials(plan.missingItems(), f.key("a"), 1 - a, f.key("b"), 1 - b, f.key("tool"), Math.max(0, 1 - tool));
            assertEquals(a == 0 || b == 0 || tool == 0, plan.simulation());
            withinStock(f.net, plan);
            balanced(plan);
            var root = tree(plan);
            CraftingTreeAudit.audit(f, plan, root);
            if (request == 17) assertNull(CraftingTestNetwork.replay(plan), "supplying the reported shortage must unblock the plan");
            var filtered = root.withMissingOnly();
            if (plan.missingItems().isEmpty()) assertNull(filtered);
            else {
                assertEquals(amounts(plan.missingItems()), amounts(CraftingTreeAudit.missing(filtered)));
                CraftingTreeAudit.structure(filtered, null, new ArrayList<>());
                CraftingTreeAudit.roundTrip(filtered);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void finiteFuelAlternativesRespectShortagesAndPriorityChangesAcrossCachedRequests(int cache) throws Exception {
        memo(cache);
        var f = new Fixture(cache);
        var first = f.recipe("first", new Object[]{"a", 1, "fuel-a", 1}, new Object[]{"a", 2});
        var second = f.recipe("second", new Object[]{"a", 1, "fuel-b", 1}, new Object[]{"a", 3});
        f.recipe("root", new Object[]{"a", 1}, new Object[]{"result", 1});
        f.stock("a", 1);
        f.register(false);
        for (long request : new long[]{100, 1_000_000_000L, 100}) {
            f.stock("fuel-a", request / 5 * 2);
            f.stock("fuel-b", request / 10 * 3);
            var stock = amounts(f.net.stock);
            for (int priority : new int[]{10, -10}) {
                f.net.priority(first, priority);
                var plan = f.net.plan(f.key("result"), request);
                complete(f.net, plan, f.key("result"), request);
                assertTrue(plan.patternTimes().getLong(first) > 0 && plan.patternTimes().getLong(second) > 0);
                balanced(plan);
                var saved = CraftingTreeAudit.wireData(tree(plan));
                if (request == 100) assertNull(CraftingTestNetwork.replay(plan));
                // Even consuming the starter at the end gives at most request + 1 outputs.
                var shortPlan = f.net.plan(f.key("result"), request + 2);
                assertFalse(shortPlan.missingItems().isEmpty(), "finite fuels cannot support unlimited recursive growth");
                assertTrue(shortPlan.simulation());
                Set<AEKey> fuels = Set.of(f.key("fuel-a"), f.key("fuel-b"));
                assertTrue(shortPlan.missingItems().keySet().stream().allMatch(fuels::contains),
                    () -> "report the real fuel shortage, not invented seeds: " + f.named(shortPlan.missingItems()));
                balanced(shortPlan);
                CraftingTreeAudit.audit(f, shortPlan, tree(shortPlan));
                if (request == 100) assertNull(CraftingTestNetwork.replay(shortPlan));
                assertEquals(stock, amounts(f.net.stock));
                assertEquals(saved, CraftingTreeAudit.wireData(tree(plan)), "subsequent failed requests must not rewrite prior trees");
            }
        }
    }
}
