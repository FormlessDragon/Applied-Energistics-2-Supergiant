package ae2.core.network.clientbound;

import ae2.core.network.ClientboundPacket;
import ae2.client.render.ProfileDataHandler;
import ae2.me.ticker.ProfileData;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ProfileDataUpdatePacket extends ClientboundPacket {
    private ProfileData data;

    public ProfileDataUpdatePacket() {
    }

    public ProfileDataUpdatePacket(ProfileData data) {
        this.data = data;
    }

    @Override
    protected void read(ByteBuf buf) {
        data = ProfileData.read(buf);
    }

    @Override
    protected void write(ByteBuf buf) {
        data.write(buf);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        ProfileDataHandler.receiveData(data);
    }
}
