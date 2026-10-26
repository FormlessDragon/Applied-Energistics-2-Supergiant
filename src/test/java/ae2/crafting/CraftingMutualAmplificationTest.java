package ae2.crafting;

import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.crafting.pattern.AEProcessingPattern;
import ae2.client.gui.me.crafting.CraftingTreeCycleView;
import ae2.integration.data.CraftingTreeStackRegistry;
import ae2.integration.data.LiteCraftTreeNode;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static ae2.crafting.CraftingTreeCyclesTest.shownMissing;
import static ae2.crafting.CraftingTreeCyclesTest.tree;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingMutualAmplificationTest extends CraftingPlanningTestSupport {
    @ParameterizedTest
    @CsvSource({"0,64", "1,3"})
    void fourStoneVariantsGrowFromTheirSharedSeeds(int targetIndex, long stock) throws Exception {
        var net = new CraftingTestNetwork();
        var stones = List.of(0, 1, 3, 2).stream()
            .map(meta -> AEItemKey.of(new ItemStack(Blocks.STONE, 1, meta))).toList();
        var target = stones.get(targetIndex);
        for (var output : stones) {
            var inputs = new ArrayList<GenericStack>();
            for (var input : stones) {
                if (!input.equals(output)) inputs.add(new GenericStack(input, 1));
            }
            var encoded = new ItemStack(Items.PAPER);
            AEProcessingPattern.encode(encoded, inputs, List.of(new GenericStack(output, 6)));
            net.addPattern(new AEProcessingPattern(AEItemKey.of(encoded)), 0);
            if (!output.equals(target)) net.store(output, stock);
        }

        var originalStock = amounts(net.stock);
        // Reuse the same network, including its cached graph, when increasing the request size.
        for (long amount : new long[]{1000, 1_000_000_000L, 100_000_000_000L, 1_000_000_000_000L}) {
            var plan = net.plan(target, amount);
            complete(net, plan, target, amount);
            assertEquals(0, plan.usedItems().get(target));
            assertEquals(originalStock, amounts(net.stock));
            assertEquals(amounts(plan.missingItems()), amounts(shownMissing(tree(plan))));
            assertCycleView(plan, tree(plan));
            if (amount == 1000) assertNull(CraftingTestNetwork.replay(plan));
            assertBalance(plan, stones, target, amount);
        }
    }

    private static void assertCycleView(ICraftingPlan plan, LiteCraftTreeNode root) {
        var registry = new CraftingTreeStackRegistry();
        var buffer = Unpooled.buffer();
        try {
            root.writeToBuffer(buffer, registry);
            var client = LiteCraftTreeNode.fromBuffer(buffer, registry, null,
                new CraftingTreeStackRegistry.DecodeLimits(), 0);
            client.sort();
            var contracted = CraftingTreeCycleView.contract(client);
            assertEquals(1, contracted.cycles().size());
            var region = contracted.cycles().values().iterator().next();
            assertEquals(4, region.crafts(), "every mutually dependent recipe belongs to the same cycle");
            var external = new HashMap<ae2.api.stacks.AEKey, Long>();
            for (var process : contracted.root().inputs()) {
                for (var input : process.inputs()) {
                    assertTrue(input.inputs().isEmpty());
                    assertNull(external.put(input.output().what(), input.output().amount()),
                        "same external ingredient must be merged");
                }
            }
            assertEquals(amounts(plan.usedItems()), external, "internal transfers must not appear as extra stock");
            var ring = CraftingTreeCycleView.ring(region, new ReferenceOpenHashSet<>(), contracted.entries());
            assertEquals(region.feedbacks(), ring.feedback().size(), "all shared and ancestral sources must resolve");
            assertTrue(ring.feedback().values().stream().allMatch(process -> process.sourceId() >= 0));
            var shownStock = new HashMap<ae2.api.stacks.AEKey, Long>();
            CraftingTreeAudit.walk(ring.root(), ignored -> {}, process -> {
                assertEquals(process.inputs().size(), process.inputs().stream().map(n -> n.output().what()).distinct().count(),
                    "stored seeds and circulating inputs of the same material must share one icon per recipe");
                var original = region.procs().stream().filter(p -> p.sourceId() == process.sourceId()).findFirst().orElseThrow();
                var expected = new HashMap<ae2.api.stacks.AEKey, Long>();
                var shown = new HashMap<ae2.api.stacks.AEKey, Long>();
                original.inputs().forEach(n -> expected.merge(n.output().what(), n.output().amount(), Math::addExact));
                process.inputs().forEach(n -> shown.merge(n.output().what(), n.output().amount(), Math::addExact));
                assertEquals(expected, shown, "merging must preserve the full input amount");
                process.inputs().forEach(n -> {
                    var amounts = ring.amounts().get(n);
                    assertEquals(n.output().amount(), amounts.stored() + amounts.crafted() + amounts.returned() + n.missing(),
                        "tooltip contributions must explain the entire displayed amount");
                    if (amounts.stored() > 0) shownStock.merge(n.output().what(), amounts.stored(), Math::addExact);
                });
            });
            assertEquals(amounts(plan.usedItems()), shownStock, "merged icons retain the exact inventory withdrawals");
        } finally {
            buffer.release();
        }
    }

    private static void assertBalance(ICraftingPlan plan, List<AEItemKey> stones,
                                      AEItemKey target, long amount) {
        var balance = new HashMap<AEItemKey, BigInteger>();
        for (var key : stones) balance.put(key, BigInteger.valueOf(plan.usedItems().get(key)));
        plan.patternTimes().forEach((pattern, crafts) -> {
            var count = BigInteger.valueOf(crafts);
            for (var input : pattern.getInputs()) {
                var stack = input.possibleInputs()[0];
                balance.compute((AEItemKey) stack.what(), (key, value) -> value.subtract(count
                    .multiply(BigInteger.valueOf(stack.amount())).multiply(BigInteger.valueOf(input.getMultiplier()))));
            }
            for (var output : pattern.getOutputs()) {
                balance.compute((AEItemKey) output.what(), (key, value) -> value.add(count.multiply(BigInteger.valueOf(output.amount()))));
            }
        });
        assertTrue(balance.get(target).compareTo(BigInteger.valueOf(amount)) >= 0, balance::toString);
        assertTrue(balance.values().stream().allMatch(value -> value.signum() >= 0), balance::toString);
    }
}
