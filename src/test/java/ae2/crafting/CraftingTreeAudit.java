package ae2.crafting;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.container.me.crafting.CraftingPlanSummary;
import ae2.integration.data.CraftingTreeStackRegistry;
import ae2.integration.data.LiteCraftTreeNode;
import ae2.integration.data.LiteCraftTreeProc;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static ae2.crafting.CraftingPlanningTestSupport.amounts;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks every input, including non-first inputs and feedback; no integer-division estimate of craft counts.
 */
final class CraftingTreeAudit {
    static void audit(CraftingAdversarialFixtures.Fixture fixture, ICraftingPlan plan, LiteCraftTreeNode root) {
        assertAll("tree data\n" + fixture.describe(),
            () -> assertEquals(plan.finalOutput(), root.output(), "root request"),
            () -> structure(root, null, new ArrayList<>()),
            () -> assertEquals(fixture.named(plan.missingItems()), fixture.named(missing(root)), "tree/plan missing totals"),
            () -> inputsMatchPlan(fixture, plan, root),
            () -> rawLeavesMatchStock(fixture, plan, root),
            () -> roundTrip(root),
            () -> summary(fixture, plan));
    }

    static void walk(LiteCraftTreeNode node, Consumer<LiteCraftTreeNode> nodes, Consumer<LiteCraftTreeProc> procs) {
        nodes.accept(node);
        for (var proc : node.inputs()) {
            procs.accept(proc);
            for (var input : proc.inputs()) walk(input, nodes, procs);
        }
    }

    static KeyCounter missing(LiteCraftTreeNode root) {
        var result = new KeyCounter();
        walk(root, n -> result.add(n.output().what(), n.missing()), p -> {
        });
        result.removeZeros();
        return result;
    }

    static void structure(LiteCraftTreeNode node, LiteCraftTreeProc parent, List<LiteCraftTreeProc> ancestors) {
        structure(node, parent, ancestors, Map.of());
    }

    static void structure(LiteCraftTreeNode node, LiteCraftTreeProc parent, List<LiteCraftTreeProc> ancestors,
                          Map<IPatternDetails, KeyCounter> remainders) {
        assertSame(parent, node.parent(), "child must refer to its owning process");
        assertTrue(node.output().amount() > 0, "zero/negative tree amount");
        assertTrue(node.missing() >= 0 && node.missing() <= node.output().amount(), "missing exceeds node amount");
        if (LiteCraftTreeNode.isCycleFeedback(node.feedback())) {
            assertTrue(node.inputs().isEmpty(), "feedback must be a finite leaf");
            assertTrue(node.feedback() < ancestors.size(), "unresolved or out-of-range feedback distance");
            var producer = ancestors.get(ancestors.size() - 1 - node.feedback());
            assertNotNull(parent);
            assertTrue(parent.cycle() >= 0);
            assertEquals(parent.cycle(), producer.cycle(), "feedback crosses unrelated cycle groups");
            if (producer.pattern() != null) assertTrue(producer.pattern().getOutputs().stream()
                                                               .anyMatch(o -> o.what().equals(node.output().what()))
                    || remainders.containsKey(producer.pattern())
                    && remainders.get(producer.pattern()).get(node.output().what()) > 0,
                "feedback targets a different material");
        }
        for (var proc : node.inputs()) {
            assertFalse(proc.inputs().isEmpty(), "empty displayed process");
            ancestors.add(proc);
            for (var input : proc.inputs()) structure(input, proc, ancestors, remainders);
            ancestors.removeLast();
        }
    }

    static void inputsMatchPlan(CraftingAdversarialFixtures.Fixture fixture, ICraftingPlan plan,
                                LiteCraftTreeNode root) {
        var actual = new HashMap<IPatternDetails, Map<String, BigInteger>>();
        walk(root, n -> {
        }, proc -> {
            if (proc.pattern() == null) return; // Inventory-only/boundary groups are not recipe executions.
            var counts = actual.computeIfAbsent(proc.pattern(), p -> new HashMap<>());
            for (var input : proc.inputs())
                counts.merge(fixture.name(input.output().what()),
                    BigInteger.valueOf(input.output().amount()), BigInteger::add);
        });
        var expected = new HashMap<IPatternDetails, Map<String, BigInteger>>();
        plan.patternTimes().forEach((pattern, times) -> {
            if (times == 0) return;
            var counts = expected.computeIfAbsent(pattern, p -> new HashMap<>());
            for (var input : pattern.getInputs()) {
                assertEquals(1, input.possibleInputs().length, "audit uses exact processing patterns");
                var stack = input.possibleInputs()[0];
                counts.merge(fixture.name(stack.what()), BigInteger.valueOf(stack.amount())
                                                                   .multiply(BigInteger.valueOf(input.getMultiplier())).multiply(BigInteger.valueOf(times)),
                    BigInteger::add);
            }
        });
        assertEquals(expected, actual, "all recipe input totals must match the final plan, without lost remainders");
    }

    private static void rawLeavesMatchStock(CraftingAdversarialFixtures.Fixture fixture, ICraftingPlan plan,
                                            LiteCraftTreeNode root) {
        var craftable = new HashSet<AEKey>();
        fixture.patterns.values().forEach(p -> p.getOutputs().forEach(o -> craftable.add(o.what())));
        var shown = new KeyCounter();
        walk(root, node -> {
            if (node.inputs().isEmpty() && node.feedback() < 0 && !craftable.contains(node.output().what())) {
                shown.add(node.output().what(), node.output().amount() - node.missing());
            }
        }, p -> {
        });
        var expected = new KeyCounter();
        plan.usedItems().forEach((key, count) -> {
            if (!craftable.contains(key)) expected.add(key, count);
        });
        plan.emittedItems().forEach((key, count) -> {
            if (!craftable.contains(key)) expected.add(key, count);
        });
        assertEquals(fixture.named(expected), fixture.named(shown), "raw leaves duplicate or lose allocated stock");
    }

    /**
     * All wire-visible fields, excluding server-only pattern objects and parent references.
     */
    static List<Object> wireData(LiteCraftTreeNode node) {
        var children = new ArrayList<>();
        // An absent location entry and a present empty list encode the same empty location list on the wire.
        for (var proc : node.inputs())
            children.add(List.of(proc.cycle(), proc.machines(),
                proc.machines().stream().map(proc::machineLocations).toList(),
                proc.inputs().stream().map(CraftingTreeAudit::wireData).toList()));
        return List.of(node.output(), node.missing(), node.feedback(), children);
    }

    static void roundTrip(LiteCraftTreeNode root) {
        var registry = new CraftingTreeStackRegistry();
        var body = Unpooled.buffer();
        var keys = Unpooled.buffer();
        try {
            root.writeToBuffer(body, registry);
            registry.write(keys);
            var decodedRegistry = new CraftingTreeStackRegistry();
            decodedRegistry.read(keys);
            var decoded = LiteCraftTreeNode.fromBuffer(body, decodedRegistry, null);
            assertEquals(0, keys.readableBytes());
            assertEquals(0, body.readableBytes());
            assertEquals(wireData(root), wireData(decoded), "client round-trip changed tree data");
            structure(decoded, null, new ArrayList<>());
        } finally {
            body.release();
            keys.release();
        }
    }

    private static void summary(CraftingAdversarialFixtures.Fixture fixture, ICraftingPlan plan) {
        var summary = CraftingPlanSummary.fromJob(fixture.net.grid(), plan);
        var seen = new HashSet<AEKey>();
        var expectedKeys = new HashSet<AEKey>();
        for (var counter : List.of(plan.usedItems(), plan.missingItems(), plan.emittedItems())) {
            counter.forEach((key, count) -> {
                if (count > 0) expectedKeys.add(key);
            });
        }
        plan.patternTimes().keySet().forEach(p -> p.getOutputs().forEach(o -> expectedKeys.add(o.what())));
        var shownMissing = new KeyCounter();
        for (var entry : summary.entries()) {
            assertTrue(seen.add(entry.what()), "duplicate summary entry");
            shownMissing.add(entry.what(), entry.missingAmount());
            assertEquals(plan.usedItems().get(entry.what()) + plan.emittedItems().get(entry.what()), entry.storedAmount());
            assertEquals(fixture.net.stock.get(entry.what()), entry.inventoryAmount());
            assertEquals(entry.what().equals(plan.finalOutput().what()), entry.finalOutput());
            long crafted = plan.emittedItems().get(entry.what()), requests = 0;
            for (var recipe : plan.patternTimes().object2LongEntrySet()) {
                for (var output : recipe.getKey().getOutputs()) {
                    if (output.what().equals(entry.what())) {
                        crafted = Math.addExact(crafted, Math.multiplyExact(output.amount(), recipe.getLongValue()));
                        requests = Math.addExact(requests, recipe.getLongValue());
                    }
                }
            }
            assertEquals(requests, entry.requestCount());
            assertEquals(entry.finalOutput() ? plan.finalOutput().amount() : crafted, entry.craftAmount());
        }
        assertEquals(expectedKeys, seen, "summary key coverage");
        assertEquals(amounts(plan.missingItems()), amounts(shownMissing));
        var buffer = new PacketBuffer(Unpooled.buffer());
        try {
            summary.write(buffer);
            assertEquals(summary, CraftingPlanSummary.read(buffer), "summary packet changed quantities");
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
