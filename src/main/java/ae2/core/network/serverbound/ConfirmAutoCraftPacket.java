package ae2.core.network.serverbound;

import ae2.container.implementations.ContainerCraftAmount;
import ae2.core.network.ServerboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

public class ConfirmAutoCraftPacket extends ServerboundPacket {
    private int windowId;
    private int amount;
    private boolean craftMissingAmount;
    private boolean autoStart;

    public ConfirmAutoCraftPacket() {
    }

    public ConfirmAutoCraftPacket(int windowId, int amount, boolean craftMissingAmount, boolean autoStart) {
        this.windowId = windowId;
        this.amount = amount;
        this.craftMissingAmount = craftMissingAmount;
        this.autoStart = autoStart;
    }

    @Override
    protected void read(ByteBuf buf) {
        this.windowId = buf.readInt();
        this.amount = buf.readInt();
        this.craftMissingAmount = buf.readBoolean();
        this.autoStart = buf.readBoolean();
        if (buf.isReadable()) {
            throw new IllegalArgumentException("Trailing auto craft confirmation payload bytes: " + buf.readableBytes());
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        buf.writeInt(this.windowId);
        buf.writeInt(this.amount);
        buf.writeBoolean(this.craftMissingAmount);
        buf.writeBoolean(this.autoStart);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (!(player.openContainer instanceof ContainerCraftAmount container)) {
            return;
        }
        if (container.windowId != this.windowId) {
            return;
        }
        if (this.amount <= 0 || this.amount > ContainerCraftAmount.MAX_AUTO_CRAFT_AMOUNT) {
            return;
        }
        container.confirm(this.amount, this.craftMissingAmount, this.autoStart);
    }
}
