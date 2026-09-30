package ae2.container.me.items;

import ae2.api.stacks.AEItemKey;
import ae2.container.interfaces.ICraftingGridContainer.GridFill;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Wire format coverage for the crafting grid slots a batch order wants to fill. The terminal reads them on the client
 * to show the ordered materials semi-transparently in their slots, so they have to survive the round trip.
 */
class GridFillSyncTest {
    private static AEItemKey material;
    private static AEItemKey other;

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
        material = AEItemKey.of(new ItemStack(Items.SLIME_BALL));
        other = AEItemKey.of(new ItemStack(Items.ENDER_PEARL));
    }

    @Test
    void gridFillsRoundTrip() {
        var fills = List.of(new GridFill(0, material, 2), new GridFill(4, other, 1));
        var sync = new ContainerCraftingTerm.GridFillSync(fills);

        ByteBuf encoded = Unpooled.buffer();
        sync.writeToPacket(encoded);
        byte[] written = ByteBufUtil.getBytes(encoded, encoded.readerIndex(), encoded.readableBytes());
        encoded.release();

        var decoded = new ContainerCraftingTerm.GridFillSync(Unpooled.wrappedBuffer(written));

        assertEquals(fills, decoded.fills(), "the client has to read exactly what the server wrote");
    }
}
