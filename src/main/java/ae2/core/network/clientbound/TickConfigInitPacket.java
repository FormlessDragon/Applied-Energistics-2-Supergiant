package ae2.core.network.clientbound;

import ae2.core.network.ClientboundPacket;
import ae2.client.gui.implementations.GuiTickAnalyser;
import ae2.items.tools.TickAnalyserConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class TickConfigInitPacket extends ClientboundPacket {
    private TickAnalyserConfig config;

    public TickConfigInitPacket() {
    }

    public TickConfigInitPacket(TickAnalyserConfig config) {
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
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (minecraft.currentScreen instanceof GuiTickAnalyser gui) {
            gui.loadConfig(config);
        }
    }
}
