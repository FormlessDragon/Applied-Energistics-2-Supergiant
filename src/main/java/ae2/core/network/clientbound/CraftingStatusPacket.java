package ae2.core.network.clientbound;

import ae2.container.implementations.ContainerCraftingCPU;
import ae2.container.me.crafting.CraftingStatus;
import ae2.core.network.ClientboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class CraftingStatusPacket extends ClientboundPacket {
    private int containerId;
    private long cpuEpoch;
    private long statusRevision;
    private CraftingStatus status;

    public CraftingStatusPacket() {
    }

    public CraftingStatusPacket(int containerId, long cpuEpoch, long statusRevision, CraftingStatus status) {
        this.containerId = containerId;
        this.cpuEpoch = cpuEpoch;
        this.statusRevision = statusRevision;
        this.status = status;
    }

    @Override
    protected void read(ByteBuf buf) {
        try {
            var data = new PacketBuffer(buf);
            this.containerId = data.readInt();
            this.cpuEpoch = data.readVarLong();
            this.statusRevision = data.readVarLong();
            this.status = CraftingStatus.read(data);
        } catch (RuntimeException e) {
            this.containerId = -1;
            this.cpuEpoch = 0;
            this.statusRevision = 0;
            this.status = CraftingStatus.EMPTY;
            buf.skipBytes(buf.readableBytes());
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        var data = new PacketBuffer(buf);
        data.writeInt(containerId);
        data.writeVarLong(cpuEpoch);
        data.writeVarLong(statusRevision);
        status.write(data);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.player.openContainer == null
            || minecraft.player.openContainer.windowId != containerId) {
            return;
        }

        if (minecraft.player.openContainer instanceof ContainerCraftingCPU cpuContainer) {
            cpuContainer.acceptClientStatus(cpuEpoch, statusRevision, status);
        }
    }
}
