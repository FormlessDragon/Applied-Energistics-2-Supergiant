package ae2.core.network.serverbound;

import ae2.api.stacks.AEKeyType;
import ae2.container.interfaces.IKeyTypeSelectionContainer;
import ae2.core.network.ServerboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

public class SelectKeyTypePacket extends ServerboundPacket {
    private AEKeyType keyType;
    private boolean enabled;

    public SelectKeyTypePacket() {
    }

    public SelectKeyTypePacket(AEKeyType keyType, boolean enabled) {
        this.keyType = keyType;
        this.enabled = enabled;
    }

    @Override
    protected void read(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        this.keyType = AEKeyType.fromRawId(packetBuffer.readVarInt());
        this.enabled = packetBuffer.readBoolean();
    }

    @Override
    protected void write(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeVarInt(this.keyType.getRawId());
        packetBuffer.writeBoolean(this.enabled);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (this.keyType == null) {
            return;
        }
        if (player.openContainer instanceof IKeyTypeSelectionContainer container) {
            container.getServerKeyTypeSelection().setEnabled(this.keyType, this.enabled);
        }
    }
}
