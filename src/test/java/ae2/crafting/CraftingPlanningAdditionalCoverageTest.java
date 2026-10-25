package ae2.crafting;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.crafting.CalculationStrategy;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.networking.crafting.ICraftingService;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.helpers.patternprovider.PseudoPatternDetails;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static ae2.crafting.CraftingTestNetwork.*;
import static org.junit.jupiter.api.Assertions.*;

class CraftingPlanningAdditionalCoverageTest extends CraftingPlanningTestSupport {
    @ParameterizedTest
    @CsvSource({"0,false", "0,true", "10000,false", "10000,true"})
    void onlySomeCompetingBranchesMustSwitchProducer(int cache, boolean restrictedFirst) {
        memo(cache);
        var f = new CraftingAdversarialFixtures.Fixture(0);
        f.recipe("a_from_x", of("x", 1), of("a", 1));
        f.recipe("a_from_y", of("y", 1), of("a", 1));
        f.recipe("b_from_x", of("x", 1), of("b", 1));
        f.recipe("b_from_y", of("y", 1), of("b", 1));
        f.recipe("c_from_x", of("x", 2), of("c", 1));
        f.recipe("root", restrictedFirst ? of("c", 1, "a", 1, "b", 1) : of("a", 1, "b", 1, "c", 1),
            of("result", 1));
        f.register(false);
        f.net.priority(f.patterns.get("a_from_x"), 10);
        f.net.priority(f.patterns.get("b_from_x"), 10);
        f.stock("x", 3);
        f.stock("y", 1);
        // Independent executable witness: x -> a, y -> b, 2x -> c, then a+b+c -> result.
        new CraftingAdversarialFixtures.Scenario(f, "result", 1, List.of(
            new CraftingAdversarialFixtures.Step(f.patterns.get("a_from_x"), 1),
            new CraftingAdversarialFixtures.Step(f.patterns.get("b_from_y"), 1),
            new CraftingAdversarialFixtures.Step(f.patterns.get("c_from_x"), 1),
            new CraftingAdversarialFixtures.Step(f.patterns.get("root"), 1))).verifyWitness();
        var plans = List.of(f.net.plan(f.key("result"), 1), f.net.plan(f.key("result"), 1));
        assertAll(plans.stream().map(plan -> () -> complete(f.net, plan, f.key("result"), 1)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10000})
    void twoMutuallyDependentSeedTypesAreBothRequired(int cache) {
        memo(cache);
        var f = new CraftingAdversarialFixtures.Fixture(0);
        f.recipe("grow_both", of("a", 1, "b", 1), of("a", 2, "b", 2));
        f.recipe("root", of("a", 1, "b", 1), of("result", 1));
        f.register(false);
        f.stock("a", 1);
        var missing = f.net.plan(f.key("result"), 5);
        materials(missing.missingItems(), f.key("b"), 1);
        f.stock("b", 1);
        complete(f.net, f.net.plan(f.key("result"), 5), f.key("result"), 5);
    }

    @ParameterizedTest
    @CsvSource({"0,false", "0,true", "10000,false", "10000,true"})
    void secondaryOutputWithoutPrimaryDependencyIsNotIndexed(int cache, boolean pseudo) {
        memo(cache);
        var f = new CraftingAdversarialFixtures.Fixture(0);
        var p = f.recipe("split", of("raw", 2), of("a", 3, "b", 2));
        if (pseudo) p = PseudoPatternDetails.wrap(p);
        f.net.addPattern(p, 0);
        f.stock("raw", 6);
        var primary = f.net.plan(f.key("a"), 7);
        complete(f.net, primary, f.key("a"), 7);
        assertEquals(3, crafts(primary, p));
        // Current service intentionally indexes primary outputs only. Pin this policy rather than
        // incorrectly classifying direct secondary-output rejection as a new solver regression.
        assertTrue(f.net.grid().getService(ICraftingService.class).getCraftingFor(f.key("b")).isEmpty());
        for (int attempt = 0; attempt < 2; attempt++) {
            var plan = f.net.plan(f.key("b"), 5);
            materials(plan.missingItems(), f.key("b"), 5);
            assertTrue(plan.simulation());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10000})
    void exactRoundingBeyondDoublePrecision(int cache) {
        memo(cache);
        var net = new CraftingTestNetwork();
        var p = net.pattern("make", of("raw", 2), of("result", 3));
        long requested = (1L << 53) + 1;
        long times = requested / 3 + (requested % 3 == 0 ? 0 : 1);
        net.store("raw", 2 * times);
        var plan = net.plan("result", requested);
        complete(net, plan, "result", requested);
        assertEquals(times, crafts(plan, p));
        materials(plan.usedItems(), "raw", 2 * times);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10000})
    void emittedIntermediateUsesStockAndInvalidatesWhenProviderRemoved(int cache) {
        memo(cache);
        var net = new CraftingTestNetwork();
        net.pattern("root", of("x", 2), of("result", 1));
        net.store("x", 1);
        var service = net.grid().getService(ICraftingService.class);
        var provider = new ICraftingProvider() {
            public List<IPatternDetails> getAvailablePatterns() { return List.of(); }
            public Set<AEKey> getEmitableItems() { return Set.of(key("x")); }
            public boolean isBusy() { return false; }
            public boolean pushPattern(IPatternDetails p, KeyCounter[] in, int n) { return false; }
        };
        service.addGlobalCraftingProvider(provider);
        var plan = net.plan("result", 3);
        assertFalse(plan.simulation());
        materials(plan.usedItems(), "x", 1);
        materials(plan.emittedItems(), "x", 5);
        materials(plan.missingItems());
        service.removeGlobalCraftingProvider(provider);
        var after = net.plan("result", 3);
        materials(after.usedItems(), "x", 1);
        materials(after.emittedItems());
        materials(after.missingItems(), "x", 5);
    }

    @ParameterizedTest
    @CsvSource({"0,1", "0,2", "10000,1", "10000,2"})
    void craftLessMustChooseAMoreEfficientLowerPriorityRecipe(int cache, int stock) {
        memo(cache);
        var f = new CraftingAdversarialFixtures.Fixture(0);
        var expensive = f.recipe("expensive", of("x", 2), of("result", 1));
        f.recipe("efficient", of("x", 1), of("result", 1));
        f.register(false);
        f.net.priority(expensive, 10);
        f.stock("x", stock);
        complete(f.net, f.net.plan(f.key("result"), stock), f.key("result"), stock);
        var plan = f.net.plan(f.key("result"), 3, CalculationStrategy.CRAFT_LESS);
        complete(f.net, plan, f.key("result"), stock);
    }

}
