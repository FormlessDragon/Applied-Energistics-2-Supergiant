package ae2.core.network.serverbound;

import ae2.core.network.InitNetwork;
import ae2.core.network.ServerboundPacket;
import ae2.container.implementations.ContainerNetworkAnalyser;
import ae2.container.implementations.ContainerTickAnalyser;
import ae2.core.network.clientbound.NetworkConfigInitPacket;
import ae2.core.network.clientbound.TickConfigInitPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

public class AnalyserGenericPacket extends ServerboundPacket {
    private String name = "";

    public AnalyserGenericPacket() {
    }

    public AnalyserGenericPacket(String name) {
        this.name = name;
    }

    @Override
    protected void read(ByteBuf buf) {
        this.name = new PacketBuffer(buf).readString(32767);
    }

    @Override
    protected void write(ByteBuf buf) {
        new PacketBuffer(buf).writeString(this.name);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (!"update".equals(this.name)) {
            return;
        }
        if (player.openContainer instanceof ContainerNetworkAnalyser container) {
            InitNetwork.sendToClient(player, new NetworkConfigInitPacket(container.getConfig()));
        } else if (player.openContainer instanceof ContainerTickAnalyser container) {
            InitNetwork.sendToClient(player, new TickConfigInitPacket(container.getConfig()));
        }
    }
}
