package ae2.core.network.clientbound;

import ae2.container.AEBaseContainer;
import ae2.core.network.AppEngPayloadHandler;
import ae2.core.network.ClientboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * This packet is used to synchronize container-fields from server to client.
 */
public class GuiDataSyncPacket extends ClientboundPacket {
    private static final int MAX_PAYLOAD_BYTES = 1024 * 1024;

    private int windowId;
    private byte[] payload;

    public GuiDataSyncPacket() {
    }

    public GuiDataSyncPacket(int windowId, byte[] payload) {
        this.windowId = windowId;
        this.payload = payload;
    }

    @Override
    protected void read(ByteBuf buf) {
        this.windowId = buf.readInt();
        int length = buf.readInt();
        if (length < 0 || length > MAX_PAYLOAD_BYTES || length > buf.readableBytes()) {
            this.payload = null;
            buf.skipBytes(buf.readableBytes());
            return;
        }

        this.payload = new byte[length];
        buf.readBytes(this.payload);
        if (buf.isReadable()) {
            this.payload = null;
            buf.skipBytes(buf.readableBytes());
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        buf.writeInt(windowId);
        buf.writeInt(payload.length);
        buf.writeBytes(payload);
    }

    @Override
    public void handleClient(Minecraft minecraft) {
        if (this.payload != null && minecraft.player != null
            && minecraft.player.openContainer instanceof AEBaseContainer container
            && container.windowId == windowId) {
            container.receiveGuiData(this.payload);
        }
    }

    public static final class Handler implements IMessageHandler<GuiDataSyncPacket, IMessage> {
        private static final AppEngPayloadHandler.Client<GuiDataSyncPacket> DELEGATE = new AppEngPayloadHandler.Client<>();

        @Override
        public IMessage onMessage(GuiDataSyncPacket message, MessageContext ctx) {
            return DELEGATE.onMessage(message, ctx);
        }
    }
}
