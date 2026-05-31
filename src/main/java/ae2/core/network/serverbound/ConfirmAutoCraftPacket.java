package ae2.core.network.serverbound;

import ae2.container.implementations.ContainerCraftAmount;
import ae2.core.network.ServerboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

public class ConfirmAutoCraftPacket extends ServerboundPacket {
    private int amount;
    private boolean craftMissingAmount;
    private boolean autoStart;

    public ConfirmAutoCraftPacket() {
    }

    public ConfirmAutoCraftPacket(int amount, boolean craftMissingAmount, boolean autoStart) {
        this.amount = amount;
        this.craftMissingAmount = craftMissingAmount;
        this.autoStart = autoStart;
    }

    @Override
    protected void read(ByteBuf buf) {
        this.amount = buf.readInt();
        this.craftMissingAmount = buf.readBoolean();
        this.autoStart = buf.readBoolean();
    }

    @Override
    protected void write(ByteBuf buf) {
        buf.writeInt(this.amount);
        buf.writeBoolean(this.craftMissingAmount);
        buf.writeBoolean(this.autoStart);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (player.openContainer instanceof ContainerCraftAmount container) {
            container.confirm(this.amount, this.craftMissingAmount, this.autoStart);
        }
    }
}
