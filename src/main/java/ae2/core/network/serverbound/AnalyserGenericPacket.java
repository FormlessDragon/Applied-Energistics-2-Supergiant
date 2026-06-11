package ae2.core.network.serverbound;

import ae2.container.implementations.ContainerNetworkAnalyser;
import ae2.container.implementations.ContainerTickAnalyser;
import ae2.core.network.InitNetwork;
import ae2.core.network.ServerboundPacket;
import ae2.core.network.clientbound.NetworkConfigInitPacket;
import ae2.core.network.clientbound.TickConfigInitPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

public class AnalyserGenericPacket extends ServerboundPacket {
    private static final int MAX_ACTION_NAME_LENGTH = 16;

    private int windowId;
    private String name = "";

    public AnalyserGenericPacket() {
    }

    public AnalyserGenericPacket(int windowId, String name) {
        this.windowId = windowId;
        this.name = name;
    }

    @Override
    protected void read(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        this.windowId = packetBuffer.readVarInt();
        this.name = packetBuffer.readString(MAX_ACTION_NAME_LENGTH);
        if (packetBuffer.isReadable()) {
            throw new IllegalArgumentException("Trailing analyser generic packet payload bytes: "
                + packetBuffer.readableBytes());
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeVarInt(this.windowId);
        packetBuffer.writeString(this.name);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (!"update".equals(this.name)) {
            return;
        }
        if (player.openContainer instanceof ContainerNetworkAnalyser container) {
            if (container.windowId != this.windowId) {
                return;
            }
            InitNetwork.sendToClient(player, new NetworkConfigInitPacket(container.getConfig()));
        } else if (player.openContainer instanceof ContainerTickAnalyser container) {
            if (container.windowId != this.windowId) {
                return;
            }
            InitNetwork.sendToClient(player, new TickConfigInitPacket(container.getConfig()));
        }
    }
}
