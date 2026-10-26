package ae2.crafting;

import ae2.api.stacks.AEFluidKey;
import ae2.client.gui.me.crafting.CraftingTreeCycleView;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraftforge.fluids.FluidRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static ae2.crafting.CraftingAdversarialFixtures.Fixture;
import static ae2.crafting.CraftingAdversarialFixtures.Scenario;
import static ae2.crafting.CraftingAdversarialFixtures.Step;
import static ae2.crafting.CraftingAdversarialFixtures.balanced;
import static ae2.crafting.CraftingTreeCyclesTest.tree;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Combinations of recursive features, checked against independently executable schedules. */
class CraftingRecursiveCombinationsTest extends CraftingPlanningTestSupport {
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void twoFuelLimitedProducersOfTheSameMaterialKeepBothCyclesAccessible(int order) throws Exception {
        var f = new Fixture(order);
        var first = f.recipe("first", new Object[]{"a", 1, "fuel-a", 1}, new Object[]{"a", 2});
        var second = f.recipe("second", new Object[]{"a", 1, "fuel-b", 1}, new Object[]{"a", 3});
        f.stock("a", 1);
        f.stock("fuel-a", 40);
        f.stock("fuel-b", 30);
        f.register((order & 1) != 0);
        new Scenario(f, "a", 100, List.of(new Step(first, 40), new Step(second, 30))).verifyWitness();
        var plan = f.net.plan(f.key("a"), 100);
        assertTrue(plan.missingItems().isEmpty(), () -> "missing=" + f.named(plan.missingItems())
            + ", used=" + f.named(plan.usedItems()) + ", crafts=" + plan.patternTimes());
        complete(f.net, plan, f.key("a"), 100);
        balanced(plan);
        assertNull(CraftingTestNetwork.replay(plan));
        CraftingTreeAudit.audit(f, plan, tree(plan));
        var contracted = CraftingTreeCycleView.contract(tree(plan));
        var shown = new ReferenceOpenHashSet<ae2.integration.data.LiteCraftTreeProc>();
        for (var region : contracted.entries().values()) shown.addAll(region.procs());
        assertEquals(2, shown.size(), "both recursive producers must be reachable in the cycle view");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void independentAmplifiersCanShareOneReturnedCatalyst(int order) throws Exception {
        var f = new Fixture(order);
        var a = f.recipe("grow-a", new Object[]{"a", 1, "tool", 1}, new Object[]{"a", 2, "tool", 1});
        var b = f.recipe("grow-b", new Object[]{"b", 1, "tool", 1}, new Object[]{"b", 2, "tool", 1});
        var root = f.recipe("root", new Object[]{"a", 3, "b", 2}, new Object[]{"result", 1});
        f.stock("a", 1);
        f.stock("b", 1);
        f.stock("tool", 1);
        f.register((order & 1) != 0);
        new Scenario(f, "result", 65, List.of(new Step(a, 195), new Step(b, 130), new Step(root, 65)))
            .verifyWitness();
        for (long amount : new long[]{65, 1_000_000_000L}) {
            var plan = f.net.plan(f.key("result"), amount);
            complete(f.net, plan, f.key("result"), amount);
            assertEquals(1, plan.usedItems().get(f.key("tool")), "the shared catalyst is borrowed only once");
            balanced(plan);
            CraftingTreeAudit.audit(f, plan, tree(plan));
            if (amount == 65) assertNull(CraftingTestNetwork.replay(plan));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void asymmetricItemFluidRingWithFiniteFuelBootstrapsAtItsExactThreshold(int order) throws Exception {
        var f = new Fixture(order);
        f.keys.put("water", AEFluidKey.of(FluidRegistry.WATER));
        var ab = f.recipe("a-b", new Object[]{"a", 2}, new Object[]{"b", 3});
        var bc = f.recipe("b-water", new Object[]{"b", 3}, new Object[]{"water", 4000});
        var ca = f.recipe("water-a", new Object[]{"water", 4000, "fuel", 1}, new Object[]{"a", 3});
        f.stock("a", 2);
        f.stock("fuel", 1_000_000_000L);
        f.register((order & 1) != 0);
        var steps = new ArrayList<Step>();
        for (int i = 0; i < 65; i++) steps.addAll(List.of(new Step(ab, 1), new Step(bc, 1), new Step(ca, 1)));
        new Scenario(f, "a", 65, steps).verifyWitness();
        for (long amount : new long[]{65, 1_000_000_000L}) {
            var plan = f.net.plan(f.key("a"), amount);
            complete(f.net, plan, f.key("a"), amount);
            balanced(plan);
            CraftingTreeAudit.audit(f, plan, tree(plan));
            if (amount == 65) assertNull(CraftingTestNetwork.replay(plan));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void asymmetricMutualGrowthWithAConsumableInputDoesNotInventSeeds(int order) throws Exception {
        var f = new Fixture(order);
        for (int output = 0; output < 4; output++) {
            var inputs = new ArrayList<Object>();
            for (int input = 0; input < 4; input++) if (input != output) inputs.addAll(List.of("m" + input, 1));
            if (output == 0) inputs.addAll(List.of("fuel", 1));
            f.recipe("grow-" + output, inputs.toArray(), new Object[]{"m" + output, 4 + output});
            f.stock("m" + output, output == 0 ? 0 : 4);
        }
        f.stock("fuel", 1_000_000_000L);
        f.register((order & 1) != 0);
        var steps = new ArrayList<Step>();
        for (int i = 0; i < 65; i++) for (var pattern : f.patterns.values()) steps.add(new Step(pattern, 1));
        new Scenario(f, "m0", 65, steps).verifyWitness();
        for (long amount : new long[]{65, 1_000_000_000L}) {
            var plan = f.net.plan(f.key("m0"), amount);
            complete(f.net, plan, f.key("m0"), amount);
            assertEquals(0, plan.usedItems().get(f.key("m0")));
            balanced(plan);
            CraftingTreeAudit.audit(f, plan, tree(plan));
            if (amount == 65) assertNull(CraftingTestNetwork.replay(plan));
            assertTrue(plan.usedItems().get(f.key("fuel")) > 0);
        }
    }
}
