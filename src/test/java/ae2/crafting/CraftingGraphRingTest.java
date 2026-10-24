package ae2.crafting;

import ae2.api.networking.crafting.ICraftingPlan;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.junit.jupiter.api.Test;

import static ae2.crafting.CraftingTestNetwork.missing;
import static ae2.crafting.CraftingTestNetwork.names;
import static ae2.crafting.CraftingTestNetwork.of;
import static ae2.crafting.CraftingTestNetwork.replay;
import static ae2.crafting.CraftingTestNetwork.total;
import static ae2.crafting.CraftingTestNetwork.used;
import static ae2.crafting.CraftingTreeCyclesTest.describe;
import static ae2.crafting.CraftingTreeCyclesTest.shownCrafts;
import static ae2.crafting.CraftingTreeCyclesTest.shownMissing;
import static ae2.crafting.CraftingTreeCyclesTest.tree;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rings the crafting graph plans natively, see DemandPropagation#solveRing: each pattern of the cycle takes one item
 * made in it, so the crafts follow from the amounts the patterns make and take, without the legacy tree and the repair
 * of its plan after the calculation.
 */
class CraftingGraphRingTest {

    private static final long[][] STOCKS = {{0, 0}, {3, 0}, {4, 0}, {0, 10}, {64, 64}};

    private static CraftingTestNetwork stoneBrickRing(long stone, long brick) {
        var net = new CraftingTestNetwork();
        net.pattern("stone2brick", of("stone", 4), of("brick", 5));
        net.pattern("brick2stone", of("brick", 1), of("stone", 2));
        if (stone > 0) net.store("stone", stone);
        if (brick > 0) net.store("brick", brick);
        return net;
    }

    /**
     * The graph planned the ring and its plan ran as it was: a plan repaired after the calculation keeps the crafts
     * the graph shows, so they would differ from its own.
     */
    private static void assertPlannedNatively(ICraftingPlan plan, String context) throws Exception {
        var craftingPlan = (CraftingPlan) plan;
        assertTrue(craftingPlan.graphDisplayBuilder() != null, () -> context + ": planned by the graph");
        assertTrue(craftingPlan.attemptMetrics().nativeSccs() > 0, () -> context + ": the ring solved natively");
        assertEquals(0, craftingPlan.attemptMetrics().localSccs(), context);
        assertNull(replay(plan), () -> context + ": plan does not run: crafts " + plan.patternTimes() + ", used "
            + names(plan.usedItems()) + ", missing " + names(plan.missingItems()));
        var root = tree(plan);
        assertEquals(new Object2LongOpenHashMap<>(plan.patternTimes()), shownCrafts(root),
            () -> context + ": the plan is the graph's\n" + describe(root, ""));
        assertEquals(names(plan.missingItems()), names(shownMissing(root)), () -> context + "\n" + describe(root, ""));
    }

    @Test
    void theRequestedItemIsCraftedAroundTheRing() throws Exception {
        for (long[] stock : STOCKS) {
            var net = stoneBrickRing(stock[0], stock[1]);
            String context = stock[0] + " stone and " + stock[1] + " bricks stored";

            var plan = net.plan("stone", 1000);

            assertPlannedNatively(plan, context);
            long bricksToStone = plan.patternTimes().getLong(net.patterns.get(1));
            long stoneToBricks = plan.patternTimes().getLong(net.patterns.get(0));
            // Bricks for the stone crafts: made, stored or missing
            assertTrue(5 * stoneToBricks + used(plan, "brick") + missing(plan, "brick") >= bricksToStone, context);
            assertEquals(2 * bricksToStone - 1000, plan.intermediateFinalOutputAmount(),
                () -> context + ": the CPU keeps the crafted stone beyond the request");
        }
    }

    @Test
    void withoutStockOnlyTheSeedOfOneCraftIsMissing() throws Exception {
        var plan = stoneBrickRing(0, 0).plan("stone", 1000);

        assertPlannedNatively(plan, "no stock");
        assertEquals(4, total(plan.missingItems()), names(plan.missingItems()).toString());
        assertEquals(4, missing(plan, "stone"));
    }

    @Test
    void storedStoneShortOfACraftIsUsedAndOnlyTheRestIsMissing() throws Exception {
        var plan = stoneBrickRing(3, 0).plan("stone", 1000);

        assertPlannedNatively(plan, "3 stone");
        assertEquals(3, used(plan, "stone"));
        assertEquals(1, missing(plan, "stone"));
        assertEquals(1, total(plan.missingItems()));
    }

    @Test
    void storedStoneTakenToStartTheRingIsCraftedBack() throws Exception {
        var net = stoneBrickRing(4, 0);
        var plan = net.plan("stone", 100);

        assertPlannedNatively(plan, "4 stone");
        assertEquals(4, used(plan, "stone"));
        long stoneCrafted = 2 * plan.patternTimes().getLong(net.patterns.get(1));
        long stoneTaken = 4 * plan.patternTimes().getLong(net.patterns.get(0));
        // What the stone crafts make and the 4 stored cover the request, the brick crafts and the 4 kept in reserve
        assertTrue(stoneCrafted + 4 >= 100 + stoneTaken + 4,
            () -> "crafts " + plan.patternTimes());
    }

    @Test
    void storedBricksStartTheRingWithoutStone() throws Exception {
        var plan = stoneBrickRing(0, 10).plan("stone", 1000);

        assertPlannedNatively(plan, "10 bricks");
        assertEquals(0, total(plan.missingItems()));
        assertEquals(0, used(plan, "stone"));
    }

    @Test
    void aRingBelowTheRequestFeedsItsCrafts() throws Exception {
        for (var wallInput : new String[]{"brick", "stone"}) {
            var net = stoneBrickRing(0, 10);
            net.pattern("wall", of(wallInput, 1), of("wall", 1));

            var plan = net.plan("wall", 300);

            assertPlannedNatively(plan, "wall of " + wallInput);
            assertEquals(300, plan.patternTimes().getLong(net.patterns.get(2)));
            assertEquals(0, total(plan.missingItems()));
            assertEquals(0, plan.intermediateFinalOutputAmount(), "the wall is not taken back");
        }
    }

    @Test
    void aRingStarvedByCraftsOutsideItIsLeftToTheLegacyTree() throws Exception {
        // The CPU runs any craft whose inputs are there, so the walls take the stone the brick crafts wait for
        var net = stoneBrickRing(0, 0);
        net.pattern("wall", of("stone", 2, "brick", 1), of("wall", 1));

        var plan = net.plan("wall", 300);

        assertNull(((CraftingPlan) plan).graphDisplayBuilder(), "planned by the legacy tree");
        assertNull(replay(plan));
        var root = tree(plan);
        assertEquals(new Object2LongOpenHashMap<>(plan.patternTimes()), shownCrafts(root), () -> describe(root, ""));
        assertEquals(names(plan.missingItems()), names(shownMissing(root)), () -> describe(root, ""));
    }

    @Test
    void aLongerRingIsPlannedNatively() throws Exception {
        // 3 x -> 2 y -> 2 z -> 4 x
        var net = new CraftingTestNetwork();
        net.pattern("x2y", of("x", 3), of("y", 2));
        net.pattern("y2z", of("y", 1), of("z", 1));
        net.pattern("z2x", of("z", 1), of("x", 2));
        net.store("x", 3);

        var plan = net.plan("x", 500);

        assertPlannedNatively(plan, "x -> y -> z -> x");
        assertEquals(0, total(plan.missingItems()));
    }

    @Test
    void aRingWithAnotherInputIsPlannedNatively() throws Exception {
        var net = new CraftingTestNetwork();
        net.pattern("stone2brick", of("stone", 4, "coal", 1), of("brick", 5));
        net.pattern("brick2stone", of("brick", 1), of("stone", 2));
        net.pattern("coal", of("log", 1), of("coal", 2));
        net.store("stone", 4);
        net.store("log", 1000);

        var plan = net.plan("stone", 400);

        assertPlannedNatively(plan, "with coal");
        assertEquals(0, total(plan.missingItems()));
        long brickCrafts = plan.patternTimes().getLong(net.patterns.get(0));
        assertTrue(2 * plan.patternTimes().getLong(net.patterns.get(2)) >= brickCrafts, "coal for each brick craft");
    }

    @Test
    void planningAgainOnTheCachedGraphGivesTheSamePlan() throws Exception {
        var net = stoneBrickRing(4, 0);

        var first = net.plan("stone", 1000);
        var second = net.plan("stone", 1000);

        assertPlannedNatively(second, "cached graph");
        assertEquals(new Object2LongOpenHashMap<>(first.patternTimes()),
            new Object2LongOpenHashMap<>(second.patternTimes()));
        assertEquals(names(first.missingItems()), names(second.missingItems()));
    }
}
