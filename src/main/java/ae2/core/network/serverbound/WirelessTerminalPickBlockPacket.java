package ae2.core.network.serverbound;

import ae2.container.me.common.MEIngredientActions;
import ae2.core.network.ServerboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import java.io.IOException;

public class WirelessTerminalPickBlockPacket extends ServerboundPacket {
    private ItemStack target = ItemStack.EMPTY;
    private int hotbarSlot;

    public WirelessTerminalPickBlockPacket() {
    }

    public WirelessTerminalPickBlockPacket(ItemStack target, int hotbarSlot) {
        this.target = target.copy();
        this.hotbarSlot = hotbarSlot;
    }

    @Override
    protected void read(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        try {
            this.target = packetBuffer.readItemStack();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read wireless pick block stack", e);
        }
        this.hotbarSlot = packetBuffer.readInt();
    }

    @Override
    protected void write(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeItemStack(this.target);
        packetBuffer.writeInt(this.hotbarSlot);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (this.target.isEmpty()) {
            return;
        }

        MEIngredientActions.retrieveToHotbarFromWirelessTerminals(player, this.target, this.hotbarSlot);
    }
}
