package ae2.hooks.ticking;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingJobRegistryTest {
    private static World world() throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (World) ((Unsafe) field.get(null)).allocateInstance(WorldServer.class);
    }

    @Test
    void closingWorldDetachesJobsAndRejectsLateRegistration() throws Exception {
        var registry = new TickHandler.CraftingJobRegistry<String>();
        World world = world();
        long epoch = registry.getSessionEpoch();

        assertTrue(registry.register(world, "running", epoch));
        assertEquals(List.of("running"), registry.closeWorld(world));
        assertTrue(registry.snapshot(world).isEmpty());
        assertFalse(registry.register(world, "late", epoch));
    }

    @Test
    void shutdownAndNewSessionRejectOldEpochAndKeepUnregisterIdempotent() throws Exception {
        var registry = new TickHandler.CraftingJobRegistry<String>();
        World world = world();
        long oldEpoch = registry.getSessionEpoch();

        assertTrue(registry.register(world, "old", oldEpoch));
        assertEquals(List.of("old"), registry.shutdown());
        registry.unregister(world, "old");
        registry.openSessionIfClosed();
        long newEpoch = registry.getSessionEpoch();

        assertNotEquals(oldEpoch, newEpoch);
        assertFalse(registry.register(world, "stale", oldEpoch));
        assertTrue(registry.register(world, "new", newEpoch));
        assertEquals(List.of("new"), registry.snapshot(world));
    }
}
