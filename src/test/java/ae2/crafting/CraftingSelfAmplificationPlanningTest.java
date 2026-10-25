package ae2.crafting;

import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.core.AEConfig;
import ae2.crafting.graph.DemandPropagation;
import ae2.crafting.graph.GraphBuilder;
import ae2.crafting.graph.GraphExecutor;
import ae2.crafting.inv.ChildCraftingSimulationState;
import ae2.crafting.inv.NetworkCraftingSimulationState;
import ae2.crafting.pattern.AEProcessingPattern;
import ae2.integration.data.CraftingGraphDisplayTreeConverter;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static ae2.crafting.CraftingTestNetwork.crafts;
import static ae2.crafting.CraftingTreeCyclesTest.shownCrafts;
import static ae2.crafting.CraftingTreeCyclesTest.shownMissing;
import static ae2.crafting.CraftingTreeCyclesTest.tree;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces 1 stone -> 2 stone feeding 3 stone -> 4 stone bricks, using real processing patterns.
 */
class CraftingSelfAmplificationPlanningTest extends CraftingPlanningTestSupport {
    private static AEProcessingPattern processing(GenericStack input, GenericStack output) {
        var encoded = new ItemStack(Items.PAPER);
        AEProcessingPattern.encode(encoded, List.of(input), List.of(output));
        return new AEProcessingPattern(AEItemKey.of(encoded));
    }

    @ParameterizedTest(name = "stock={0}, bricks={1}, memo={2}")
    @CsvSource({
        "1, 4, 0", "2, 4, 0", "3, 4, 0",
        "1, 1000, 0", "2, 1000, 0", "3, 1000, 0",
        "1, 1000, 10000", "2, 1000, 10000", "3, 1000, 10000",
        "749, 1000, 0", "750, 1000, 0"
    })
    void oneStoneCanGrowEnoughMaterialForStoneBricks(long stock, long requested, int cache) {
        memo(cache);
        var net = new CraftingTestNetwork();
        var stone = AEItemKey.of(new ItemStack(Blocks.STONE));
        var bricks = AEItemKey.of(new ItemStack(Blocks.STONEBRICK));
        var grow = net.addPattern(processing(new GenericStack(stone, 1), new GenericStack(stone, 2)), 0);
        var finish = net.addPattern(processing(new GenericStack(stone, 3), new GenericStack(bricks, 4)), 0);
        net.store(stone, stock);

        var plan = net.plan(bricks, requested);
        assertEquals(0, plan.missingItems().get(stone), () -> "stock=" + stock + ", requested=" + requested
            + ", used=" + plan.usedItems().get(stone) + ", grow=" + crafts(plan, grow)
            + ", bricks=" + crafts(plan, finish));
        complete(net, plan, bricks, requested);
        assertEquals(requested / 4, crafts(plan, finish));
        assertTrue(stock + crafts(plan, grow) >= requested / 4 * 3,
            "each amplification adds one stone before the brick recipe consumes it");
        assertEquals(stock, net.stock.get(stone), "planning must not mutate network storage");
    }

    @ParameterizedTest
    @CsvSource({"1, 0", "2, 0", "3, 0", "1, 10000"})
    void directStoneRequestCanStartFromOneSeed(long stock, int cache) {
        memo(cache);
        var net = new CraftingTestNetwork();
        var stone = AEItemKey.of(new ItemStack(Blocks.STONE));
        net.addPattern(processing(new GenericStack(stone, 1), new GenericStack(stone, 2)), 0);
        net.store(stone, stock);
        complete(net, net.plan(stone, 1000), stone, 1000);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3})
    void selfAmplificationCoversDemandAfterStockIsDeducted(long stock) throws Exception {
        var net = new CraftingTestNetwork();
        var stone = AEItemKey.of(new ItemStack(Blocks.STONE));
        var bricks = AEItemKey.of(new ItemStack(Blocks.STONEBRICK));
        net.addPattern(processing(new GenericStack(stone, 1), new GenericStack(stone, 2)), 0);
        net.addPattern(processing(new GenericStack(stone, 3), new GenericStack(bricks, 4)), 0);
        net.store(stone, stock);
        var calc = net.calculation(bricks, 1000);
        // Inspect the graph before reserve planning and CPU replay can obscure a material-balance error.
        // This isolated stage runs synchronously, without the server-tick driver used by net.plan().
        int previousInterval = AEConfig.CRAFTING.craftingCalculationPausingInterval;
        AEConfig.CRAFTING.craftingCalculationPausingInterval = Integer.MAX_VALUE;
        try {
            var graph = new GraphBuilder(calc).buildGraph(bricks, 1000);
            var network = new NetworkCraftingSimulationState(net.stock);
            new DemandPropagation(calc).propagate(graph, new ChildCraftingSimulationState(network));
            var inventory = new ChildCraftingSimulationState(network);
            var snapshot = new GraphExecutor(calc, graph).applyGraph(inventory).build();
            var amplifier = graph.getRootNode().getInputs().getFirst().producer();
            var stoneInput = snapshot.nodes().getFirst().inputs().getFirst();
            var display = CraftingGraphDisplayTreeConverter.toLiteTree(snapshot, 1000, new KeyCounter());
            assertAll(
                () -> assertEquals(750 - stock, amplifier.getCraftTimes(),
                    "stock already includes the seed; each amplification supplies one additional stone"),
                () -> assertEquals(0, stoneInput.missing(), "the brick input must be covered by stock and growth"),
                () -> assertEquals(amplifier.getCraftTimes(), shownCrafts(display).getLong(amplifier.getPattern()),
                    "the seed and feedback together must equal the pattern's complete input consumption"));
        } finally {
            AEConfig.CRAFTING.craftingCalculationPausingInterval = previousInterval;
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3})
    void treeAndSummaryAgreeOnMissingStoneAfterPlanRepair(long stock) throws Exception {
        var net = new CraftingTestNetwork();
        var stone = AEItemKey.of(new ItemStack(Blocks.STONE));
        var bricks = AEItemKey.of(new ItemStack(Blocks.STONEBRICK));
        net.addPattern(processing(new GenericStack(stone, 1), new GenericStack(stone, 2)), 0);
        net.addPattern(processing(new GenericStack(stone, 3), new GenericStack(bricks, 4)), 0);
        net.store(stone, stock);
        var plan = net.plan(bricks, 1000);
        assertEquals(amounts(plan.missingItems()), amounts(shownMissing(tree(plan))),
            "the tree must account for the same missing materials as the final plan");
    }
}
