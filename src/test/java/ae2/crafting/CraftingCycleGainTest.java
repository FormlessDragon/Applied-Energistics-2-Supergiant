package ae2.crafting;

import ae2.api.networking.crafting.ICraftingPlan;
import ae2.core.localization.PlayerMessages;
import org.junit.jupiter.api.Test;

import static ae2.crafting.CraftingTestNetwork.crafts;
import static ae2.crafting.CraftingTestNetwork.key;
import static ae2.crafting.CraftingTestNetwork.missing;
import static ae2.crafting.CraftingTestNetwork.names;
import static ae2.crafting.CraftingTestNetwork.of;
import static ae2.crafting.CraftingTestNetwork.replay;
import static ae2.crafting.CraftingTestNetwork.total;
import static ae2.crafting.CraftingTestNetwork.used;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cycles whose gain only shows when their crafts run in the ratio they feed each other: one craft of each takes more of
 * the requested item than it makes, as with 4 stone -> 5 bricks and 1 brick -> 2 stone, where one craft each takes 4
 * stone to make 2 although 4 stone become 10 over 5 brick crafts.
 */
class CraftingCycleGainTest {

    private static final long[] STOCKS = {0, 3, 4, 10, 64};
    private static final long[] AMOUNTS = {1, 10, 100, 1000};

    private static CraftingTestNetwork stoneBrickCycle(long stoneFromBrick) {
        var net = new CraftingTestNetwork();
        net.pattern("stone2brick", of("stone", 4), of("brick", 5));
        net.pattern("brick2stone", of("brick", stoneFromBrick), of("stone", 2 * stoneFromBrick));
        return net;
    }

    private static void stock(CraftingTestNetwork net, long stone, long brick) {
        if (stone > 0) net.store("stone", stone);
        if (brick > 0) net.store("brick", brick);
    }

    private static void assertRuns(ICraftingPlan plan, String context) {
        assertNull(replay(plan), () -> context + ": plan does not run: crafts " + plan.patternTimes() + ", used "
            + names(plan.usedItems()) + ", missing " + names(plan.missingItems()));
    }

    /**
     * Without enough stock the plan only lacks what starts the cycle: what the stored stone lacks of the 4 a brick
     * craft takes, or the bricks for a stone craft. Which of the two is picked is up to the planner.
     */
    private static void assertOnlySeedIsMissing(ICraftingPlan plan, long storedStone, String context) {
        long stone = missing(plan, "stone");
        long brick = missing(plan, "brick");
        assertEquals(stone + brick, total(plan.missingItems()), () -> context + ": only a seed is missing");
        assertTrue(stone > 0 && stone <= 4 - storedStone && brick == 0 || stone == 0 && brick > 0 && brick <= 2,
            () -> context + ": a seed of what the stored stone lacks of 4, or up to 2 bricks, is missing");
    }

    @Test
    void stoneIsCraftedFromAnyStock() {
        for (long stone : STOCKS) {
            for (long brick : STOCKS) {
                for (long amount : AMOUNTS) {
                    var net = stoneBrickCycle(1);
                    stock(net, stone, brick);
                    String context = amount + " stone from " + stone + " stone and " + brick + " bricks";

                    var plan = net.plan("stone", amount);

                    assertRuns(plan, context);
                    String result = context + ", missing " + names(plan.missingItems());
                    if (stone < 4 && brick == 0) {
                        assertOnlySeedIsMissing(plan, stone, result);
                    } else {
                        assertEquals(0, total(plan.missingItems()), result);
                    }
                }
            }
        }
    }

    @Test
    void stoneIsCraftedWithoutStockFromASeed() {
        var net = stoneBrickCycle(1);
        var plan = net.plan("stone", 100);

        assertRuns(plan, "no stock");
        assertOnlySeedIsMissing(plan, 0, "no stock");
        assertTrue(crafts(plan, net.patterns.get(0)) > 0, "bricks are made from stone");
        assertTrue(crafts(plan, net.patterns.get(1)) > 0, "stone is made from bricks");
    }

    @Test
    void stoneIsCraftedFromStoredBricksAlone() {
        var net = stoneBrickCycle(1);
        net.store("brick", 10);

        var plan = net.plan("stone", 100);

        assertRuns(plan, "10 bricks");
        assertEquals(0, total(plan.missingItems()));
        assertEquals(0, used(plan, "stone"));
        assertTrue(used(plan, "brick") > 0);
    }

    @Test
    void bricksAreStillCraftedFromAnyStock() {
        for (long stone : STOCKS) {
            for (long brick : STOCKS) {
                for (long amount : AMOUNTS) {
                    var net = stoneBrickCycle(1);
                    stock(net, stone, brick);

                    assertRuns(net.plan("brick", amount),
                        amount + " bricks from " + stone + " stone and " + brick + " bricks");
                }
            }
        }
    }

    @Test
    void aStonePatternMakingAWholeRoundStillPlans() {
        // The workaround of the bug: 5 bricks -> 10 stone already gains in one craft each.
        var net = stoneBrickCycle(5);
        net.store("brick", 5);

        var plan = net.plan("stone", 100);

        assertRuns(plan, "5 bricks -> 10 stone");
        assertEquals(0, total(plan.missingItems()));
    }

    @Test
    void aLongerCycleGainsOverSeveralCrafts() {
        // 3 x -> 2 y -> 2 z -> 4 x: one craft each takes 3 x to make 2, but 3 x become 4.
        var net = new CraftingTestNetwork();
        net.pattern("x2y", of("x", 3), of("y", 2));
        net.pattern("y2z", of("y", 1), of("z", 1));
        net.pattern("z2x", of("z", 1), of("x", 2));
        net.store("x", 3);

        var plan = net.plan("x", 50);

        assertRuns(plan, "x -> y -> z -> x");
        assertEquals(0, total(plan.missingItems()));
    }

    @Test
    void aCycleWithAnotherInputGainsOverSeveralCrafts() {
        var net = new CraftingTestNetwork();
        net.pattern("stone2brick", of("stone", 4, "coal", 1), of("brick", 5));
        net.pattern("brick2stone", of("brick", 1), of("stone", 2));
        net.store("stone", 4);
        net.store("coal", 100);

        var plan = net.plan("stone", 40);

        assertRuns(plan, "with coal");
        assertEquals(0, total(plan.missingItems()), () -> "missing " + names(plan.missingItems()));
        assertEquals(4, used(plan, "stone"), "the stored stone starts the cycle");
        assertTrue(used(plan, "coal") > 0, "each brick craft burns coal");
    }

    @Test
    void aCycleLosingItemsStillHasNoNetOutput() {
        // 4 stone -> 3 bricks -> 3 stone: no ratio of crafts gains stone.
        var net = new CraftingTestNetwork();
        net.pattern("stone2brick", of("stone", 4), of("brick", 3));
        net.pattern("brick2stone", of("brick", 1), of("stone", 1));

        var failure = assertThrows(CraftingCalculationFailure.class, () -> net.plan("stone", 10));

        assertEquals(PlayerMessages.CraftingNoNetOutput, failure.getMessageKey());
        assertNotNull(failure.getCycle());
        assertTrue(failure.getCycle().items().contains(key("brick")), failure.getCycle().items().toString());
    }

    @Test
    void aCycleBreakingEvenStillHasNoNetOutput() {
        // 2 stone -> 1 brick -> 2 stone gives back exactly what it takes in any ratio.
        var net = new CraftingTestNetwork();
        net.pattern("stone2brick", of("stone", 2), of("brick", 1));
        net.pattern("brick2stone", of("brick", 1), of("stone", 2));

        var failure = assertThrows(CraftingCalculationFailure.class, () -> net.plan("stone", 10));

        assertEquals(PlayerMessages.CraftingNoNetOutput, failure.getMessageKey());
    }
}
