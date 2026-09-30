package ae2.core.network.clientbound;

import ae2.api.stacks.AEItemKey;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Wire format coverage for the crafting job status. A started batch order of the crafting terminal reports its
 * placeholder output together with the materials it really crafts, and the client pins and tracks those, so both have
 * to survive the round trip.
 */
class CraftingJobStatusPacketTest {
    private static AEItemKey output;
    private static AEItemKey material;

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
        output = AEItemKey.of(new ItemStack(Items.DIAMOND_PICKAXE));
        material = AEItemKey.of(new ItemStack(Items.SLIME_BALL));
    }

    @Test
    void startedJobWithTrackedItemsRoundTrips() {
        assertRoundTrips(new CraftingJobStatusPacket(UUID.randomUUID(), output, 4, 4,
            CraftingJobStatusPacket.Status.STARTED, false, List.of(material)));
    }

    @Test
    void jobWithoutTrackedItemsRoundTrips() {
        assertRoundTrips(new CraftingJobStatusPacket(UUID.randomUUID(), output, 1, 0,
            CraftingJobStatusPacket.Status.FINISHED, true));
    }

    private static void assertRoundTrips(CraftingJobStatusPacket packet) {
        ByteBuf encoded = Unpooled.buffer();
        packet.toBytes(encoded);
        byte[] written = ByteBufUtil.getBytes(encoded, encoded.readerIndex(), encoded.readableBytes());
        encoded.release();

        var decoded = new CraftingJobStatusPacket();
        var input = Unpooled.wrappedBuffer(written);
        decoded.fromBytes(input);
        input.release();

        ByteBuf reEncoded = Unpooled.buffer();
        decoded.toBytes(reEncoded);
        byte[] rewritten = ByteBufUtil.getBytes(reEncoded, reEncoded.readerIndex(), reEncoded.readableBytes());
        reEncoded.release();

        assertArrayEquals(written, rewritten, "the client has to read exactly what the server wrote");
    }
}
