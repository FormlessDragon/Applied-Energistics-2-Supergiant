package ae2.crafting;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.crafting.CalculationStrategy;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.execution.CraftingCpuTestDriver;
import ae2.helpers.patternprovider.PseudoPatternDetails;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static ae2.crafting.CraftingAdversarialFixtures.Fixture;
import static ae2.crafting.CraftingAdversarialFixtures.Scenario;
import static ae2.crafting.CraftingAdversarialFixtures.Step;
import static ae2.crafting.CraftingAdversarialFixtures.balanced;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reduced from finite-state seed 18: an unusable preferred recipe hides a feasible stock conversion cycle. */
class CraftingFiniteResourceRegressionTest extends CraftingPlanningTestSupport {
    private static Fixture network(boolean reverse, boolean trap) {
        return network(reverse, trap, 1);
    }

    private static Fixture network(boolean reverse, boolean trap, long unit) {
        var f = new Fixture(18);
        f.recipe("a-b", new Object[]{"a", 2 * unit}, new Object[]{"b", 2 * unit});
        if (trap) f.recipe("b-c", new Object[]{"b", 3 * unit}, new Object[]{"c", 3 * unit});
        f.recipe("c-d", new Object[]{"c", 2 * unit}, new Object[]{"d", 2 * unit});
        f.recipe("ab-c", new Object[]{"a", unit, "b", unit}, new Object[]{"c", 2 * unit});
        f.recipe("cd-a", new Object[]{"c", unit, "d", unit}, new Object[]{"a", 2 * unit});
        f.recipe("root", new Object[]{"ore", unit, "b", unit}, new Object[]{"result", unit});
        f.stock("a", unit);
        f.stock("b", unit);
        f.stock("c", unit);
        f.stock("ore", 2 * unit);
        f.register(reverse);
        f.net.recursiveReserve(0);
        return f;
    }

    private static List<Step> witness(Fixture f) {
        return List.of(new Step(f.patterns.get("ab-c"), 1), new Step(f.patterns.get("c-d"), 1),
            new Step(f.patterns.get("cd-a"), 1), new Step(f.patterns.get("a-b"), 1),
            new Step(f.patterns.get("root"), 2));
    }

    @ParameterizedTest
    @CsvSource({"0,false", "0,true", "10000,false", "10000,true"})
    void anUnusedAlternativeMustNotMakeAFeasibleConversionPlanMissing(int cache, boolean reverse) {
        memo(cache);
        assertAll("with and without the additional conversion", List.of(false, true).stream().map(trap -> () -> {
            var f = network(reverse, trap);
            new Scenario(f, "result", 2, witness(f)).verifyWitness();
            var plan = f.net.plan(f.key("result"), 2);
            assertAll("extra pattern=" + trap + ", missing=" + f.named(plan.missingItems()) + "\n" + f.describe(),
                () -> complete(f.net, plan, f.key("result"), 2),
                () -> balanced(plan),
                () -> assertNull(CraftingTestNetwork.replay(plan)));
        }));
    }

    @ParameterizedTest
    @CsvSource({
        "0,false,growing", "0,false,pseudo", "0,false,substitutes"
    })
    void anUnavailableSpecialAlternativeCannotHideAStockConversion(int cache, boolean reverse, String kind) throws Exception {
        memo(cache);
        var f = network(reverse, true);
        new Scenario(f, "result", 2, witness(f)).verifyWitness();
        var before = amounts(f.net.stock);
        var original = f.net.plan(f.key("result"), 2);
        var savedTree = CraftingTreeAudit.wireData(CraftingTreeCyclesTest.tree(original));
        var base = f.recipe("unused-b", new Object[]{"absent", kind.equals("growing") ? 1 : 2}, new Object[]{"b", 2});
        var unused = switch (kind) {
            case "growing" -> base;
            case "pseudo" -> PseudoPatternDetails.wrap(base);
            case "substitutes" -> {
                var inputs = new IPatternDetails.IInput[]{f.net.input(1,
                    new GenericStack(f.key("absent"), 2), new GenericStack(f.key("other-absent"), 2))};
                yield new IPatternDetails() {
                    public AEItemKey getDefinition() { return base.getDefinition(); }
                    public IInput[] getInputs() { return inputs; }
                    public List<GenericStack> getOutputs() { return base.getOutputs(); }
                };
            }
            default -> throw new IllegalArgumentException(kind);
        };
        f.net.addPattern(unused, 100);
        var exact = f.net.plan(f.key("result"), 2);
        var less = f.net.plan(f.key("result"), 3, CalculationStrategy.CRAFT_LESS);
        assertAll("an unsupported alternative must not disable the supported conversion witness",
            () -> complete(f.net, exact, f.key("result"), 2),
            () -> complete(f.net, less, f.key("result"), 2),
            () -> {
                for (var plan : List.of(exact, less)) {
                    balanced(plan);
                    assertEquals(0, plan.patternTimes().getLong(unused));
                    assertNull(CraftingTestNetwork.replay(plan));
                    CraftingTreeAudit.audit(f, plan, CraftingTreeCyclesTest.tree(plan));
                }
            });
        assertTrue(f.net.plan(f.key("result"), 3).simulation(), "the alternative still has no raw material");
        assertEquals(before, amounts(f.net.stock));
        assertEquals(savedTree, CraftingTreeAudit.wireData(CraftingTreeCyclesTest.tree(original)));
        assertExecutes(f, exact, 1);
        f.net.removePattern(unused);
        complete(f.net, f.net.plan(f.key("result"), 2), f.key("result"), 2);
        for (var name : List.of("a", "b", "c")) f.stock(name, 0);
        assertTrue(f.net.plan(f.key("result"), 2).simulation(), "depleting every seed still prevents conversion");
    }

    @ParameterizedTest
    @ValueSource(ints = {0})
    void manyUnsupportedAlternativesCannotExhaustTheConversionPatternLimit(int cache) {
        memo(cache);
        var f = network(false, true);
        for (int i = 0; i < 65; i++) {
            var pattern = f.recipe("unavailable-" + i, new Object[]{"absent-" + i, 1}, new Object[]{"b", 2});
            f.net.addPattern(pattern, 10_000 - i);
        }
        var plan = f.net.plan(f.key("result"), 2);
        complete(f.net, plan, f.key("result"), 2);
        assertNull(CraftingTestNetwork.replay(plan));
    }

    @ParameterizedTest
    @CsvSource({"0,false", "0,true"})
    void craftLessMustNotStopAtTheInitiallyAvailableRootInputs(int cache, boolean reverse) {
        memo(cache);
        var f = network(reverse, true);
        var plan = f.net.plan(f.key("result"), 3, CalculationStrategy.CRAFT_LESS);
        // There are exactly two ore; the witness attains this upper bound.
        new Scenario(f, "result", 2, witness(f)).verifyWitness();
        complete(f.net, plan, f.key("result"), 2);
    }

    @ParameterizedTest
    @CsvSource({"1,false", "13,true"})
    void theIndependentWitnessAndCalculatedPlanExecuteOnTheCraftingCpu(int budget, boolean calculate) {
        var f = network(false, true);
        new Scenario(f, "result", 2, witness(f)).verifyWitness();
        var times = new Object2LongOpenHashMap<IPatternDetails>();
        for (var step : witness(f)) times.addTo(step.pattern(), step.times());
        ICraftingPlan plan;
        if (calculate) {
            plan = f.net.plan(f.key("result"), 2);
            complete(f.net, plan, f.key("result"), 2);
        } else {
            plan = new CraftingPlan(new GenericStack(f.key("result"), 2), 1024, false, true,
                f.net.stock, new KeyCounter(), new KeyCounter(), 0, times, null, List.of(), null,
                new CraftingAttemptMetrics(), null);
        }
        balanced(plan);
        assertNull(CraftingTestNetwork.replay(plan));
        assertExecutes(f, plan, budget);
    }

    static void assertExecutes(Fixture f, ICraftingPlan plan, int budget) {
        var cpu = new CraftingCpuTestDriver(plan, CraftingTestNetwork.world());
        plan.usedItems().forEach(cpu::stock);
        var pending = new KeyCounter();
        var dispatched = new Object2LongOpenHashMap<IPatternDetails>();
        var provider = new ICraftingProvider() {
            public List<IPatternDetails> getAvailablePatterns() { return List.copyOf(f.patterns.values()); }
            public boolean isBusy() { return false; }
            public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs, int multiplier) {
                for (int slot = 0; slot < inputs.length; slot++) {
                    var expected = pattern.getInputs()[slot].possibleInputs()[0];
                    assertEquals(expected.amount() * pattern.getInputs()[slot].getMultiplier() * multiplier,
                        inputs[slot].get(expected.what()));
                }
                dispatched.addTo(pattern, multiplier);
                for (var output : pattern.getOutputs()) pending.add(output.what(), output.amount() * multiplier);
                return true;
            }
        };
        for (int tick = 0; cpu.remaining() > 0 && tick < 1000; tick++) {
            cpu.run(provider, budget);
            if (tick % 3 != 2) continue;
            for (var key : f.keys.values()) {
                if (key.equals(f.key("result"))) continue;
                long amount = pending.get(key);
                if (amount > 0) assertEquals(amount, cpu.deliver(key, amount));
                pending.remove(key);
            }
        }
        assertEquals(0, cpu.remaining());
        assertEquals(plan.finalOutput().amount(), pending.get(f.key("result")));
        assertEquals(plan.patternTimes(), dispatched);
    }

    @Test
    void cancellationStopsTheConversionSearch() {
        var f = network(false, true);
        var missing = (CraftingPlan) f.net.plan(f.key("result"), 3);
        var calculation = f.net.calculation(f.key("result"), 3);
        calculation.cancel();
        assertThrows(InterruptedException.class, () -> CraftingStockConversion.tryPlan(missing, calculation));
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 9_007_199_254_740_993L})
    void conversionQuantitiesStayExactBeyondDoublePrecision(long unit) {
        var f = network(false, true, unit);
        var plan = f.net.plan(f.key("result"), 2 * unit);
        complete(f.net, plan, f.key("result"), 2 * unit);
        balanced(plan);
        assertNull(CraftingTestNetwork.replay(plan));
        materials(plan.usedItems(), f.key("a"), unit, f.key("b"), unit, f.key("c"), unit, f.key("ore"), 2 * unit);
    }

    @ParameterizedTest
    @ValueSource(ints = {0})
    void storedFinalOutputCannotCoverANewProductionRequest(int cache) {
        memo(cache);
        var f = network(false, true);
        f.stock("result", 1_000_000);
        var before = amounts(f.net.stock);
        var plan = f.net.plan(f.key("result"), 2);
        complete(f.net, plan, f.key("result"), 2);
        assertEquals(0, plan.usedItems().get(f.key("result")));
        var impossible = f.net.plan(f.key("result"), 3);
        assertTrue(impossible.simulation());
        assertEquals(0, impossible.usedItems().get(f.key("result")));
        assertEquals(before, amounts(f.net.stock));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 64})
    void conversionFallbackPreservesTheConfiguredEndOfJobSeed(int reserve) {
        var f = new Fixture(reserve);
        f.recipe("grow", new Object[]{"a", 1, "fuel", 1}, new Object[]{"a", 2});
        f.recipe("root", new Object[]{"a", 2}, new Object[]{"result", 1});
        f.stock("a", 1);
        f.stock("fuel", 1);
        f.register(false);
        f.net.recursiveReserve(reserve);
        var plan = f.net.plan(f.key("result"), 1);
        if (reserve == 0) complete(f.net, plan, f.key("result"), 1);
        else {
            assertTrue(plan.simulation());
            materials(plan.missingItems(), f.key("fuel"), 1);
        }
        assertNull(CraftingTestNetwork.replay(plan));
    }
}
