package ae2.crafting;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.integration.data.CraftingPlanTree;
import ae2.integration.data.CraftingTreeStackRegistry;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.init.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static ae2.crafting.CraftingAdversarialFixtures.Fixture;
import static ae2.crafting.CraftingAdversarialFixtures.ceil;
import static ae2.crafting.CraftingAdversarialFixtures.generated;
import static ae2.crafting.CraftingTreeCyclesTest.tree;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CraftingAdversarialTreeTest extends CraftingPlanningTestSupport {
    static Stream<Arguments> generatedCases() {
        return CraftingAdversarialPlanningTest.generatedCases();
    }

    @Test
    void oversizedInputTablesAreRejectedBeforeBuildingTheDisplayTree() {
        var net = new CraftingTestNetwork();
        var inputs = IntStream.rangeClosed(0, CraftingTreeStackRegistry.MAX_CHILDREN_PER_PROCESS)
                              .mapToObj(i -> net.input(1, new GenericStack(CraftingTestNetwork.key("tree-budget-" + i), 1)))
                              .toArray(IPatternDetails.IInput[]::new);
        var output = new GenericStack(CraftingTestNetwork.key("tree-budget-result"), 1);
        var pattern = new IPatternDetails() {
            public AEItemKey getDefinition() {
                return AEItemKey.of(Items.PAPER);
            }

            public IInput[] getInputs() {
                return inputs;
            }

            public List<GenericStack> getOutputs() {
                return List.of(output);
            }
        };
        var times = new Object2LongOpenHashMap<IPatternDetails>();
        times.put(pattern, 1);
        assertThrows(IllegalArgumentException.class, () -> CraftingPlanTree.build(output, times,
            new KeyCounter(), new KeyCounter(), new KeyCounter(), net.calculation(output.what(), 1), Map.of()));
    }

    @ParameterizedTest(name = "memo={0}, seed={1}")
    @MethodSource("generatedCases")
    void everyDisplayedInputMatchesTheFinalPlanAfterColdAndWarmPlanning(int cache, int seed) {
        memo(cache);
        var scenario = generated(seed);
        var f = scenario.fixture();
        assertAll("seed=" + seed, IntStream.range(0, 2).mapToObj(attempt -> (Executable) () -> {
            var plan = f.net.plan(f.key(scenario.output()), scenario.amount());
            CraftingTreeAudit.audit(f, plan, tree(plan));
        }));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void delayedTreesKeepTheirOwnDataAcrossAmountStockAndRecipeChanges(int cache) {
        memo(cache);
        var f = new Fixture(43);
        var grow = f.recipe("grow", new Object[]{"a", 1}, new Object[]{"a", 2});
        f.recipe("root", new Object[]{"a", 3}, new Object[]{"result", 4});
        f.stock("a", 1);
        f.register(false);
        var plans = new ArrayList<ICraftingPlan>();
        var savedMissing = new ArrayList<Object>();
        for (int quantity : new int[]{65, 1, 68, 4}) {
            var plan = f.net.plan(f.key("result"), quantity);
            plans.add(plan);
            savedMissing.add(amounts(plan.missingItems()));
            f.stock("a", quantity % 3);
        }
        f.net.removePattern(grow);
        f.stock("a", 0);
        var withoutGrow = f.net.plan(f.key("result"), 68);
        plans.add(withoutGrow);
        savedMissing.add(amounts(withoutGrow.missingItems()));
        f.net.addPattern(grow, 0);
        f.stock("a", 1);
        f.net.plan(f.key("result"), 17);
        assertAll(IntStream.range(0, plans.size()).mapToObj(i -> (Executable) () -> {
            var plan = plans.get(i);
            assertEquals(savedMissing.get(i), amounts(plan.missingItems()), "later plans rewrote an earlier plan");
            var displayed = tree(plan);
            var before = CraftingTreeAudit.wireData(displayed);
            assertAll("saved plan=" + i,
                () -> assertEquals(plan.finalOutput(), displayed.output()),
                () -> assertEquals(amounts(plan.missingItems()), amounts(CraftingTreeAudit.missing(displayed))),
                () -> CraftingTreeAudit.inputsMatchPlan(f, plan, displayed),
                () -> CraftingTreeAudit.roundTrip(displayed),
                () -> {
                    f.net.plan(f.key("result"), 9);
                    assertSame(displayed, tree(plan), "materialized tree should remain cached");
                    assertEquals(before, CraftingTreeAudit.wireData(tree(plan)), "later plan changed a displayed tree");
                });
        }));
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 127, 128, 2_147_483_648L, 4_294_967_299L})
    void treeAndSummaryKeepLongQuantitiesAndRecipeRounding(long request) throws Exception {
        var f = new Fixture(17);
        f.recipe("plate", new Object[]{"ore", 3}, new Object[]{"plate", 2});
        f.recipe("root", new Object[]{"plate", 4}, new Object[]{"result", 3});
        long rootTimes = ceil(request, 3), plateTimes = ceil(rootTimes * 4, 2);
        f.stock("ore", plateTimes * 3 - 1);
        f.register(false);
        var plan = f.net.plan(f.key("result"), request);
        assertAll(
            () -> materials(plan.missingItems(), f.key("ore"), 1),
            () -> assertEquals(rootTimes, plan.patternTimes().getLong(f.patterns.get("root"))),
            () -> assertEquals(plateTimes, plan.patternTimes().getLong(f.patterns.get("plate"))),
            () -> CraftingTreeAudit.audit(f, plan, tree(plan)));
    }
}
