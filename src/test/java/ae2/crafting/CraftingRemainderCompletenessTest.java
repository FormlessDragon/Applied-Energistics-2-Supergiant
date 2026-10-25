package ae2.crafting;

import ae2.api.networking.crafting.CalculationStrategy;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import net.minecraft.init.Items;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import static ae2.crafting.CraftingRemainderFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Finite-state completeness checks against full-grid BFS, including both reachable and unreachable inventories. */
class CraftingRemainderCompletenessTest extends CraftingRecipeTestSupport {
    private static Stream<Arguments> toolTransitions() {
        var cases = new ArrayList<Arguments>();
        for (int next0 = -1; next0 <= 1; next0++) {
            for (int next1 = -1; next1 <= 1; next1++) {
                for (int slots = 1; slots <= 2; slots++) {
                    for (int cache : new int[]{0, 10_000}) cases.add(Arguments.of(next0, next1, slots, cache));
                }
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "two-state returns 0->{0}, 1->{1}, slots={2}, memo={3}")
    @MethodSource("toolTransitions")
    void allTwoStateToolTransitionsAgreeWithExhaustiveReachability(int next0, int next1, int slots, int cache) {
        memo(cache);
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var carrot = item(Items.CARROT);
        var inputs = new Slot[slots + 1];
        inputs[0] = exact(apple);
        for (int i = 1; i < inputs.length; i++) inputs[i] = tool();
        f.rule("exhaustive_tool_transitions", carrot, true, inputs, grid -> {
            var out = remaining(grid);
            for (int slot = 1; slot <= slots; slot++) {
                int next = grid.getStackInSlot(slot).getItemDamage() == 0 ? next0 : next1;
                if (next >= 0) out.set(slot, damage(next).toStack());
            }
            return out;
        });
        var audit = new Audit("transitions=" + next0 + "," + next1 + " slots=" + slots + " memo=" + cache);
        for (int state0 = 0; state0 <= 2; state0++) {
            for (int state1 = 0; state1 <= 2; state1++) {
                for (int requested = 1; requested <= 3; requested++) {
                    f.net.stock.clear();
                    f.stock(apple, requested);
                    f.stock(damage(0), state0);
                    f.stock(damage(1), state1);
                    // Every craft consumes an apple. These graphs are finite, independently of any depth cutoff.
                    boolean reachable = f.reachableExhaustively(f.net.stock, carrot, requested);
                    int minimumMissing = minimumExtraEncodedTools(f, carrot, requested, slots * requested);
                    int count = requested;
                    String context = "stock0=" + state0 + " stock1=" + state1 + " request=" + count;
                    audit.check("REPORT " + context, () -> {
                        var plan = f.net.plan(carrot, count);
                        assertAll(
                            () -> agreesWithOracle(f, plan, carrot, count, reachable),
                            () -> materials(plan.missingItems(), damage(0), minimumMissing));
                    });
                    if (requested == 3) {
                        int maximum = 0;
                        for (int output = 1; output <= count; output++) {
                            if (f.reachableExhaustively(f.net.stock, carrot, output)) maximum = output;
                        }
                        int feasibleAmount = maximum;
                        audit.check("CRAFT_LESS " + context, () -> {
                            var plan = f.net.plan(carrot, count, CalculationStrategy.CRAFT_LESS);
                            agreesWithOracle(f, plan, carrot, feasibleAmount > 0 ? feasibleAmount : count,
                                feasibleAmount > 0);
                        });
                    }
                }
            }
        }
        audit.finish();
    }

    private static int minimumExtraEncodedTools(Fixture f, AEItemKey target, int requested, int limit) {
        for (int extra = 0; extra <= limit; extra++) {
            var supplied = new KeyCounter();
            supplied.addAll(f.net.stock);
            supplied.add(damage(0), extra);
            if (f.reachableExhaustively(supplied, target, requested)) return extra;
        }
        throw new AssertionError("fresh tools for every occupied slot must make this finite fixture feasible");
    }

    @ParameterizedTest(name = "staggered two-use tools, initially stored={0}")
    @CsvSource({"0", "3"})
    void threeTwoUseToolsAreEnoughForThreeCraftsWithTwoSimultaneousSlots(int stored) {
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var carrot = item(Items.CARROT);
        f.rule("staggered_two_use_tools", carrot, true, new Slot[]{exact(apple), tool(), tool()}, grid -> {
            var out = remaining(grid);
            for (int slot = 1; slot <= 2; slot++) {
                if (grid.getStackInSlot(slot).getItemDamage() == 0) out.set(slot, damage(1).toStack());
            }
            return out;
        });
        f.stock(apple, 3);
        f.stock(damage(0), stored);
        // Independent witness with tools A/B/C: use A+B, then worn A+C, then worn B+C.
        assertEquals(3 - stored, minimumExtraEncodedTools(f, carrot, 3, 6));
        var plan = f.net.plan(carrot, 3);
        assertAll(
            () -> agreesWithOracle(f, plan, carrot, 3, stored == 3),
            () -> materials(plan.missingItems(), damage(0), 3 - stored));
    }

    @ParameterizedTest(name = "strict tool cycle seed={0}, memo={1}")
    @CsvSource({"-1,0", "0,0", "1,0", "-1,10000", "0,10000", "1,10000"})
    void strictCrossPatternStateCyclesNeedExactlyOneRealBootstrap(int seed, int cache) throws Exception {
        memo(cache);
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var potato = item(Items.POTATO);
        var carrot = item(Items.CARROT);
        var bread = item(Items.BREAD);
        var gold = item(Items.GOLD_INGOT);
        f.rule("strict_cycle_left", carrot, false, new Slot[]{exact(apple), exact(charge(0))},
            grid -> at(grid, 1, charge(1)));
        f.rule("strict_cycle_right", bread, false, new Slot[]{exact(potato), exact(charge(1))},
            grid -> at(grid, 1, charge(0)));
        f.rule("strict_cycle_join", gold, false, new Slot[]{exact(carrot), exact(bread)},
            CraftingRemainderFixtures::remaining);
        f.stock(apple, 2);
        f.stock(potato, 2);
        if (seed >= 0) f.stock(charge(seed), 1);
        boolean reachable = f.reachableExhaustively(f.net.stock, gold, 2);
        assertEquals(seed >= 0, reachable, "an unseeded return cycle cannot start itself");
        agreesWithOracle(f, f.net.plan(gold, 2), gold, 2, reachable);
    }

    @ParameterizedTest(name = "global alternative safeFirst={0}, reversedInputs={1}, memo={2}")
    @CsvSource({"false,false,0", "false,true,0", "true,false,0", "true,true,0",
        "false,false,10000", "false,true,10000", "true,false,10000", "true,true,10000"})
    void locallyFeasibleProducersMustNotConsumeAnotherBranchesOnlyTool(boolean safeFirst, boolean reversed,
                                                                    int cache) throws Exception {
        memo(cache);
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var potato = item(Items.POTATO);
        var carrot = item(Items.CARROT);
        var bread = item(Items.BREAD);
        var gold = item(Items.GOLD_INGOT);
        var consuming = f.rule("locally_feasible_consuming", carrot, false,
            new Slot[]{exact(apple), exact(damage(0))}, CraftingRemainderFixtures::remaining);
        var returning = f.rule("globally_feasible_returning", carrot, false,
            new Slot[]{exact(apple), exact(potato)}, grid -> at(grid, 1, potato));
        f.net.priority(consuming.pattern, safeFirst ? 0 : 10);
        f.net.priority(returning.pattern, safeFirst ? 10 : 0);
        f.rule("exclusive_tool_consumer", bread, false, new Slot[]{exact(damage(0))},
            CraftingRemainderFixtures::remaining);
        f.rule("global_alternative_join", gold, false,
            reversed ? new Slot[]{exact(bread), exact(carrot)} : new Slot[]{exact(carrot), exact(bread)},
            CraftingRemainderFixtures::remaining);
        f.stock(apple, 1);
        f.stock(potato, 1);
        f.stock(damage(0), 1);
        assertTrue(f.reachableExhaustively(f.net.stock, gold, 1));
        agreesWithOracle(f, f.net.plan(gold, 1), gold, 1, true);
    }

    private static Stream<Arguments> siblingOrders() {
        return Stream.of("012", "021", "102", "120", "201", "210")
            .flatMap(order -> Stream.of(Arguments.of(order, false), Arguments.of(order, true)));
    }

    @ParameterizedTest(name = "shared-return order={0}, seeded={1}")
    @MethodSource("siblingOrders")
    void sharedRemainderFeasibilityAndTreeDoNotDependOnSiblingOrder(String order, boolean seeded) throws Exception {
        memo(10_000);
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var bowl = item(Items.BOWL);
        var carrot = item(Items.CARROT);
        var bread = item(Items.BREAD);
        var potato = item(Items.POTATO);
        var gold = item(Items.GOLD_INGOT);
        f.rule("ordered_return_producer", carrot, false,
            seeded ? new Slot[]{exact(apple), exact(bowl)} : new Slot[]{exact(apple)}, grid -> {
                var out = at(grid, 8, bowl);
                out.get(8).setCount(seeded ? 3 : 2);
                return out;
            });
        f.rule("ordered_return_left", bread, false, new Slot[]{exact(bowl)}, CraftingRemainderFixtures::remaining);
        f.rule("ordered_return_right", potato, false, new Slot[]{exact(bowl)}, CraftingRemainderFixtures::remaining);
        var products = new AEItemKey[]{carrot, bread, potato};
        var join = new Slot[3];
        for (int i = 0; i < 3; i++) join[i] = exact(products[order.charAt(i) - '0']);
        f.rule("ordered_return_join", gold, false, join, CraftingRemainderFixtures::remaining);
        f.stock(apple, 2);
        if (seeded) f.stock(bowl, 1);
        assertTrue(f.reachableExhaustively(f.net.stock, gold, 2));
        agreesWithOracle(f, f.net.plan(gold, 2), gold, 2, true);
    }

    private static void agreesWithOracle(Fixture f, ICraftingPlan plan, AEItemKey target, int requested,
                                         boolean reachable) {
        assertAll(
            () -> assertEquals(target, plan.finalOutput().what()),
            () -> assertEquals(requested, plan.finalOutput().amount()),
            () -> assertEquals(reachable, !plan.simulation() && amounts(plan.missingItems()).isEmpty(),
                "submit feasibility disagrees with exhaustive complete-grid execution; missing="
                    + amounts(plan.missingItems())),
            () -> withinStock(f.net, plan),
            () -> materials(plan.emittedItems()),
            () -> f.assertExecutable(plan),
            () -> f.assertTree(plan),
            () -> physicalRawStockLeavesMatchThePlan(plan));
    }

    private static void physicalRawStockLeavesMatchThePlan(ICraftingPlan plan) throws Exception {
        var primaryOutputs = new HashSet<AEKey>();
        plan.patternTimes().keySet().forEach(pattern -> pattern.getOutputs()
            .forEach(output -> primaryOutputs.add(output.what())));
        var expected = amounts(plan.usedItems());
        expected.keySet().removeAll(primaryOutputs);
        var raw = new KeyCounter();
        CraftingTreeAudit.walk(CraftingTreeCyclesTest.tree(plan), node -> {
            if (node.inputs().isEmpty() && node.feedback() < 0 && !primaryOutputs.contains(node.output().what())) {
                raw.add(node.output().what(), node.output().amount() - node.missing());
            }
        }, process -> {});
        assertEquals(expected, amounts(raw), "physical raw stock leaves must match planned withdrawals");
    }

    private static final class Audit {
        private final String family;
        private final List<String> examples = new ArrayList<>();
        private int checked;
        private int failed;
        private int reportFailures;
        private int craftLessFailures;

        Audit(String family) { this.family = family; }

        void check(String context, Executable test) {
            checked++;
            try {
                test.execute();
            } catch (Throwable error) {
                if (error instanceof Error fatal && !(fatal instanceof AssertionError)) throw fatal;
                failed++;
                if (context.startsWith("CRAFT_LESS")) craftLessFailures++;
                else reportFailures++;
                if (examples.size() < 8) {
                    var message = error.toString();
                    examples.add(context + ": " + message.substring(0, Math.min(message.length(), 1600)));
                }
            }
        }

        void finish() {
            System.out.printf("exhaustive-tools %s checked=%d failed=%d reportFailures=%d craftLessFailures=%d%n",
                family, checked, failed, reportFailures, craftLessFailures);
            assertEquals(0, failed, family + "; " + failed + "/" + checked + " scenarios disagree\n"
                + String.join("\n", examples));
        }
    }
}
