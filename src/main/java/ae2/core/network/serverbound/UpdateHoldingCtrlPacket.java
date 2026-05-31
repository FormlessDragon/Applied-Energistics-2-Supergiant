package ae2.core.network.serverbound;

import ae2.core.PlayerState;
import ae2.core.network.ServerboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

public class UpdateHoldingCtrlPacket extends ServerboundPacket {

    private boolean keyDown;

    public UpdateHoldingCtrlPacket() {
    }

    public UpdateHoldingCtrlPacket(boolean keyDown) {
        this.keyDown = keyDown;
    }

    @Override
    protected void read(ByteBuf buf) {
        this.keyDown = new PacketBuffer(buf).readBoolean();
    }

    @Override
    protected void write(ByteBuf buf) {
        new PacketBuffer(buf).writeBoolean(this.keyDown);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        PlayerState.setHoldingCtrl(player, this.keyDown);
    }
}
