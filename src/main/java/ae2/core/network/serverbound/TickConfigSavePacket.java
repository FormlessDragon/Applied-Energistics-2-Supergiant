package ae2.core.network.serverbound;

import ae2.container.implementations.ContainerTickAnalyser;
import ae2.core.network.ServerboundPacket;
import ae2.items.tools.TickAnalyserConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

public class TickConfigSavePacket extends ServerboundPacket {
    private int windowId;
    private TickAnalyserConfig config;

    public TickConfigSavePacket() {
    }

    public TickConfigSavePacket(int windowId, TickAnalyserConfig config) {
        this.windowId = windowId;
        this.config = config;
    }

    @Override
    protected void read(ByteBuf buf) {
        this.windowId = buf.readInt();
        config = TickAnalyserConfig.read(buf);
        if (buf.isReadable()) {
            throw new IllegalArgumentException("Trailing tick analyser config payload bytes: " + buf.readableBytes());
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        buf.writeInt(this.windowId);
        config.write(buf);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (this.config == null || !(player.openContainer instanceof ContainerTickAnalyser container)) {
            return;
        }
        if (container.windowId != this.windowId) {
            return;
        }
        container.saveConfig(config);
    }
}
