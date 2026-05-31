package ae2.core.network.serverbound;

import ae2.core.network.ServerboundPacket;
import ae2.helpers.IMouseWheelItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

public class MouseWheelPacket extends ServerboundPacket {

    private boolean wheelUp;

    public MouseWheelPacket() {
    }

    public MouseWheelPacket(boolean wheelUp) {
        this.wheelUp = wheelUp;
    }

    @Override
    protected void read(ByteBuf buf) {
        this.wheelUp = new PacketBuffer(buf).readBoolean();
    }

    @Override
    protected void write(ByteBuf buf) {
        new PacketBuffer(buf).writeBoolean(this.wheelUp);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (handleStack(player.getHeldItemMainhand())) {
            return;
        }

        handleStack(player.getHeldItemOffhand());
    }

    private boolean handleStack(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof IMouseWheelItem mouseWheelItem) {
            mouseWheelItem.onWheel(stack, this.wheelUp);
            return true;
        }

        return false;
    }
}
