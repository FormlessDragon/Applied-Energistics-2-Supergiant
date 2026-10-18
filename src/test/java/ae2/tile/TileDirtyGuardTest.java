package ae2.tile;

import net.minecraft.world.WorldServer;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TileDirtyGuardTest {
    @Test
    void recursiveSaveIsSuppressedButLaterSaveSurvivesMarkDirtyFailure() throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var world = (WorldServer) ((Unsafe) field.get(null)).allocateInstance(WorldServer.class);
        var tile = new Tile();
        tile.setWorld(world);
        assertThrows(IllegalStateException.class, tile::saveChanges);
        tile.fail = false;
        tile.saveChanges();
        assertEquals(2, tile.dirties);
        tile.setWorld(null);
        tile.saveChanges();
        assertEquals(2, tile.dirties);
    }

    private static final class Tile extends AEBaseTile {
        int dirties;
        boolean fail = true;

        @Override
        public void markDirty() {
            dirties++;
            saveChanges();
            if (fail) throw new IllegalStateException("controlled dirty callback failure");
        }
    }
}
