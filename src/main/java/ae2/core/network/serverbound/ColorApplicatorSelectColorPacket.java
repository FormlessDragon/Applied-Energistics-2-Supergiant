package ae2.core.network.serverbound;

import ae2.api.util.AEColor;
import ae2.core.network.ServerboundPacket;
import ae2.items.tools.powered.ColorApplicatorItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import javax.annotation.Nullable;

public class ColorApplicatorSelectColorPacket extends ServerboundPacket {

    @Nullable
    private AEColor color;

    public ColorApplicatorSelectColorPacket() {
    }

    public ColorApplicatorSelectColorPacket(@Nullable AEColor color) {
        this.color = color;
    }

    private static void switchColor(ItemStack stack, @Nullable AEColor color) {
        if (!stack.isEmpty() && stack.getItem() instanceof ColorApplicatorItem colorApplicator) {
            colorApplicator.setActiveColor(stack, color);
        }
    }

    @Override
    protected void read(ByteBuf buf) {
        PacketBuffer data = new PacketBuffer(buf);
        this.color = data.readBoolean() ? data.readEnumValue(AEColor.class) : null;
    }

    @Override
    protected void write(ByteBuf buf) {
        PacketBuffer data = new PacketBuffer(buf);
        data.writeBoolean(this.color != null);
        if (this.color != null) {
            data.writeEnumValue(this.color);
        }
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        switchColor(player.getHeldItemMainhand(), this.color);
        switchColor(player.getHeldItemOffhand(), this.color);
    }
}
