package ae2.core.network.clientbound;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentString;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequesterSyncIdentityTest {
    private static int intField(Object instance, String name) throws Exception {
        return field(instance, name).getInt(instance);
    }

    private static long longField(Object instance, String name) throws Exception {
        return field(instance, name).getLong(instance);
    }

    private static Field field(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @Test
    void removalRoundTripsOnlyItsRequesterIdentity() throws Exception {
        var packet = RequesterSyncPacket.removeRequester(Long.MIN_VALUE).withIdentity(5, 16);
        ByteBuf buffer = Unpooled.buffer();
        try {
            packet.toBytes(buffer);
            var decoded = new RequesterSyncPacket();
            decoded.fromBytes(buffer);
            assertTrue(decoded.isRemoval());
            assertEquals(Long.MIN_VALUE, decoded.getRequesterId());
            assertEquals(5, intField(decoded, "windowId"));
            assertEquals(16, longField(decoded, "sourceGeneration"));
            assertFalse(decoded.isClearAll());
            assertFalse(decoded.isFullUpdate());
            assertTrue(decoded.getRows().isEmpty());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void deltaRetainsIdentityAndRowContentsWithoutMutatingSourceTag() throws Exception {
        var rows = new Int2ObjectOpenHashMap<NBTTagCompound>();
        var tag = new NBTTagCompound();
        tag.setLong("amount", 77);
        rows.put(2, tag);
        var packet = RequesterSyncPacket.incrementalUpdate(6, 18, Long.MIN_VALUE, rows);
        tag.setLong("amount", 99);
        ByteBuf buffer = Unpooled.buffer();
        try {
            packet.toBytes(buffer);
            var decoded = new RequesterSyncPacket();
            decoded.fromBytes(buffer);
            assertEquals(6, intField(decoded, "windowId"));
            assertEquals(18, longField(decoded, "sourceGeneration"));
            assertEquals(Long.MIN_VALUE, decoded.getRequesterId());
            assertEquals(77, decoded.getRows().get(2).getLong("amount"));
            assertFalse(decoded.isFullUpdate());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void unscopedAndTruncatedPacketsAreInvalidatedBeforeDispatch() throws Exception {
        for (boolean truncate : new boolean[]{false, true}) {
            ByteBuf buffer = Unpooled.buffer();
            try {
                var packet = truncate ? RequesterSyncPacket.clearAll(1, 2) : RequesterSyncPacket.clearAll();
                packet.toBytes(buffer);
                if (truncate) {
                    buffer.writerIndex(buffer.writerIndex() - 1);
                }
                var decoded = new RequesterSyncPacket();
                decoded.fromBytes(buffer);
                assertFalse(field(decoded, "valid").getBoolean(decoded));
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void fullUpdateRoundTripsWindowAndSourceGeneration() throws Exception {
        var packet = RequesterSyncPacket.fullUpdate(12, 34L, 7L, new TextComponentString("requester"), 9L, 0,
            new Int2ObjectOpenHashMap<>());
        ByteBuf buffer = Unpooled.buffer();
        try {
            packet.toBytes(buffer);
            var decoded = new RequesterSyncPacket();
            decoded.fromBytes(buffer);
            assertEquals(12, intField(decoded, "windowId"));
            assertEquals(34L, longField(decoded, "sourceGeneration"));
            assertEquals(7L, decoded.getRequesterId());
            assertTrue(decoded.isFullUpdate());
        } finally {
            buffer.release();
        }
    }

    @Test
    void clearUpdateRetainsIdentityInsteadOfBeingGlobal() throws Exception {
        var packet = RequesterSyncPacket.clearAll(19, 44L);
        ByteBuf buffer = Unpooled.buffer();
        try {
            packet.toBytes(buffer);
            var decoded = new RequesterSyncPacket();
            decoded.fromBytes(buffer);
            assertTrue(decoded.isClearAll());
            assertEquals(19, intField(decoded, "windowId"));
            assertEquals(44L, longField(decoded, "sourceGeneration"));
        } finally {
            buffer.release();
        }
    }
}
