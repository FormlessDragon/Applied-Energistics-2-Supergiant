package ae2.core.network.serverbound;

import ae2.core.network.ServerboundPacket;
import ae2.container.implementations.ContainerNetworkAnalyser;
import ae2.items.tools.NetworkAnalyserConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

public class NetworkConfigSavePacket extends ServerboundPacket {
    private NetworkAnalyserConfig config;

    public NetworkConfigSavePacket() {
    }

    public NetworkConfigSavePacket(NetworkAnalyserConfig config) {
        this.config = config;
    }

    @Override
    protected void read(ByteBuf buf) {
        config = NetworkAnalyserConfig.read(buf);
    }

    @Override
    protected void write(ByteBuf buf) {
        config.write(buf);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (player.openContainer instanceof ContainerNetworkAnalyser container) {
            container.saveConfig(config);
        }
    }
}
