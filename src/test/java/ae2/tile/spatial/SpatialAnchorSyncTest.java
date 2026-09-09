package ae2.tile.spatial;

import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpatialAnchorSyncTest {
    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void syncIsSelfContainedAndDoesNotLimitNetworkChunksToTransferRadius() {
        var server = new Anchor();
        server.getConfigManager().putSetting(Settings.OVERLAY_MODE, YesNo.YES);
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 12; z++) {
                server.registerChunk(new ChunkPos(x, z));
                server.registerChunk(new ChunkPos(x, z));
            }
        }
        assertEquals(144, server.countLoadedChunks());
        var client = new Anchor();
        server.syncTo(client);
        assertEquals(server.getLoadedChunks(), client.getLoadedChunks());
        server.syncTo(client);
        assertEquals(144, client.getLoadedChunks().size());
        var newTrackingClient = new Anchor();
        server.syncTo(newTrackingClient);
        assertEquals(client.getLoadedChunks(), newTrackingClient.getLoadedChunks());
        server.releaseAll();
        server.syncTo(client);
        assertEquals(0, client.getLoadedChunks().size());
    }

    private static final class Anchor extends TileSpatialAnchor {
        @Override
        public ItemStack getItemFromTile() {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean canBeRotated() {
            return false;
        }

        private void syncTo(Anchor client) {
            ByteBuf data = Unpooled.buffer();
            try {
                writeToStream(data);
                client.readFromStream(data);
                assertEquals(0, data.readableBytes());
            } finally {
                data.release();
            }
        }
    }
}
