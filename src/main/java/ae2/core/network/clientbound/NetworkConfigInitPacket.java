package ae2.core.network.clientbound;

import ae2.client.gui.implementations.GuiNetworkAnalyser;
import ae2.core.network.ClientboundPacket;
import ae2.items.tools.NetworkAnalyserConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class NetworkConfigInitPacket extends ClientboundPacket {
    private NetworkAnalyserConfig config;

    public NetworkConfigInitPacket() {
    }

    public NetworkConfigInitPacket(NetworkAnalyserConfig config) {
        this.config = config;
    }

    @Override
    protected void read(ByteBuf buf) {
        config = NetworkAnalyserConfig.read(buf);
        if (config == null) {
            config = NetworkAnalyserConfig.DEFAULT;
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        config.write(buf);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (minecraft.currentScreen instanceof GuiNetworkAnalyser gui) {
            gui.loadConfig(config);
        }
    }
}
