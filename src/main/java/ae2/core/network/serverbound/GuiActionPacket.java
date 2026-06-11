package ae2.core.network.serverbound;

import ae2.container.AEBaseContainer;
import ae2.core.network.ServerboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import org.jetbrains.annotations.Nullable;

public class GuiActionPacket extends ServerboundPacket {
    public static final int MAX_ACTION_NAME_LENGTH = 128;
    public static final int MAX_JSON_PAYLOAD_LENGTH = 16384;

    private int windowId;
    private String actionName;
    @Nullable
    private String jsonPayload;

    public GuiActionPacket() {
    }

    public GuiActionPacket(int windowId, String actionName, @Nullable String jsonPayload) {
        this.windowId = windowId;
        this.actionName = actionName;
        this.jsonPayload = jsonPayload;
    }

    @Override
    protected void read(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        this.windowId = packetBuffer.readInt();
        this.actionName = packetBuffer.readString(MAX_ACTION_NAME_LENGTH);
        if (packetBuffer.readBoolean()) {
            this.jsonPayload = packetBuffer.readString(MAX_JSON_PAYLOAD_LENGTH);
        } else {
            this.jsonPayload = null;
        }
        if (buf.isReadable()) {
            throw new IllegalArgumentException("Trailing GUI action packet payload bytes: " + buf.readableBytes());
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeInt(this.windowId);
        packetBuffer.writeString(this.actionName);
        packetBuffer.writeBoolean(this.jsonPayload != null);
        if (this.jsonPayload != null) {
            packetBuffer.writeString(this.jsonPayload);
        }
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (player.openContainer instanceof AEBaseContainer container) {
            if (container.windowId != this.windowId) {
                return;
            }
            container.receiveClientAction(this.actionName, this.jsonPayload);
        }
    }
}
