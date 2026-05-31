package ae2.core.network.serverbound;

import ae2.container.AEBaseContainer;
import ae2.core.network.ServerboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

public class SwapSlotsPacket extends ServerboundPacket {
    private int windowId = -1;
    private int slotA;
    private int slotB;

    public SwapSlotsPacket() {
    }

    public SwapSlotsPacket(int slotA, int slotB) {
        this(-1, slotA, slotB);
    }

    public SwapSlotsPacket(int windowId, int slotA, int slotB) {
        this.windowId = windowId;
        this.slotA = slotA;
        this.slotB = slotB;
    }

    @Override
    protected void read(ByteBuf buf) {
        PacketBuffer data = new PacketBuffer(buf);
        this.windowId = data.readInt();
        this.slotA = data.readInt();
        this.slotB = data.readInt();
    }

    @Override
    protected void write(ByteBuf buf) {
        PacketBuffer data = new PacketBuffer(buf);
        data.writeInt(this.windowId);
        data.writeInt(this.slotA);
        data.writeInt(this.slotB);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if ((this.windowId < 0 || player.openContainer.windowId == this.windowId)
            && player.openContainer instanceof AEBaseContainer container) {
            container.swapSlotContents(this.slotA, this.slotB);
        }
    }
}
