package ae2.crafting;

import ae2.core.AEConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static ae2.crafting.CraftingTestNetwork.crafts;
import static ae2.crafting.CraftingTestNetwork.of;
import static ae2.crafting.CraftingTestNetwork.replay;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CraftingPatternPriorityTest extends CraftingPlanningTestSupport {
    private int previousFallbackSeconds;

    @BeforeEach
    void saveFallback() {
        previousFallbackSeconds = AEConfig.CRAFTING.craftingCalculationFallbackSeconds;
    }

    @AfterEach
    void restoreFallback() {
        AEConfig.CRAFTING.craftingCalculationFallbackSeconds = previousFallbackSeconds;
    }

    @ParameterizedTest
    @CsvSource({"0,0", "0,5", "10000,0", "10000,5"})
    void shortagesKeepPriorityAndConsumeEveryAvailableAlternative(int cache, int fallback) {
        memo(cache);
        AEConfig.CRAFTING.craftingCalculationFallbackSeconds = fallback;
        var net = new CraftingTestNetwork();
        var preferred = net.pattern("preferred", 10, of("gold", 2), of("result", 1));
        var alternative = net.pattern("alternative", 5, of("copper", 1), of("result", 1));
        net.store("gold", 2);
        net.store("copper", 1);

        for (int attempt = 0; attempt < 2; attempt++) {
            var plan = net.plan("result", 4);

            materials(plan.usedItems(), "gold", 2, "copper", 1);
            materials(plan.missingItems(), "gold", 4);
            assertEquals(3, crafts(plan, preferred));
            assertEquals(1, crafts(plan, alternative));
            assertNull(replay(plan));
        }
    }

    @ParameterizedTest
    @CsvSource({"0,0", "0,5", "10000,0", "10000,5"})
    void availablePatternsAreExhaustedInPriorityOrder(int cache, int fallback) {
        memo(cache);
        AEConfig.CRAFTING.craftingCalculationFallbackSeconds = fallback;
        var net = new CraftingTestNetwork();
        var preferred = net.pattern("preferred", 30, of("gold", 1), of("result", 1));
        var second = net.pattern("second", 20, of("copper", 1), of("result", 1));
        var third = net.pattern("third", 10, of("iron", 1), of("result", 1));
        net.store("gold", 1);
        net.store("copper", 2);
        net.store("iron", 10);

        var plan = net.plan("result", 4);

        complete(net, plan, "result", 4);
        materials(plan.usedItems(), "gold", 1, "copper", 2, "iron", 1);
        assertEquals(1, crafts(plan, preferred));
        assertEquals(2, crafts(plan, second));
        assertEquals(1, crafts(plan, third));
        assertNull(replay(plan));
    }

    @ParameterizedTest
    @CsvSource({"0,0", "0,5", "10000,0", "10000,5"})
    void missingMaterialsFollowTheCurrentPreferredPatternsSubtree(int cache, int fallback) {
        memo(cache);
        AEConfig.CRAFTING.craftingCalculationFallbackSeconds = fallback;
        var net = new CraftingTestNetwork();
        net.pattern("refine", of("ore", 3), of("gold", 1));
        var preferred = net.pattern("preferred", 10, of("gold", 2), of("result", 1));
        var alternative = net.pattern("alternative", 5, of("copper", 1), of("result", 1));
        net.store("ore", 6);
        net.store("copper", 1);

        var first = net.plan("result", 4);
        materials(first.usedItems(), "ore", 6, "copper", 1);
        materials(first.missingItems(), "ore", 12);
        assertEquals(3, crafts(first, preferred));
        assertEquals(1, crafts(first, alternative));
        assertNull(replay(first));

        net.priority(alternative, 20);
        var reprioritized = net.plan("result", 4);
        materials(reprioritized.usedItems(), "ore", 6, "copper", 1);
        materials(reprioritized.missingItems(), "copper", 2);
        assertEquals(1, crafts(reprioritized, preferred));
        assertEquals(3, crafts(reprioritized, alternative));
        assertNull(replay(reprioritized));
    }
}
