package ae2.me.service;

import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.util.DimensionalBlockPos;
import ae2.core.AEConfig;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.util.Deque;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngredientFlowSnapshotTest {
    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
        AEConfig.init();
    }


    private static Object addBucket(IngredientFlowService service, AEKey key, long in, long out) throws Exception {
        return addBucket(service, key, in, out, 0L);
    }

    @SuppressWarnings("unchecked")
    private static Object addBucket(IngredientFlowService service, AEKey key, long in, long out, long start) throws Exception {
        var bucketClass = Class.forName(IngredientFlowService.class.getName() + "$Bucket");
        var bucketConstructor = bucketClass.getDeclaredConstructor(long.class);
        bucketConstructor.setAccessible(true);
        var bucket = bucketConstructor.newInstance(start);
        var accumulatorClass = Class.forName(IngredientFlowService.class.getName() + "$ItemFlowAccumulator");
        var accumulatorConstructor = accumulatorClass.getDeclaredConstructor();
        accumulatorConstructor.setAccessible(true);
        var accumulator = accumulatorConstructor.newInstance();
        for (var name : new String[]{"in", "out"}) {
            var field = accumulatorClass.getDeclaredField(name);
            field.setAccessible(true);
            field.setLong(accumulator, name.equals("in") ? in : out);
        }
        var byKey = bucketClass.getDeclaredField("byKey");
        byKey.setAccessible(true);
        ((Map<AEKey, Object>) byKey.get(bucket)).put(key, accumulator);
        var buckets = IngredientFlowService.class.getDeclaredField("buckets");
        buckets.setAccessible(true);
        ((Deque<Object>) buckets.get(service)).add(bucket);
        return accumulator;
    }

    @SuppressWarnings("unchecked")
    private static void addLocation(Object accumulator, DimensionalBlockPos location, long in, long out) throws Exception {
        var flowClass = Class.forName(IngredientFlowService.class.getName() + "$LocationFlow");
        var constructor = flowClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        var flow = constructor.newInstance();
        var inField = flowClass.getDeclaredField("in");
        var outField = flowClass.getDeclaredField("out");
        inField.setAccessible(true);
        outField.setAccessible(true);
        inField.setLong(flow, in);
        outField.setLong(flow, out);
        var locations = accumulator.getClass().getDeclaredField("byLocation");
        locations.setAccessible(true);
        ((Map<DimensionalBlockPos, Object>) locations.get(accumulator)).put(location, flow);
    }

    @Test
    void locationTotalsFilterZeroAndDoNotReuseBucketValues() throws Exception {
        Bootstrap.register();
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var world = (World) ((Unsafe) unsafeField.get(null)).allocateInstance(WorldServer.class);
        var provider = new WorldProviderSurface();
        provider.setDimension(7);
        var providerField = World.class.getDeclaredField("provider");
        providerField.setAccessible(true);
        providerField.set(world, provider);
        var firstLocation = new DimensionalBlockPos(world, 1, 0, 0);
        var secondLocation = new DimensionalBlockPos(world, 2, 0, 0);
        var zeroLocation = new DimensionalBlockPos(world, 3, 0, 0);
        var key = AEItemKey.of(new ItemStack(Items.APPLE));
        var service = new IngredientFlowService();
        var firstBucket = addBucket(service, key, 0, 0);
        addLocation(firstBucket, firstLocation, 0, 0);
        addLocation(firstBucket, secondLocation, 4, Long.MAX_VALUE - 1);
        addLocation(firstBucket, zeroLocation, 0, 0);
        var secondBucket = addBucket(service, key, 0, 0);
        addLocation(secondBucket, firstLocation, 9, 2);
        addLocation(secondBucket, secondLocation, 3, 8);

        var result = service.getRecentFlow(key);
        assertEquals(2, result.size());
        var first = result.stream().filter(flow -> flow.location.equals(firstLocation)).findFirst().orElseThrow();
        var second = result.stream().filter(flow -> flow.location.equals(secondLocation)).findFirst().orElseThrow();
        assertEquals(7, first.dimension);
        assertEquals(firstLocation.getPos(), first.position);
        assertEquals(7, second.dimension);
        assertEquals(secondLocation.getPos(), second.position);
        assertEquals(9, first.in);
        assertEquals(2, first.out);
        assertEquals(7, second.in);
        assertEquals(Long.MAX_VALUE, second.out);

        first.in = 100;
        assertEquals(9, service.getRecentFlow(key).stream()
                               .filter(flow -> flow.location.equals(firstLocation)).findFirst().orElseThrow().in);
        assertTrue(service.getRecentFlow(AEItemKey.of(new ItemStack(Items.STICK))).isEmpty());
    }

    @Test
    void accumulatesBothDirectionsAndKeepsPublishedPairsAcrossRebuilds() throws Exception {
        Bootstrap.register();
        var key = AEItemKey.of(new ItemStack(Items.APPLE));
        var service = new IngredientFlowService();
        service.setTrackingEnabled(true);
        addBucket(service, key, Long.MAX_VALUE - 5, 3);
        addBucket(service, key, 10, 7);

        var first = service.getAllRecentFlow();
        assertEquals(Long.MAX_VALUE, first.get(key).leftLong());
        assertEquals(10, first.get(key).rightLong());
        assertSame(first, service.getAllRecentFlow());
        assertThrows(UnsupportedOperationException.class, first::clear);

        service.setTrackingEnabled(false);
        assertTrue(service.getAllRecentFlow().isEmpty());
        service.setTrackingEnabled(true);
        addBucket(service, key, 2, 5);
        var next = service.getAllRecentFlow();
        assertNotSame(first.get(key), next.get(key));
        assertEquals(2, next.get(key).leftLong());
        assertEquals(5, next.get(key).rightLong());
        assertEquals(Long.MAX_VALUE, first.get(key).leftLong());
        assertEquals(10, first.get(key).rightLong());
    }

    @Test
    void recordingSaturatesBothDirectionsAndDisableDiscardsTheHistory() {
        boolean previousEnabled = AEConfig.GENERAL.enableIngredientFlowTracking;
        AEConfig.GENERAL.enableIngredientFlowTracking = true;
        try {
            var key = AEItemKey.of(Items.APPLE);
            var service = new IngredientFlowService();
            service.recordFlow(key, 7, IActionSource.empty());
            assertTrue(service.getAllRecentFlow().isEmpty());
            service.setTrackingEnabled(true);
            service.recordFlow(key, 7, IActionSource.empty());
            var first = service.getAllRecentFlow();
            service.recordFlow(key, Long.MAX_VALUE, IActionSource.empty());
            service.recordFlow(key, Long.MIN_VALUE, IActionSource.empty());
            service.recordFlow(key, -1, IActionSource.empty());
            var next = service.getAllRecentFlow();
            assertEquals(Long.MAX_VALUE, next.get(key).leftLong());
            assertEquals(Long.MAX_VALUE, next.get(key).rightLong());
            assertEquals(7, first.get(key).leftLong());
            assertEquals(0, first.get(key).rightLong());
            service.setTrackingEnabled(false);
            service.setTrackingEnabled(true);
            assertTrue(service.getAllRecentFlow().isEmpty());
        } finally {
            AEConfig.GENERAL.enableIngredientFlowTracking = previousEnabled;
        }
    }

    @Test
    void expiredBucketsLeaveTotalsAndInvalidateOnlyTheCurrentSnapshot() throws Exception {
        var key = AEItemKey.of(Items.APPLE);
        var service = new IngredientFlowService();
        long now = System.currentTimeMillis();
        long window = AEConfig.instance().getIngredientFlowTrackingWindowMinutes() * 60_000L;
        addBucket(service, key, 10, 20, now - window - 10_000);
        addBucket(service, key, 3, 4, now + 10_000);
        var before = service.getAllRecentFlow();
        service.onServerEndTick();
        var after = service.getAllRecentFlow();
        assertEquals(3, after.get(key).leftLong());
        assertEquals(4, after.get(key).rightLong());
        assertEquals(13, before.get(key).leftLong());
        assertEquals(24, before.get(key).rightLong());
        service.onServerEndTick();
        assertSame(after, service.getAllRecentFlow());
    }
}
