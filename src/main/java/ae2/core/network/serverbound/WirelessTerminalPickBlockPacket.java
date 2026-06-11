package ae2.core.network.serverbound;

import ae2.container.me.common.MEIngredientActions;
import ae2.core.network.ServerboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import java.io.IOException;

public class WirelessTerminalPickBlockPacket extends ServerboundPacket {
    private static final int MAX_PACKET_BYTES = 128 * 1024;

    private ItemStack target = ItemStack.EMPTY;
    private int hotbarSlot;
    private boolean invalid;

    public WirelessTerminalPickBlockPacket() {
    }

    public WirelessTerminalPickBlockPacket(ItemStack target, int hotbarSlot) {
        this.target = target.copy();
        this.hotbarSlot = hotbarSlot;
    }

    @Override
    protected void read(ByteBuf buf) {
        if (buf.readableBytes() > MAX_PACKET_BYTES) {
            invalidate(buf);
            return;
        }

        try {
            PacketBuffer packetBuffer = new PacketBuffer(buf);
            this.target = packetBuffer.readItemStack();
            this.hotbarSlot = packetBuffer.readInt();
            if (packetBuffer.isReadable()) {
                throw new IllegalArgumentException("Trailing wireless terminal pick block packet payload bytes: "
                    + packetBuffer.readableBytes());
            }
        } catch (IOException | RuntimeException e) {
            invalidate(buf, e);
        }
    }

    private void invalidate(ByteBuf buf) {
        invalidate(buf, new IllegalArgumentException("Wireless terminal pick block packet exceeds maximum size"));
    }

    private void invalidate(ByteBuf buf, Exception exception) {
        this.invalid = true;
        this.target = ItemStack.EMPTY;
        invalidateMalformed(buf, exception instanceof RuntimeException runtimeException
            ? runtimeException
            : new IllegalArgumentException("Could not read wireless terminal pick block packet", exception));
    }

    @Override
    protected void write(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeItemStack(this.target);
        packetBuffer.writeInt(this.hotbarSlot);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (this.invalid || this.target.isEmpty()) {
            return;
        }

        MEIngredientActions.retrieveToHotbarFromWirelessTerminals(player, this.target, this.hotbarSlot);
    }
}
