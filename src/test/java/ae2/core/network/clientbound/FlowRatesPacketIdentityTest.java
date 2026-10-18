package ae2.core.network.clientbound;

import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.LongLongMutablePair;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowRatesPacketIdentityTest {
    private static int intField(Object instance, String name) throws Exception {
        return field(instance, name).getInt(instance);
    }

    private static long longField(Object instance, String name) throws Exception {
        return field(instance, name).getLong(instance);
    }

    private static boolean booleanField(Object instance, String name) throws Exception {
        return field(instance, name).getBoolean(instance);
    }

    private static Field field(Object instance, String name) throws Exception {
        var field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @Test
    void pairKeepsInflowThenOutflowWireOrder() throws Exception {
        Bootstrap.register();
        var key = AEItemKey.of(new ItemStack(Items.APPLE));
        var packet = new FlowRatesPacket(12, 34, true,
            Map.of(key, new LongLongMutablePair(Long.MAX_VALUE, 17)));
        ByteBuf buffer = Unpooled.buffer();
        try {
            packet.toBytes(buffer);
            var wire = new PacketBuffer(buffer.duplicate());
            assertEquals(12, wire.readVarInt());
            assertEquals(34, wire.readVarLong());
            assertTrue(wire.readBoolean());
            assertEquals(0, wire.readVarLong());
            assertEquals(0, wire.readVarInt());
            assertEquals(1, wire.readVarInt());
            assertTrue(wire.readBoolean());
            assertEquals(1, wire.readInt());
            assertEquals(key, AEKey.readKey(wire));
            assertEquals(Long.MAX_VALUE, wire.readLong());
            assertEquals(17, wire.readLong());
            assertEquals(0, wire.readableBytes());

            var decoded = new FlowRatesPacket();
            decoded.fromBytes(buffer);
            var rates = (Map<?, ?>) field(decoded, "rates").get(decoded);
            var rate = (LongLongPair) rates.get(key);
            assertEquals(Long.MAX_VALUE, rate.leftLong());
            assertEquals(17, rate.rightLong());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void roundTripsWindowSourceGenerationAndTrackingState() throws Exception {
        var packet = new FlowRatesPacket(12, 34L, false, 56L, 1, 3, true, Map.of());
        ByteBuf buffer = Unpooled.buffer();
        try {
            packet.toBytes(buffer);
            var decoded = new FlowRatesPacket();
            decoded.fromBytes(buffer);
            assertEquals(12, intField(decoded, "windowId"));
            assertEquals(34L, longField(decoded, "sourceGeneration"));
            assertEquals(56L, longField(decoded, "snapshotId"));
            assertEquals(1, intField(decoded, "pageIndex"));
            assertEquals(3, intField(decoded, "pageCount"));
            assertTrue(booleanField(decoded, "complete"));
            assertFalse(booleanField(decoded, "trackingActive"));
        } finally {
            buffer.release();
        }
    }
}
