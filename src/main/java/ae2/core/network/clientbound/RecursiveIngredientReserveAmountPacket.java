package ae2.core.network.clientbound;

import ae2.container.me.common.ContainerMEStorage;
import ae2.core.network.ClientboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class RecursiveIngredientReserveAmountPacket extends ClientboundPacket {
    private long amount;

    public RecursiveIngredientReserveAmountPacket() {
    }

    public RecursiveIngredientReserveAmountPacket(long amount) {
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
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (minecraft.player != null && minecraft.player.openContainer instanceof ContainerMEStorage container) {
            container.setRecursiveIngredientReserveAmount(this.amount);
        }
    }
}
