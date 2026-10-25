package ae2.crafting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static ae2.crafting.CraftingAdversarialFixtures.Fixture;
import static ae2.crafting.CraftingAdversarialFixtures.RANDOM_CASES;
import static ae2.crafting.CraftingAdversarialFixtures.Scenario;
import static ae2.crafting.CraftingAdversarialFixtures.Step;
import static ae2.crafting.CraftingAdversarialFixtures.balanced;
import static ae2.crafting.CraftingAdversarialFixtures.ceil;
import static ae2.crafting.CraftingAdversarialFixtures.generated;
import static ae2.crafting.CraftingTestNetwork.crafts;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verified feasible schedules must not be rejected; impossible cases have independent shortage bounds.
 */
class CraftingAdversarialPlanningTest extends CraftingPlanningTestSupport {
    static Stream<Arguments> generatedCases() {
        return IntStream.of(0, 10_000).boxed().flatMap(cache ->
            IntStream.range(0, RANDOM_CASES).mapToObj(seed -> Arguments.of(cache, seed)));
    }

    static Stream<Arguments> gainBoundaries() {
        var cases = new ArrayList<Arguments>();
        for (int cache : new int[]{0, 10_000})
            for (int capacity : new int[]{1, 2, 5}) {
                for (int gain : new int[]{1, 4})
                    for (int input : new int[]{1, 3}) {
                        for (int request : new int[]{1, 4, 17}) {
                            for (int stock : IntStream.of(0, capacity - 1, capacity, capacity + 1).distinct().toArray()) {
                                cases.add(Arguments.of(cache, capacity, gain, input, request, stock));
                            }
                        }
                    }
            }
        return cases.stream();
    }

    static Stream<Arguments> nonGrowingCases() {
        var cases = new ArrayList<Arguments>();
        for (int capacity : new int[]{2, 5})
            for (int loss : new int[]{0, 1}) {
                for (int stock : new int[]{0, capacity - 1, capacity, 26, 27, 28}) {
                    cases.add(Arguments.of(capacity, loss, stock));
                }
            }
        return cases.stream();
    }

    @ParameterizedTest(name = "memo={0}, seed={1}")
    @MethodSource("generatedCases")
    void independentlyExecutableNetworksMustNotBeRejected(int cache, int seed) {
        memo(cache);
        var scenario = generated(seed);
        scenario.verifyWitness();
        var f = scenario.fixture();
        var stock = amounts(f.net.stock);
        assertAll("feasible seed=" + seed + "\n" + f.describe(), IntStream.range(0, 2).mapToObj(attempt ->
            (Executable) () -> {
                var plan = f.net.plan(f.key(scenario.output()), scenario.amount());
                assertAll("attempt=" + attempt + ", missing=" + f.named(plan.missingItems()),
                    () -> complete(f.net, plan, f.key(scenario.output()), scenario.amount()),
                    () -> balanced(plan),
                    () -> assertEquals(0, plan.usedItems().get(f.key(scenario.output())),
                        "stocked final output cannot replace crafting the request"),
                    () -> assertEquals(stock, amounts(f.net.stock), "planning mutated inventory"));
            }));
    }

    @ParameterizedTest(name = "memo={0}, {1}->{1}+{2}, input={3}, request={4}, stock={5}")
    @MethodSource("gainBoundaries")
    void seedThresholdsAndBatchRoundingHaveOnlyTheMathematicallyNecessaryShortage(
        int cache, int capacity, int gain, int input, int request, int stock) {
        memo(cache);
        var f = new Fixture(capacity * 31 + gain * 7 + input);
        f.recipe("grow", new Object[]{"a", capacity}, new Object[]{"a", capacity + gain});
        f.recipe("root", new Object[]{"a", input}, new Object[]{"result", 4});
        f.stock("a", stock);
        f.register(false);
        f.net.recursiveReserve(0); // Separate minimal material requirements from the optional end-of-job reserve.
        long needed = ceil(request, 4) * input;
        long missing = Math.max(0, Math.min(capacity, needed) - stock);
        var plan = f.net.plan(f.key("result"), request);
        assertAll(f.describe(),
            () -> materials(plan.missingItems(), f.key("a"), missing),
            () -> assertEquals(ceil(request, 4), crafts(plan, f.patterns.get("root"))),
            () -> withinStock(f.net, plan),
            () -> balanced(plan));
    }

    @ParameterizedTest(name = "input={0}, loss={1}, stock={2}")
    @MethodSource("nonGrowingCases")
    void zeroAndNegativeGainCannotHideTrueRawMaterialShortages(int capacity, int loss, int stock) {
        var f = new Fixture(stock);
        var cycle = f.recipe("non-growing", new Object[]{"a", capacity}, new Object[]{"a", capacity - loss});
        f.recipe("root", new Object[]{"a", 3}, new Object[]{"result", 1});
        f.stock("a", stock);
        f.register(false);
        f.net.recursiveReserve(0);
        var plan = f.net.plan(f.key("result"), 9);
        assertAll(f.describe(),
            () -> materials(plan.missingItems(), f.key("a"), Math.max(0, 27 - stock)),
            () -> assertEquals(0, crafts(plan, cycle), "a losing cycle cannot supply a deficit"),
            () -> withinStock(f.net, plan),
            () -> balanced(plan));
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "0, 1", "0, 21", "0, 22", "0, 23", "10000, 0", "10000, 21", "10000, 22"})
    void anAmplifierWithConsumableFuelReportsFuelRatherThanInventedSeeds(int cache, int fuel) {
        memo(cache);
        var f = new Fixture(fuel);
        f.recipe("grow", new Object[]{"a", 1, "fuel", 2}, new Object[]{"a", 2});
        f.recipe("root", new Object[]{"a", 3}, new Object[]{"result", 1});
        f.stock("a", 1);
        f.stock("fuel", fuel);
        f.register(false);
        f.net.recursiveReserve(0);
        var plan = f.net.plan(f.key("result"), 4);
        assertAll(f.describe(),
            () -> materials(plan.missingItems(), f.key("fuel"), Math.max(0, 22 - fuel)),
            () -> withinStock(f.net, plan),
            () -> balanced(plan));
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "1, 1", "64, 1", "0, 2", "1, 2", "64, 2", "0, 3", "1, 3", "64, 3"})
    void reservePolicyCannotTurnFreeGrowthIntoAnExternalMaterialRequirement(int reserve, int stock) {
        var f = new Fixture(reserve);
        f.recipe("grow", new Object[]{"a", 1}, new Object[]{"a", 2});
        f.recipe("root", new Object[]{"a", 3}, new Object[]{"result", 4});
        f.stock("a", stock);
        f.register(false);
        f.net.recursiveReserve(reserve);
        var plan = f.net.plan(f.key("result"), 65);
        complete(f.net, plan, f.key("result"), 65);
    }

    @ParameterizedTest
    @CsvSource({"0, false", "0, true", "10000, false", "10000, true"})
    void aCraftableSeedCanStartAnOtherwiseUnseededAmplifier(int cache, boolean reverse) {
        memo(cache);
        var f = new Fixture(reverse ? 1 : 0);
        var starter = f.recipe("starter", new Object[]{"ore", 1}, new Object[]{"a", 1});
        var grow = f.recipe("grow", new Object[]{"a", 1}, new Object[]{"a", 2});
        var root = f.recipe("root", new Object[]{"a", 3}, new Object[]{"result", 4});
        f.stock("ore", 1);
        f.register(reverse);
        f.net.priority(grow, 10);
        var scenario = new Scenario(f, "result", 68,
            List.of(new Step(starter, 1), new Step(grow, 51), new Step(root, 17)));
        scenario.verifyWitness();
        var plan = f.net.plan(f.key("result"), 68);
        complete(f.net, plan, f.key("result"), 68);
        balanced(plan);
    }

    @ParameterizedTest
    @CsvSource({"1, 1, 1", "1, 1, 2", "1, 1, 17", "2, 1, 1", "2, 1, 2", "2, 1, 17",
        "5, 1, 1", "5, 1, 4", "5, 1, 5", "5, 1, 17", "5, 4, 1", "5, 4, 17"})
    void requestedOutputCanAlsoBeTheSeedWithoutBeingTakenForFree(int capacity, int gain, int request) {
        var f = new Fixture(capacity * 17 + gain);
        var grow = f.recipe("grow", new Object[]{"a", capacity}, new Object[]{"a", capacity + gain});
        f.stock("a", capacity);
        f.register(false);
        var scenario = new Scenario(f, "a", request, List.of(new Step(grow, ceil(request, gain))));
        scenario.verifyWitness();
        var plan = f.net.plan(f.key("a"), request);
        complete(f.net, plan, f.key("a"), request);
        balanced(plan);
    }

    @Test
    void anotherUnseededAmplifierCannotProvideARealBootstrapSeed() {
        var f = new Fixture(19);
        var grow = f.recipe("grow", new Object[]{"a", 1}, new Object[]{"a", 2});
        f.recipe("other-grow", new Object[]{"a", 2}, new Object[]{"a", 3});
        f.recipe("root", new Object[]{"a", 3}, new Object[]{"result", 4});
        f.register(false);
        f.net.priority(grow, 10);
        f.net.recursiveReserve(0);
        var plan = f.net.plan(f.key("result"), 1000);
        materials(plan.missingItems(), f.key("a"), 1);
        withinStock(f.net, plan);
        balanced(plan);
    }

    @ParameterizedTest
    @CsvSource({"2", "1"})
    void aNonGrowingRootCannotCountStoredOutputAsNewProduction(int output) {
        var f = new Fixture(output);
        f.recipe("loop", new Object[]{"a", 2}, new Object[]{"a", output});
        f.stock("a", 27);
        f.register(false);
        var plan = f.net.plan(f.key("a"), 9);
        materials(plan.usedItems());
        materials(plan.missingItems(), f.key("a"), 9);
        balanced(plan);
    }
}
