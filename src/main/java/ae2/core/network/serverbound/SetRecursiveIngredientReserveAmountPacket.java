package ae2.core.network.serverbound;

import ae2.container.me.common.ContainerMEStorage;
import ae2.core.network.InitNetwork;
import ae2.core.network.ServerboundPacket;
import ae2.core.network.clientbound.RecursiveIngredientReserveAmountPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

public class SetRecursiveIngredientReserveAmountPacket extends ServerboundPacket {
    private long amount;

    public SetRecursiveIngredientReserveAmountPacket() {
    }

    public SetRecursiveIngredientReserveAmountPacket(long amount) {
        this.amount = amount;
    }

    @Override
    protected void read(ByteBuf buf) {
        this.amount = new PacketBuffer(buf).readLong();
    }

    @Override
    protected void write(ByteBuf buf) {
        new PacketBuffer(buf).writeLong(this.amount);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (!(player.openContainer instanceof ContainerMEStorage container)) {
            return;
        }

        var gridNode = container.getGridNode();
        if (gridNode == null || !gridNode.isActive()) {
            return;
        }

        long clampedAmount = Math.max(0, this.amount);
        gridNode.grid().getCraftingService().setRecursiveIngredientReserveAmount(clampedAmount);
        container.setRecursiveIngredientReserveAmount(clampedAmount);
        InitNetwork.CHANNEL.sendTo(new RecursiveIngredientReserveAmountPacket(clampedAmount), player);
    }
}
