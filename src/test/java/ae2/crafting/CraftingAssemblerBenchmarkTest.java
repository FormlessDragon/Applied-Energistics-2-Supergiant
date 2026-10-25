package ae2.crafting;

import net.minecraft.init.Items;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Locale;

import static ae2.crafting.CraftingRemainderFixtures.Fixture;
import static ae2.crafting.CraftingRemainderFixtures.Slot;
import static ae2.crafting.CraftingRemainderFixtures.damage;
import static ae2.crafting.CraftingRemainderFixtures.exact;
import static ae2.crafting.CraftingRemainderFixtures.item;
import static ae2.crafting.CraftingRemainderFixtures.remaining;
import static ae2.crafting.CraftingRemainderFixtures.tool;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Measures full-grid choice search with many simultaneously available, reusable tool variants.
 */
@Tag("crafting-benchmark")
class CraftingAssemblerBenchmarkTest extends CraftingRecipeTestSupport {
    @ParameterizedTest(name = "reusable tool variants={0}")
    @ValueSource(ints = {8, 64})
    void repeatedGridChoices(int variants) {
        memo(0);
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var carrot = item(Items.CARROT);
        var rule = f.rule("many_reusable_variants", carrot, true,
            new Slot[]{exact(apple), tool(), tool()}, grid -> {
                var result = remaining(grid);
                result.set(1, grid.getStackInSlot(1).copy());
                result.set(2, grid.getStackInSlot(2).copy());
                return result;
            });
        f.stock(apple, 32);
        for (int i = 0; i < variants; i++) f.stock(damage(i), 2);
        var elapsed = new long[5];
        var allocated = new long[5];
        var callbacks = new int[5];
        for (int sample = -3; sample < elapsed.length; sample++) {
            rule.callbacks.set(0);
            long bytes = CraftingRealPatternPlanningTest.craftingAllocatedBytes();
            long start = System.nanoTime();
            var plan = f.net.plan(carrot, 32);
            long nanos = System.nanoTime() - start;
            bytes = CraftingRealPatternPlanningTest.craftingAllocatedBytes() - bytes;
            if (sample >= 0) {
                elapsed[sample] = nanos;
                allocated[sample] = bytes;
                callbacks[sample] = rule.callbacks.get();
            }
            complete(f.net, plan, carrot, 32);
            materials(plan.usedItems(), apple, 32, damage(0), 2);
            assertEquals(32, plan.patternTimes().getLong(rule.pattern));
        }
        Arrays.sort(elapsed);
        Arrays.sort(allocated);
        Arrays.sort(callbacks);
        System.out.printf(Locale.ROOT, "assembler variants=%d crafts=32 median=%.3fms allocated=%.3fMiB callbacks=%d%n",
            variants, elapsed[2] / 1e6, allocated[2] / 1048576.0, callbacks[2]);
    }
}
