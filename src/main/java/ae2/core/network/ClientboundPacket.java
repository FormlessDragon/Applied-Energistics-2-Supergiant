package ae2.core.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public abstract class ClientboundPacket implements IMessage {

    @Override
    public final void fromBytes(ByteBuf buf) {
        this.read(buf);
    }

    @Override
    public final void toBytes(ByteBuf buf) {
        this.write(buf);
    }

    protected void read(ByteBuf buf) {
    }

    protected void write(ByteBuf buf) {
    }

    public final void handleClient(Object minecraft) {
        handleClient((Minecraft) minecraft);
    }

    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
    }
}
