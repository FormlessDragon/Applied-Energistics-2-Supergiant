package ae2.core.network.serverbound;

import ae2.core.network.ServerboundPacket;
import ae2.container.implementations.ContainerTickAnalyser;
import ae2.items.tools.TickAnalyserConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

public class TickConfigSavePacket extends ServerboundPacket {
    private TickAnalyserConfig config;

    public TickConfigSavePacket() {
    }

    public TickConfigSavePacket(TickAnalyserConfig config) {
        this.config = config;
    }

    @Override
    protected void read(ByteBuf buf) {
        config = TickAnalyserConfig.read(buf);
    }

    @Override
    protected void write(ByteBuf buf) {
        config.write(buf);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (player.openContainer instanceof ContainerTickAnalyser container) {
            container.saveConfig(config);
        }
    }
}
