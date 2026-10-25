package ae2.crafting;

import ae2.api.networking.crafting.ICraftingPlan;
import ae2.core.AEConfig;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.function.Supplier;

import static ae2.crafting.CraftingTestNetwork.crafts;
import static ae2.crafting.CraftingTestNetwork.names;
import static ae2.crafting.CraftingTestNetwork.of;
import static ae2.crafting.CraftingTestNetwork.replay;
import static ae2.crafting.CraftingTestNetwork.total;
import static ae2.crafting.CraftingTestNetwork.used;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How long the crafting calculation takes. The bounds are generous, several times what a desktop machine needs, so
 * they only fail on work that grows with the requested amount or with the square of the network.
 */
class CraftingCalculationPerformanceTest {

    private static long nanos(Runnable run) {
        long start = System.nanoTime();
        run.run();
        return System.nanoTime() - start;
    }

    private static <T> T timed(String label, long[] out, Supplier<T> supplier) {
        long start = System.nanoTime();
        var result = supplier.get();
        out[0] = System.nanoTime() - start;
        System.out.printf("%s: %.1f ms%n", label, out[0] / 1e6);
        return result;
    }

    private static void assertRuns(ICraftingPlan plan) {
        assertNull(replay(plan), () -> "plan does not run: crafts " + plan.patternTimes() + ", missing "
            + names(plan.missingItems()));
    }

    private static long ms(long nanos) {
        return nanos / 1_000_000;
    }

    private static CraftingTestNetwork catalystNetwork() {
        var net = new CraftingTestNetwork();
        net.pattern("cat", of("a", 1, "catalyst", 1), of("b", 1, "catalyst", 1));
        net.store("a", Long.MAX_VALUE / 4);
        net.store("catalyst", 1);
        return net;
    }

    private static CraftingTestNetwork containerNetwork() {
        var net = new CraftingTestNetwork();
        net.container("water_bucket", "bucket");
        net.pattern("plate", of("ingot", 1, "water_bucket", 1), of("plate", 1));
        net.pattern("fill", of("bucket", 1, "water", 1000), of("water_bucket", 1));
        net.store("ingot", Long.MAX_VALUE / 4);
        net.store("water", Long.MAX_VALUE / 4);
        net.store("bucket", 1);
        return net;
    }

    private static CraftingTestNetwork cycleNetwork() {
        var net = new CraftingTestNetwork();
        net.pattern("x2y", of("x", 1), of("y", 2));
        net.pattern("y2xz", of("y", 1), of("z", 1, "x", 1));
        net.store("x", 1);
        return net;
    }

    // The batch is what one craft needs plus what the later ones do not get back, not one craft at a time
    @Test
    void catalystCostDoesNotGrowWithTheAmount() {
        catalystNetwork().plan("b", 1_000);
        var small = new long[1];
        var large = new long[1];
        timed("catalyst x1000", small, () -> catalystNetwork().plan("b", 1_000));
        var plan = timed("catalyst x100000000", large, () -> catalystNetwork().plan("b", 100_000_000));

        assertEquals(1, used(plan, "catalyst"));
        assertTrue(ms(large[0]) < 2_000, () -> "took " + ms(large[0]) + " ms");
    }

    @Test
    void containerCostDoesNotGrowWithTheAmount() {
        containerNetwork().plan("plate", 1_000);
        var large = new long[1];
        var plan = timed("container x100000000", large, () -> containerNetwork().plan("plate", 100_000_000));

        assertEquals(1, used(plan, "bucket"));
        assertEquals(0, total(plan.missingItems()));
        assertTrue(ms(large[0]) < 2_000, () -> "took " + ms(large[0]) + " ms");
    }

    @Test
    void cycleCostDoesNotGrowWithTheAmount() {
        cycleNetwork().plan("z", 1_000);
        var large = new long[1];
        var plan = timed("cycle x100000000", large, () -> cycleNetwork().plan("z", 100_000_000));

        assertEquals(1, used(plan, "x"));
        assertTrue(ms(large[0]) < 2_000, () -> "took " + ms(large[0]) + " ms");
    }

    private static long craftingAllocatedBytes() {
        if (!(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean threads)
            || !threads.isThreadAllocatedMemorySupported()) return 0;
        if (!threads.isThreadAllocatedMemoryEnabled()) threads.setThreadAllocatedMemoryEnabled(true);
        long bytes = 0, driver = Thread.currentThread().threadId();
        for (var thread : threads.getThreadInfo(threads.getAllThreadIds())) {
            if (thread != null && (thread.getThreadId() == driver || thread.getThreadName().equals("AE Crafting Calculator"))) {
                bytes += Math.max(0, threads.getThreadAllocatedBytes(thread.getThreadId()));
            }
        }
        return bytes;
    }

    @Test
    void amplificationFeedingAConsumerDoesNotIterateOverBillionsOfCrafts() {
        var net = new CraftingTestNetwork();
        net.pattern("grow", of("stone", 1), of("stone", 2));
        net.pattern("bricks", of("stone", 3), of("bricks", 4));
        net.store("stone", 1);
        net.plan("bricks", 1000);
        var elapsed = new long[1];
        long allocated = craftingAllocatedBytes();
        var plan = timed("amplification x4294967296", elapsed, () -> net.plan("bricks", 4_294_967_296L));
        allocated = craftingAllocatedBytes() - allocated;
        System.out.printf("amplification x4294967296 allocated: %.3f MiB%n", allocated / 1048576.0);
        assertEquals(0, total(plan.missingItems()));
        assertEquals(1, used(plan, "stone"));
        assertEquals(2, plan.patternTimes().size(), "keep aggregate tasks, not a schedule per craft");
        assertTrue(ms(elapsed[0]) < 2000, () -> "took " + ms(elapsed[0]) + " ms");
        assertTrue(allocated < 16 * 1024 * 1024, "tiny recipe graph allocated " + allocated + " bytes");
    }

    /**
     * Layers of items, each made from 2 to 4 items of the layer below, the bottom one stored, and a root item made from
     * the whole top layer.
     */
    static CraftingTestNetwork layered(long seed, int layers, int width, boolean alternatives, long stock) {
        var random = new Random(seed);
        var net = new CraftingTestNetwork();
        String prefix = "s" + seed + "w" + width + (alternatives ? "a" : "") + "_";
        for (int i = 0; i < width; i++) {
            net.store(prefix + "L0_" + i, stock);
        }
        for (int layer = 1; layer <= layers; layer++) {
            for (int i = 0; i < width; i++) {
                String item = prefix + "L" + layer + "_" + i;
                for (int v = 0; v < (alternatives ? 2 : 1); v++) {
                    int count = 2 + random.nextInt(3);
                    var in = new Object[count * 2];
                    var used = new HashSet<Integer>();
                    for (int c = 0; c < count; c++) {
                        int from;
                        do {
                            from = random.nextInt(width);
                        } while (!used.add(from));
                        in[c * 2] = prefix + "L" + (layer - 1) + "_" + from;
                        in[c * 2 + 1] = 1 + random.nextInt(4);
                    }
                    net.pattern(item + (v == 0 ? "" : "#alt"), 10 - v, in, of(item, 1 + random.nextInt(3)));
                }
            }
        }
        var root = new Object[width * 2];
        for (int i = 0; i < width; i++) {
            root[i * 2] = prefix + "L" + layers + "_" + i;
            root[i * 2 + 1] = 1;
        }
        net.pattern(prefix + "root", root, of(prefix + "root", 1));
        return net;
    }

    static String root(long seed, int width, boolean alternatives) {
        return "s" + seed + "w" + width + (alternatives ? "a" : "") + "_root";
    }

    private static long median(long[] values) {
        var copy = values.clone();
        Arrays.sort(copy);
        return copy[copy.length / 2];
    }

    // 3000 patterns over 10 layers
    @Test
    void largeNetwork() {
        layered(1, 10, 300, false, 1_000_000_000_000L).plan(root(1, 300, false), 10);
        var misses = new long[5];
        for (int i = 0; i < misses.length; i++) {
            long seed = 2 + i;
            var net = layered(seed, 10, 300, false, 1_000_000_000_000L);
            var plan = new ICraftingPlan[1];
            misses[i] = nanos(() -> plan[0] = net.plan(root(seed, 300, false), 10));
            assertRuns(plan[0]);
        }
        var net = layered(9, 10, 300, false, 1_000_000_000_000L);
        net.plan(root(9, 300, false), 10);
        var hits = new long[5];
        for (int i = 0; i < hits.length; i++) {
            int amount = 11 + i;
            hits[i] = nanos(() -> net.plan(root(9, 300, false), amount));
        }
        System.out.printf("3000 patterns: cache miss median %.1f ms, cache hit median %.1f ms%n",
            median(misses) / 1e6, median(hits) / 1e6);

        assertTrue(ms(median(misses)) < 1_500, () -> "cache miss took " + ms(median(misses)) + " ms");
        assertTrue(ms(median(hits)) < 750, () -> "cache hit took " + ms(median(hits)) + " ms");
    }

    // Two patterns per item and far too little stock: every key of the graph has alternatives to try
    @Test
    void largeNetworkWithAlternativesAndShortage() {
        var net = layered(21, 10, 150, true, 1_000);
        var time = new long[1];
        var plan = timed("3000 patterns with alternatives and shortage", time, () -> net.plan(root(21, 150, true), 100));

        assertTrue(total(plan.missingItems()) > 0);
        assertRuns(plan);
        assertTrue(ms(time[0]) < 5_000, () -> "took " + ms(time[0]) + " ms");
    }

    /**
     * Patterns with byproducts are planned by the legacy tree, which applies one simulation diff per craft branch. The
     * time grows with the size of the network, not with its square.
     */
    @Test
    void legacyTreeGrowsLinearly() {
        legacyNetwork(30, 40).plan("lroot30", 1);
        legacyNetwork(30, 160).plan("lroot30", 1);
        var small = new long[3];
        var large = new long[3];
        for (int i = 0; i < small.length; i++) {
            var smallNetwork = legacyNetwork(31, 40);
            var largeNetwork = legacyNetwork(32, 160);
            var plans = new ICraftingPlan[2];
            small[i] = nanos(() -> plans[0] = smallNetwork.plan("lroot31", 1));
            large[i] = nanos(() -> plans[1] = largeNetwork.plan("lroot32", 1));
            for (var plan : plans) {
                assertEquals(0, total(plan.missingItems()), () -> "missing " + names(plan.missingItems()));
                assertRuns(plan);
            }
        }
        System.out.printf("legacy tree: 40/layer median %.1f ms, 160/layer median %.1f ms%n",
            median(small) / 1e6, median(large) / 1e6);
        assertTrue(median(large) < Math.max(median(small), 20_000_000L) * 10,
            () -> "4x the patterns took " + (double) median(large) / median(small) + "x the time");
    }

    @Test
    void legacyTreeWithoutMemoCacheGrowsLinearly() {
        int previous = AEConfig.CRAFTING.memoizationCacheSize;
        AEConfig.CRAFTING.memoizationCacheSize = 0;
        try {
            legacyTreeGrowsLinearly();
        } finally {
            AEConfig.CRAFTING.memoizationCacheSize = previous;
        }
    }

    private static CraftingTestNetwork legacyNetwork(int seed, int width) {
        var random = new Random(seed);
        var net = new CraftingTestNetwork();
        String prefix = "l" + seed + "_";
        for (int i = 0; i < width; i++) {
            net.store(prefix + "0_" + i, 1_000_000_000L);
        }
        for (int layer = 1; layer <= 6; layer++) {
            for (int i = 0; i < width; i++) {
                var in = of(prefix + (layer - 1) + "_" + random.nextInt(width), 1 + random.nextInt(2),
                    prefix + (layer - 1) + "_" + random.nextInt(width) + "|" + prefix + "0_" + i, 1);
                net.pattern(prefix + layer + "_" + i, in, of(prefix + layer + "_" + i, 1, prefix + "slag", 1));
            }
        }
        var root = new Object[width * 2];
        for (int i = 0; i < width; i++) {
            root[i * 2] = prefix + "6_" + i;
            root[i * 2 + 1] = 1;
        }
        net.pattern("lroot" + seed, root, of("lroot" + seed, 1));
        return net;
    }

    @Test
    void alternativesPlanAsExpected() {
        var net = new CraftingTestNetwork();
        var iron = net.pattern("rod", 10, of("iron", 1), of("rod", 3));
        var steel = net.pattern("rod#steel", 5, of("steel", 1), of("rod", 3));
        net.store("iron", 3);
        net.store("steel", 10);

        var plan = net.plan("rod", 10);

        assertEquals(3, crafts(plan, iron));
        assertEquals(1, crafts(plan, steel));
    }
}
