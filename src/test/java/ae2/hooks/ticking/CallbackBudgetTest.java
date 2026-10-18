package ae2.hooks.ticking;

import ae2.util.ILevelRunnable;
import com.google.common.base.Ticker;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CallbackBudgetTest {
    private static World world() throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (World) ((Unsafe) field.get(null)).allocateInstance(WorldServer.class);
    }

    private static int drain(TickHandler handler, Queue<ILevelRunnable> queue) throws Exception {
        var method = TickHandler.class.getDeclaredMethod("processQueue", Queue.class, World.class);
        method.setAccessible(true);
        try {
            return (int) method.invoke(handler, queue, null);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Error error) throw error;
            throw (Exception) e.getCause();
        }
    }

    private static int processed(TickHandler handler) throws Exception {
        var field = TickHandler.class.getDeclaredField("processQueueElementsProcessed");
        field.setAccessible(true);
        return field.getInt(handler);
    }

    @Test
    void failuresConsumeBudgetAndResumeInFifoOrder() throws Exception {
        var clock = new Clock();
        var handler = new TickHandler(clock);
        var calls = new ArrayList<Integer>();
        Queue<ILevelRunnable> queue = new ArrayDeque<>();
        for (int i = 0; i < 4; i++) {
            int id = i;
            queue.add(level -> {
                calls.add(id);
                clock.millis += 25;
                throw new IllegalStateException("PA-006 controlled failure " + id);
            });
        }
        assertEquals(3, drain(handler, queue));
        assertEquals(List.of(0), calls);
        assertEquals(1, processed(handler));
        assertEquals(3, drain(handler, queue), "exhausted budget must not poll another callback");
        handler.onServerStartTick(new TickEvent.ServerTickEvent(TickEvent.Phase.START));
        assertEquals(2, drain(handler, queue));
        assertEquals(List.of(0, 1), calls);
    }

    @Test
    void fixedSeedMixedCallbacksMatchFifoOracleAcrossTicks() throws Exception {
        var random = new Random(600141L);
        var clock = new Clock();
        var handler = new TickHandler(clock);
        Queue<ILevelRunnable> queue = new ArrayDeque<>();
        var calls = new ArrayList<Integer>();
        for (int i = 0; i < 200; i++) {
            int id = i;
            int cost = random.nextInt(1, 9);
            boolean fail = random.nextInt(5) == 0;
            queue.add(level -> {
                calls.add(id);
                clock.millis += cost;
                if (fail) throw new IllegalArgumentException("PA-006 mixed callback " + id);
            });
        }
        while (!queue.isEmpty()) {
            handler.onServerStartTick(new TickEvent.ServerTickEvent(TickEvent.Phase.START));
            drain(handler, queue);
        }
        assertEquals(IntStream.range(0, 200).boxed().toList(), calls);
    }

    @Test
    void callbackReentryAndAppendCannotResetTheBudgetOrDuplicateWork() throws Exception {
        var clock = new Clock();
        var handler = new TickHandler(clock);
        Queue<ILevelRunnable> queue = new ArrayDeque<>();
        var calls = new ArrayList<Integer>();
        queue.add(level -> {
            calls.add(1);
            handler.onServerStartTick(new TickEvent.ServerTickEvent(TickEvent.Phase.START));
            try {
                assertEquals(0, drain(handler, queue));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            queue.add(ignored -> calls.add(3));
            clock.millis += 25;
        });
        queue.add(level -> calls.add(2));
        assertEquals(2, drain(handler, queue));
        assertEquals(List.of(1), calls);
        handler.onServerStartTick(new TickEvent.ServerTickEvent(TickEvent.Phase.START));
        assertEquals(0, drain(handler, queue));
        assertEquals(List.of(1, 2, 3), calls);
    }

    @Test
    void fatalErrorPropagatesAndFinallyRestoresTheHandler() throws Exception {
        var clock = new Clock();
        var handler = new TickHandler(clock);
        Queue<ILevelRunnable> queue = new ArrayDeque<>();
        var fatal = new OutOfMemoryError("PA-006 injected fatal error, no allocation failure");
        queue.add(level -> {
            throw fatal;
        });
        assertSame(fatal, assertThrows(OutOfMemoryError.class, () -> drain(handler, queue)));
        assertEquals(1, processed(handler));
        var calls = new AtomicInteger();
        queue.add(level -> calls.incrementAndGet());
        assertEquals(0, drain(handler, queue));
        assertEquals(1, calls.get());
    }

    @Test
    void shutdownInvalidatesDetachedWorkAndNewSessionCanRun() throws Exception {
        var handler = new TickHandler(new Clock());
        Queue<ILevelRunnable> queue = new ArrayDeque<>();
        var calls = new ArrayList<Integer>();
        var owner = Thread.currentThread();
        queue.add(level -> {
            assertSame(owner, Thread.currentThread());
            calls.add(1);
            handler.shutdown();
        });
        queue.add(level -> fail("Old generation must stop after shutdown"));
        assertEquals(1, drain(handler, queue));
        handler.addCallable(null, () -> {
            assertSame(owner, Thread.currentThread());
            calls.add(2);
        });
        handler.onServerStartTick(new TickEvent.ServerTickEvent(TickEvent.Phase.START));
        handler.onServerEndTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        assertEquals(List.of(1, 2), calls);
    }

    @Test
    void unloadingAnUnrelatedWorldDoesNotDiscardCurrentWork() throws Exception {
        var handler = new TickHandler(new Clock());
        World current = world();
        World other = world();
        var calls = new AtomicInteger();
        handler.addCallable(current, () -> {
            handler.onUnloadWorld(new WorldEvent.Unload(other));
            calls.incrementAndGet();
        });
        handler.addCallable(current, calls::incrementAndGet);
        handler.onWorldStartTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.START, current));
        assertEquals(2, calls.get());
    }

    @Test
    void ownerWorldUnloadDoesNotRepublishDetachedQueue() throws Exception {
        var handler = new TickHandler(new Clock());
        World current = world();
        var owner = Thread.currentThread();
        var calls = new AtomicInteger();
        handler.addCallable(current, () -> {
            assertSame(owner, Thread.currentThread());
            handler.onUnloadWorld(new WorldEvent.Unload(current));
            calls.incrementAndGet();
        });
        handler.addCallable(current, () -> fail("Unloaded queue must not publish"));
        handler.onWorldStartTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.START, current));
        handler.onWorldStartTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.START, current));
        assertEquals(1, calls.get());
        var field = TickHandler.class.getDeclaredField("callQueue");
        field.setAccessible(true);
        assertTrue(((Map<?, ?>) field.get(handler)).isEmpty());
        System.out.println("G-OWNER callback/commit thread=" + owner.getName()
            + " session=handler-instance sourceGeneration=0 targetGeneration=1 oldQueuePublished=false");
    }

    @Test
    void lateWorldCallbackAfterUnloadIsRejectedWithoutRetainingQueue() throws Exception {
        var handler = new TickHandler(new Clock());
        World current = world();
        handler.onUnloadWorld(new WorldEvent.Unload(current));
        assertFalse(handler.tryAddCallable(current, () -> fail("A callback for an unloaded world must not run")));
        handler.addCallable(current, () -> fail("A callback for an unloaded world must not run"));
        handler.onWorldStartTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.START, current));

        var field = TickHandler.class.getDeclaredField("callQueue");
        field.setAccessible(true);
        assertTrue(((Map<?, ?>) field.get(handler)).isEmpty());
    }

    private static final class Clock extends Ticker {
        long millis;

        @Override
        public long read() {
            return millis * 1_000_000L;
        }
    }
}
