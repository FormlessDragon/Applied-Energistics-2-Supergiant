package ae2.crafting;

import ae2.api.networking.crafting.CalculationStrategy;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static ae2.crafting.CraftingAdversarialFixtures.Fixture;
import static ae2.crafting.CraftingAdversarialFixtures.Scenario;
import static ae2.crafting.CraftingAdversarialFixtures.Step;
import static ae2.crafting.CraftingAdversarialFixtures.balanced;
import static ae2.crafting.CraftingTreeCyclesTest.tree;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exhaustive reachability of small, finite inventories, independently of the planner and its replay validator.
 * Every recipe conserves or decreases the total item count, so the search terminates without a depth cutoff.
 */
class CraftingFiniteStatePlanningTest extends CraftingPlanningTestSupport {
    static Stream<Arguments> cases() {
        return IntStream.of(0, 10_000).boxed().flatMap(cache ->
            IntStream.range(0, 16).mapToObj(seed -> Arguments.of(cache, seed)));
    }

    static Stream<Arguments> splitCases() {
        return IntStream.of(0, 10_000).boxed().flatMap(cache ->
            IntStream.range(0, 8).mapToObj(seed -> Arguments.of(cache, seed)));
    }

    @ParameterizedTest(name = "split/join: memo={0}, seed={1}")
    @MethodSource("splitCases")
    void splitOutputsAndPriorityChangesAgreeWithReachability(int cache, int seed) throws Exception {
        memo(cache);
        var network = generated(seed, true);
        var f = network.fixture;
        var oracle = search(network);
        verifyWitness(network, oracle);
        var before = amounts(f.net.stock);
        var original = f.net.plan(f.key("result"), Math.max(1, oracle.maximum));
        var originalTree = tree(original);
        var savedTree = CraftingTreeAudit.wireData(originalTree);
        assertAll(oracle.describe() + "\n" + f.describe(), IntStream.of(1, -1).mapToObj(direction -> (Executable) () -> {
            int priority = 0;
            for (var pattern : f.patterns.values()) f.net.priority(pattern, direction * ++priority);
            var exact = f.net.plan(f.key("result"), Math.max(1, oracle.maximum));
            var impossible = f.net.plan(f.key("result"), oracle.maximum + 1);
            var less = f.net.plan(f.key("result"), oracle.maximum + 1, CalculationStrategy.CRAFT_LESS);
            assertAll("priority direction=" + direction,
                () -> {
                    if (oracle.maximum > 0) {
                        complete(f.net, exact, f.key("result"), oracle.maximum);
                        complete(f.net, less, f.key("result"), oracle.maximum);
                    } else {
                        assertTrue(exact.simulation());
                        assertTrue(less.simulation());
                    }
                },
                () -> assertTrue(impossible.simulation(), "cannot exceed the exhaustive maximum"),
                () -> assertEquals(before, amounts(f.net.stock)),
                () -> assertEquals(savedTree, CraftingTreeAudit.wireData(originalTree)),
                () -> {
                    for (var plan : List.of(exact, impossible, less)) {
                        withinStock(f.net, plan);
                        balanced(plan);
                        assertNull(CraftingTestNetwork.replay(plan));
                        CraftingTreeAudit.audit(f, plan, tree(plan));
                    }
                });
        }));
    }

    @ParameterizedTest(name = "finite inventory: memo={0}, seed={1}")
    @MethodSource("cases")
    void finiteCyclicNetworksAgreeWithExhaustiveReachability(int cache, int seed) {
        memo(cache);
        var network = generated(seed);
        var oracle = search(network);
        var f = network.fixture;
        var before = amounts(f.net.stock);
        String context = oracle.describe() + "\n" + f.describe();
        verifyWitness(network, oracle);
        assertAll(context, IntStream.of(Math.max(1, oracle.maximum), oracle.maximum + 1, Math.max(1, oracle.maximum))
            .mapToObj(request -> (Executable) () -> {
                var plan = f.net.plan(f.key("result"), request);
                String actual = "request=" + request + ", missing=" + f.named(plan.missingItems())
                    + ", crafts=" + plan.patternTimes();
                assertAll(actual,
                    () -> assertEquals(request > oracle.maximum, plan.simulation()),
                    () -> {
                        if (request <= oracle.maximum) complete(f.net, plan, f.key("result"), request);
                        else assertFalse(plan.missingItems().isEmpty());
                    },
                    () -> assertEquals(before, amounts(f.net.stock)),
                    () -> withinStock(f.net, plan),
                    () -> balanced(plan),
                    () -> assertNull(CraftingTestNetwork.replay(plan)),
                    () -> CraftingTreeAudit.audit(f, plan, tree(plan)));
            }));
    }

    @ParameterizedTest(name = "maximum output: memo={0}, seed={1}")
    @MethodSource("cases")
    void craftLessFindsTheExhaustivelyProvenMaximum(int cache, int seed) {
        memo(cache);
        var network = generated(seed);
        var oracle = search(network);
        var f = network.fixture;
        verifyWitness(network, oracle);
        int request = oracle.maximum + 1;
        var before = amounts(f.net.stock);
        var plan = f.net.plan(f.key("result"), request, CalculationStrategy.CRAFT_LESS);
        String context = oracle.describe() + ", actual=" + plan.finalOutput().amount()
            + ", missing=" + f.named(plan.missingItems()) + "\n" + f.describe();
        assertAll(context,
            () -> {
                if (oracle.maximum > 0) complete(f.net, plan, f.key("result"), oracle.maximum);
                else {
                    assertEquals(request, plan.finalOutput().amount());
                    assertTrue(plan.simulation());
                    assertFalse(plan.missingItems().isEmpty());
                }
            },
            () -> {
                assertEquals(before, amounts(f.net.stock));
                withinStock(f.net, plan);
                balanced(plan);
                assertNull(CraftingTestNetwork.replay(plan), context);
                CraftingTreeAudit.audit(f, plan, tree(plan));
            });
    }

    private static void verifyWitness(Network network, Oracle oracle) {
        var f = network.fixture;
        new Scenario(f, "result", oracle.maximum,
            oracle.witness.stream().map(name -> new Step(f.patterns.get(name), 1)).toList()).verifyWitness();
    }

    @ParameterizedTest
    @CsvSource({"2,1", "2,13", "18,1", "18,13"})
    void differentCountsForTheSameInventoryProduceExecutableCpuTasks(int seed, int budget) {
        var network = generated(seed);
        var oracle = search(network);
        var f = network.fixture;
        var plan = f.net.plan(f.key("result"), oracle.maximum);
        complete(f.net, plan, f.key("result"), oracle.maximum);
        CraftingFiniteResourceRegressionTest.assertExecutes(f, plan, budget);
    }

    private static Network generated(int seed) {
        return generated(seed, false);
    }

    static Fixture conversionFixture(int seed) {
        return generated(seed).fixture;
    }

    private static Network generated(int seed, boolean split) {
        var random = new Random(0xF1717EL + seed);
        int materials = 4 + random.nextInt(3);
        var f = new Fixture(seed);
        // End-of-job reserves are a separate policy, not part of the reachability question.
        f.net.recursiveReserve(0);
        var recipes = new ArrayList<Recipe>();
        for (int i = 0; i < materials; i++) {
            int batch = 1 + random.nextInt(3);
            int output = (seed & 1) == 0 ? batch : 1 + random.nextInt(batch);
            add(f, recipes, "ring-" + i, vector(materials, i, batch),
                vector(materials, (i + 1) % materials, output));
        }
        for (int i = 0, count = 1 + random.nextInt(4); i < count; i++) {
            int from = random.nextInt(materials);
            int other = (from + 1 + random.nextInt(materials - 1)) % materials;
            int to = (other + 1 + random.nextInt(materials - 1)) % materials;
            if (to == from) to = (to + 1) % materials;
            var input = vector(materials, from, 1);
            input[other]++;
            add(f, recipes, "join-" + i, input, vector(materials, to, 2));
        }
        if (split) {
            for (int from = 0; from < materials; from++) {
                var output = vector(materials, (from + 1) % materials, 1);
                output[(from + 2) % materials]++;
                add(f, recipes, "split-" + from, vector(materials, from, 2), output);
            }
        }
        var root = vector(materials, random.nextInt(materials), 1);
        root[random.nextInt(materials)]++;
        add(f, recipes, "root", root, vector(materials, materials, 1));
        var stock = new int[materials + 1];
        for (int i = 0, total = 4 + seed % 4; i < total; i++) stock[random.nextInt(materials)]++;
        for (int i = 0; i < materials; i++) f.stock(name(i, materials), stock[i]);
        f.register((seed & 2) != 0);
        return new Network(f, recipes, stock);
    }

    private static int[] vector(int materials, int item, int amount) {
        var result = new int[materials + 1];
        result[item] = amount;
        return result;
    }

    private static String name(int item, int materials) {
        return item == materials ? "result" : "m" + item;
    }

    private static Object[] pairs(int[] vector) {
        var result = new ArrayList<Object>();
        for (int i = 0; i < vector.length; i++) {
            if (vector[i] > 0) result.addAll(List.of(name(i, vector.length - 1), vector[i]));
        }
        return result.toArray();
    }

    private static void add(Fixture f, List<Recipe> recipes, String name, int[] inputs, int[] outputs) {
        assertTrue(Arrays.stream(inputs).sum() >= Arrays.stream(outputs).sum(), "search must remain finite");
        f.recipe(name, pairs(inputs), pairs(outputs));
        recipes.add(new Recipe(name, inputs, outputs));
    }

    private static Oracle search(Network network) {
        var start = Arrays.stream(network.stock).boxed().toList();
        var seen = new HashMap<List<Integer>, Previous>();
        var pending = new ArrayDeque<List<Integer>>();
        seen.put(start, null);
        pending.add(start);
        var best = start;
        int target = start.size() - 1;
        while (!pending.isEmpty()) {
            var current = pending.removeFirst();
            if (current.get(target) > best.get(target)) best = current;
            for (var recipe : network.recipes) {
                var next = new ArrayList<Integer>(current.size());
                for (int i = 0; i < current.size(); i++) {
                    if (current.get(i) < recipe.inputs[i]) break;
                    next.add(current.get(i) - recipe.inputs[i] + recipe.outputs[i]);
                }
                if (next.size() == current.size() && !seen.containsKey(next)) {
                    seen.put(next, new Previous(current, recipe.name));
                    pending.addLast(next);
                }
            }
        }
        var witness = new ArrayList<String>();
        for (var state = best; seen.get(state) != null; state = seen.get(state).state) {
            witness.add(seen.get(state).recipe);
        }
        Collections.reverse(witness);
        return new Oracle(best.get(target), List.copyOf(witness), seen.size());
    }

    private record Recipe(String name, int[] inputs, int[] outputs) {
    }

    private record Network(Fixture fixture, List<Recipe> recipes, int[] stock) {
    }

    private record Previous(List<Integer> state, String recipe) {
    }

    private record Oracle(int maximum, List<String> witness, int states) {
        String describe() {
            return "maximum=" + maximum + ", states=" + states + ", witness=" + witness;
        }
    }
}
