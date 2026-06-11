package ae2.core.network.serverbound;

import ae2.api.crafting.PatternDetailsHelper;
import ae2.container.me.common.ContainerMEStorage;
import ae2.core.network.InitNetwork;
import ae2.core.network.ServerboundPacket;
import ae2.core.network.clientbound.RecursiveIngredientReserveAmountPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

public class SetRecursiveIngredientReserveAmountPacket extends ServerboundPacket {
    private int windowId;
    private long amount;

    public SetRecursiveIngredientReserveAmountPacket() {
    }

    public SetRecursiveIngredientReserveAmountPacket(int windowId, long amount) {
        this.windowId = windowId;
        this.amount = amount;
    }

    @Override
    protected void read(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        this.windowId = packetBuffer.readInt();
        this.amount = packetBuffer.readLong();
        if (packetBuffer.isReadable()) {
            throw new IllegalArgumentException("Trailing bytes in recursive ingredient reserve amount packet");
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeInt(this.windowId);
        packetBuffer.writeLong(this.amount);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (!(player.openContainer instanceof ContainerMEStorage container)) {
            return;
        }
        if (container.windowId != this.windowId) {
            return;
        }

        var gridNode = container.getGridNode();
        if (gridNode == null || !gridNode.isActive()) {
            return;
        }

        long clampedAmount = Math.clamp(this.amount, 0, PatternDetailsHelper.MAX_PROCESSING_PATTERN_AMOUNT);
        gridNode.grid().getCraftingService().setRecursiveIngredientReserveAmount(clampedAmount);
        container.setRecursiveIngredientReserveAmount(clampedAmount);
        InitNetwork.CHANNEL.sendTo(new RecursiveIngredientReserveAmountPacket(clampedAmount), player);
    }
}
