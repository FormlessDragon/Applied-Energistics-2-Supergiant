package ae2.core.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public abstract class ServerboundPacket implements IMessage {

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

    public void handleServer(EntityPlayerMP player) {
    }
}
