package appeng.core.network.serverbound;

import appeng.container.implementations.ContainerCraftingCPU;
import appeng.core.network.ServerboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

public class TraceCraftingSupplierPacket extends ServerboundPacket {
    private int windowId;
    private long serial;

    public TraceCraftingSupplierPacket() {
    }

    public TraceCraftingSupplierPacket(int windowId, long serial) {
        this.windowId = windowId;
        this.serial = serial;
    }

    @Override
    protected void read(ByteBuf buf) {
        var data = new PacketBuffer(buf);
        this.windowId = data.readInt();
        this.serial = data.readVarLong();
    }

    @Override
    protected void write(ByteBuf buf) {
        var data = new PacketBuffer(buf);
        data.writeInt(this.windowId);
        data.writeVarLong(this.serial);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (player.openContainer.windowId != this.windowId) {
            return;
        }
        if (player.openContainer instanceof ContainerCraftingCPU container) {
            container.traceSupplierForSerial(this.serial);
        }
    }
}
