package ae2.crafting;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.client.gui.me.crafting.CraftingTreeCycleView;
import ae2.core.localization.PlayerMessages;
import ae2.integration.data.CraftingTreeStackRegistry;
import ae2.integration.data.LiteCraftTreeNode;
import ae2.integration.data.LiteCraftTreeProc;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static ae2.crafting.CraftingTestNetwork.key;
import static ae2.crafting.CraftingTestNetwork.names;
import static ae2.crafting.CraftingTestNetwork.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crafting cycles marked in the display tree of a plan, how the crafting tree screen contracts them and shows each
 * as a ring, and what a cycle without net output is reported as.
 */
class CraftingTreeCyclesTest {

    static LiteCraftTreeNode tree(ICraftingPlan plan) throws Exception {
        return ((CraftingPlanDisplaySource) plan).requestDisplayTree().get(30, TimeUnit.SECONDS);
    }

    private static void walk(LiteCraftTreeNode node, Consumer<LiteCraftTreeNode> nodes,
                             Consumer<LiteCraftTreeProc> procs) {
        nodes.accept(node);
        for (var proc : node.inputs()) {
            procs.accept(proc);
            for (var input : proc.inputs()) walk(input, nodes, procs);
        }
    }

    static String describe(LiteCraftTreeNode node, String indent) {
        var text = new StringBuilder(indent).append(node.output().what()).append(" x").append(node.output().amount());
        if (node.feedback() >= 0) text.append(" fed back ").append(node.feedback());
        text.append('\n');
        for (var proc : node.inputs()) {
            text.append(indent).append("  craft").append(proc.cycle() >= 0 ? " of cycle " + proc.cycle() : "")
                .append('\n');
            for (var input : proc.inputs()) text.append(describe(input, indent + "    "));
        }
        return text.toString();
    }

    private static List<LiteCraftTreeNode> feedback(LiteCraftTreeNode root) {
        var found = new ArrayList<LiteCraftTreeNode>();
        walk(root, node -> {
            if (node.feedback() >= 0) found.add(node);
        }, proc -> {
        });
        return found;
    }

    private static Set<Integer> cycles(LiteCraftTreeNode root) {
        var found = new TreeSet<Integer>();
        walk(root, node -> {
        }, proc -> {
            if (proc.cycle() >= 0) found.add(proc.cycle());
        });
        return found;
    }

    private static long cycleCrafts(LiteCraftTreeNode root) {
        var count = new long[1];
        walk(root, node -> {
        }, proc -> {
            if (proc.cycle() >= 0) count[0]++;
        });
        return count[0];
    }

    /**
     * The crafts of each pattern the tree shows, from how much the crafts take of their first input. A craft can show
     * one input as several nodes, such as what a cycle feeds back and what comes from storage.
     */
    static Object2LongOpenHashMap<IPatternDetails> shownCrafts(LiteCraftTreeNode root) {
        var crafts = new Object2LongOpenHashMap<IPatternDetails>();
        walk(root, node -> {
        }, proc -> {
            AEKey what = proc.inputs().getFirst().output().what();
            long taken = 0;
            for (var input : proc.inputs()) {
                if (input.output().what().equals(what)) taken += input.output().amount();
            }
            long perCraft = 0;
            for (var patternInput : proc.pattern().getInputs()) {
                var possible = patternInput.possibleInputs()[0];
                if (possible.what().equals(what)) {
                    perCraft += possible.amount() * patternInput.getMultiplier();
                }
            }
            crafts.addTo(proc.pattern(), taken / perCraft);
        });
        return crafts;
    }

    static KeyCounter shownMissing(LiteCraftTreeNode root) {
        var missing = new KeyCounter();
        walk(root, node -> missing.add(node.output().what(), node.missing()), proc -> {
        });
        missing.removeZeros();
        return missing;
    }

    private static CraftingTestNetwork threeCraftCycle() {
        // dust <- c2a <- c <- b2c <- b <- a2b <- (fuel, a back from c2a)
        var net = new CraftingTestNetwork();
        net.pattern("a2b", of("a", 1, "fuel", 1), of("b", 1));
        net.pattern("b2c", of("b", 1), of("c", 1));
        net.pattern("c2a", of("c", 1), of("dust", 1, "a", 2));
        net.store("fuel", 100);
        net.store("a", 1);
        return net;
    }

    @Test
    void aCycleIsMarkedWithTheInputFedBackAroundIt() throws Exception {
        var root = tree(threeCraftCycle().plan("dust", 4));

        assertEquals(1, cycles(root).size(), "one cycle");
        assertEquals(3, cycleCrafts(root), "its three crafts");
        var fedBack = feedback(root);
        assertFalse(fedBack.isEmpty(), "an input fed back");
        for (var input : fedBack) {
            assertEquals(key("a"), input.output().what());
            assertTrue(input.inputs().isEmpty(), "fed back, not made again");
            assertTrue(input.parent().cycle() >= 0, "taken by a craft of the cycle");
        }
    }

    @Test
    void aCatalystIsFedBackToTheCraftTakingIt() throws Exception {
        var net = new CraftingTestNetwork();
        net.pattern("x2y", of("x", 1, "cat", 1), of("y", 1, "cat", 1));
        net.store("x", 10);
        net.store("cat", 1);

        var root = tree(net.plan("y", 5));

        var fedBack = feedback(root);
        assertEquals(1, fedBack.size(), "the catalyst");
        assertEquals(key("cat"), fedBack.getFirst().output().what());
        assertEquals(0, fedBack.getFirst().feedback(), "made by the craft taking it");
        assertEquals(1, cycles(root).size());
    }

    @Test
    void aChainHasNoCycle() throws Exception {
        var net = new CraftingTestNetwork();
        net.pattern("plank", of("log", 1), of("plank", 4));
        net.pattern("stick", of("plank", 2), of("stick", 4));
        net.store("log", 10);

        var root = tree(net.plan("stick", 16));

        assertTrue(cycles(root).isEmpty());
        assertTrue(feedback(root).isEmpty());
    }

    @Test
    void cyclesSurviveTheTripToTheClient() throws Exception {
        var root = tree(threeCraftCycle().plan("dust", 4));
        var registry = new CraftingTreeStackRegistry();
        var buffer = Unpooled.buffer();
        root.writeToBuffer(buffer, registry);

        var read = LiteCraftTreeNode.fromBuffer(buffer, registry, null, new CraftingTreeStackRegistry.DecodeLimits(), 0);

        assertEquals(cycles(root), cycles(read));
        assertEquals(cycleCrafts(root), cycleCrafts(read));
        assertEquals(feedback(root).size(), feedback(read).size());
        assertEquals(feedback(root).getFirst().feedback(), feedback(read).getFirst().feedback());
    }

    @Test
    void theScreenContractsACycleIntoTheNodeItIsEnteredBy() throws Exception {
        var root = tree(threeCraftCycle().plan("dust", 4));

        var contracted = CraftingTreeCycleView.contract(root);

        assertEquals(1, contracted.cycles().size(), "one node stands for the cycle");
        var region = contracted.cycles().values().iterator().next();
        assertEquals(3, region.crafts());
        assertEquals(1, region.feedbacks());
        var shown = new ReferenceOpenHashSet<LiteCraftTreeNode>();
        walk(contracted.root(), shown::add, proc -> {
        });
        for (var node : shown) {
            assertTrue(node.feedback() < 0, "nothing inside the cycle is left in the tree");
            assertFalse(node.output().what().equals(key("b")) || node.output().what().equals(key("c")),
                "nor what it makes on the way: " + node.output());
        }
        var cycleNode = contracted.cycles().keySet().iterator().next();
        var under = new ArrayList<String>();
        for (var proc : cycleNode.inputs()) {
            for (var input : proc.inputs()) under.add(input.output().what().toString());
        }
        assertTrue(under.contains(key("fuel").toString()), "what it takes from outside hangs under it: " + under);
    }

    @Test
    void theRingShowsOneRoundWithTheInputFedBackLinked() throws Exception {
        var root = tree(threeCraftCycle().plan("dust", 4));
        var contracted = CraftingTreeCycleView.contract(root);
        var region = contracted.cycles().values().iterator().next();

        var ring = CraftingTreeCycleView.ring(region, new ReferenceOpenHashSet<>(), contracted.entries());

        assertEquals(key("dust"), ring.root().output().what(), "what it makes on top");
        assertEquals(1, ring.feedback().size());
        var fedBack = ring.feedback().keySet().iterator().next();
        var maker = ring.feedback().get(fedBack);
        assertFalse(maker.inputs().isEmpty());
        // The round runs down the first column: each first input leads on to the input fed back
        var node = ring.root();
        int crafts = 0;
        while (!node.inputs().isEmpty()) {
            node = node.inputs().getFirst().inputs().getFirst();
            crafts++;
        }
        assertEquals(fedBack, node, "the first column ends at the input fed back");
        assertEquals(3, crafts);
        assertTrue(ring.folds().isEmpty(), "short enough to show whole");
    }

    @Test
    void aLongRingIsFoldedAndUnfolds() throws Exception {
        var net = new CraftingTestNetwork();
        int length = 10;
        for (int i = 0; i < length; i++) {
            var outputs = i == 0 ? of("i0", 1, "i" + length, 1) : of("i" + i, 1);
            net.pattern("p" + i, of("i" + (i + 1), 1), outputs);
        }
        net.store("i" + length, 1);
        var root = tree(net.plan("i0", 1));
        var contracted = CraftingTreeCycleView.contract(root);
        assertEquals(1, contracted.cycles().size());
        var region = contracted.cycles().values().iterator().next();
        assertEquals(length, region.crafts());

        var unfolded = new ReferenceOpenHashSet<LiteCraftTreeNode>();
        var ring = CraftingTreeCycleView.ring(region, unfolded, contracted.entries());
        assertEquals(1, ring.folds().size(), "folded after a few crafts");
        var fold = ring.folds().values().iterator().next();
        assertEquals(length - CraftingTreeCycleView.SEGMENT, fold.crafts());
        assertTrue(ring.feedback().isEmpty(), "the way back is folded away with the rest");

        unfolded.add(fold.node());
        ring = CraftingTreeCycleView.ring(region, unfolded, contracted.entries());
        assertTrue(ring.folds().isEmpty());
        assertEquals(1, ring.feedback().size(), "and comes back unfolded");
    }

    @Test
    void anInputMadeByACycleOfItsOwnOpensThatCycle() throws Exception {
        // q <- p2q (fuel, p back) and fuel <- g2f <- g <- f2g (fuel back)
        var net = new CraftingTestNetwork();
        net.pattern("p2q", of("p", 1, "fuel", 1), of("q", 1, "p", 1));
        net.pattern("g2f", of("g", 1), of("fuel", 2));
        net.pattern("f2g", of("fuel", 1), of("g", 1));
        net.store("p", 1);
        net.store("fuel", 1);

        var root = tree(net.plan("q", 3));
        var contracted = CraftingTreeCycleView.contract(root);

        assertEquals(2, contracted.entries().size(), () -> "two cycles, found " + cycles(root));
        CraftingTreeCycleView.Region outer = null;
        for (var region : contracted.entries().values()) {
            if (region.entry().output().what().equals(key("q"))) outer = region;
        }
        assertNotNull(outer);
        var ring = CraftingTreeCycleView.ring(outer, new ReferenceOpenHashSet<>(), contracted.entries());
        assertEquals(1, ring.nested().size(), () -> "the fuel input of the outer ring opens the inner one\n"
            + describe(root, ""));
        assertEquals(key("fuel"), ring.nested().keySet().iterator().next().output().what());
    }

    @Test
    void aCycleWithoutNetOutputIsReportedWithItsPatterns() {
        var net = new CraftingTestNetwork();
        net.pattern("p2q", of("p", 1), of("q", 1));
        net.pattern("q2p", of("q", 1), of("p", 1));

        var failure = assertThrows(CraftingCalculationFailure.class, () -> net.plan("q", 3));

        assertEquals(PlayerMessages.CraftingNoNetOutput, failure.getMessageKey());
        var cycle = failure.getCycle();
        assertNotNull(cycle, "the cycle is told");
        assertEquals(cycle.items().getFirst(), cycle.items().getLast(), "around and back: " + cycle.items());
        assertTrue(cycle.items().contains(key("p")) && cycle.items().contains(key("q")), cycle.items().toString());
        assertEquals(cycle.items().size() - 1, cycle.patterns().size(), "a pattern between each two items");
        assertEquals(1 + cycle.patterns().size(), cycle.describe().size(), "the cycle, then each pattern");
    }

    @Test
    void theTreeOfACycleShowsTheCraftsAndMissingItemsOfThePlan() throws Exception {
        // One craft each of 4 stone -> 5 bricks and 1 brick -> 2 stone takes more stone than it makes, so the plan is
        // repaired after the calculation to the crafts that balance; the tree must show those, not one round.
        for (long[] stock : new long[][]{{0, 0}, {3, 0}, {4, 0}, {0, 10}, {64, 64}}) {
            var net = new CraftingTestNetwork();
            net.pattern("stone2brick", of("stone", 4), of("brick", 5));
            var brick2stone = net.pattern("brick2stone", of("brick", 1), of("stone", 2));
            if (stock[0] > 0) net.store("stone", stock[0]);
            if (stock[1] > 0) net.store("brick", stock[1]);
            String context = stock[0] + " stone and " + stock[1] + " bricks stored";

            var plan = net.plan("stone", 1000);
            var root = tree(plan);

            assertEquals(new Object2LongOpenHashMap<>(plan.patternTimes()), shownCrafts(root),
                () -> context + "\n" + describe(root, ""));
            var missing = shownMissing(root);
            assertEquals(names(plan.missingItems()), names(missing), () -> context + "\n" + describe(root, ""));
            assertEquals(0, root.missing(), () -> context + ": a missing seed is taken by the cycle, not the request");
            long stoneCrafted = 2 * plan.patternTimes().getLong(brick2stone);
            assertEquals(stoneCrafted - 1000, plan.intermediateFinalOutputAmount(),
                () -> context + ": the CPU keeps the crafted stone beyond the request for the brick crafts");
            long stoneTaken = 4 * plan.patternTimes().getLong(net.patterns.getFirst());
            assertTrue(stoneCrafted + stock[0] + missing.get(key("stone")) >= 1000 + stoneTaken,
                context);
        }
    }
}
