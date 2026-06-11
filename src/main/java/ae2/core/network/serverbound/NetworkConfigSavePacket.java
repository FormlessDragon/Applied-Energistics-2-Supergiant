package ae2.core.network.serverbound;

import ae2.container.implementations.ContainerNetworkAnalyser;
import ae2.core.network.ServerboundPacket;
import ae2.items.tools.NetworkAnalyserConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

public class NetworkConfigSavePacket extends ServerboundPacket {
    private int windowId;
    private NetworkAnalyserConfig config;

    public NetworkConfigSavePacket() {
    }

    public NetworkConfigSavePacket(int windowId, NetworkAnalyserConfig config) {
        this.windowId = windowId;
        this.config = config;
    }

    @Override
    protected void read(ByteBuf buf) {
        this.windowId = buf.readInt();
        config = NetworkAnalyserConfig.read(buf);
        if (config == null) {
            throw new IllegalArgumentException("Invalid network analyser config");
        }
        if (buf.isReadable()) {
            throw new IllegalArgumentException("Trailing network analyser config payload bytes: " + buf.readableBytes());
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        buf.writeInt(this.windowId);
        config.write(buf);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (this.config == null || !(player.openContainer instanceof ContainerNetworkAnalyser container)) {
            return;
        }
        if (container.windowId != this.windowId) {
            return;
        }
        container.saveConfig(config);
    }
}
