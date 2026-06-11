package ae2.core.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public abstract class ServerboundPacket implements IMessage {
    private boolean invalid;

    @Override
    public final void fromBytes(ByteBuf buf) {
        try {
            this.read(buf);
        } catch (RuntimeException e) {
            invalidateMalformed(buf, e);
        }
    }

    @Override
    public final void toBytes(ByteBuf buf) {
        this.write(buf);
    }

    protected void read(ByteBuf buf) {
    }

    protected void write(ByteBuf buf) {
    }

    protected final void invalidateMalformed(ByteBuf buf, RuntimeException exception) {
        this.invalid = true;
        buf.skipBytes(buf.readableBytes());
        String packetName = getClass().getSimpleName();
        NetworkPacketHelper.warnMalformedPacket(exception, packetName,
            "Ignoring malformed serverbound packet %s", packetName);
    }

    public final boolean isInvalid() {
        return this.invalid;
    }

    public void handleServer(EntityPlayerMP player) {
    }
}
