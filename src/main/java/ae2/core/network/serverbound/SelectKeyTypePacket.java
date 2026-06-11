package ae2.core.network.serverbound;

import ae2.api.stacks.AEKeyType;
import ae2.container.AEBaseContainer;
import ae2.container.interfaces.IKeyTypeSelectionContainer;
import ae2.core.network.ServerboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

public class SelectKeyTypePacket extends ServerboundPacket {
    private int windowId;
    private AEKeyType keyType;
    private boolean enabled;

    public SelectKeyTypePacket() {
    }

    public SelectKeyTypePacket(int windowId, AEKeyType keyType, boolean enabled) {
        this.windowId = windowId;
        this.keyType = keyType;
        this.enabled = enabled;
    }

    @Override
    protected void read(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        this.windowId = packetBuffer.readInt();
        int rawId = packetBuffer.readVarInt();
        this.keyType = rawId >= 0 && rawId <= Byte.MAX_VALUE ? AEKeyType.fromRawId(rawId) : null;
        this.enabled = packetBuffer.readBoolean();
        if (packetBuffer.isReadable()) {
            throw new IllegalArgumentException("Trailing key type selection packet payload bytes: "
                + packetBuffer.readableBytes());
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeInt(this.windowId);
        packetBuffer.writeVarInt(this.keyType.getRawId());
        packetBuffer.writeBoolean(this.enabled);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (this.keyType == null) {
            return;
        }
        if (!(player.openContainer instanceof AEBaseContainer baseContainer)) {
            return;
        }
        if (baseContainer.windowId != this.windowId) {
            return;
        }
        if (!(baseContainer instanceof IKeyTypeSelectionContainer container)) {
            return;
        }
        container.getServerKeyTypeSelection().setEnabled(this.keyType, this.enabled);
    }
}
