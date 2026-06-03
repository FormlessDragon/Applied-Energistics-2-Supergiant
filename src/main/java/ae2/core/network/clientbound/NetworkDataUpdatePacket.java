package ae2.core.network.clientbound;

import ae2.core.network.ClientboundPacket;
import ae2.client.render.NetworkDataHandler;
import ae2.me.NetworkData;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class NetworkDataUpdatePacket extends ClientboundPacket {
    private NetworkData data;

    public NetworkDataUpdatePacket() {
    }

    public NetworkDataUpdatePacket(NetworkData data) {
        this.data = data;
    }

    @Override
    protected void read(ByteBuf buf) {
        data = NetworkData.read(buf);
    }

    @Override
    protected void write(ByteBuf buf) {
        data.write(buf);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        NetworkDataHandler.receiveData(data);
    }
}
