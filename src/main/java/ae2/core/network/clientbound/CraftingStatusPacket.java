package ae2.core.network.clientbound;

import ae2.client.gui.me.crafting.GuiCraftingCPU;
import ae2.container.me.crafting.CraftingStatus;
import ae2.core.network.ClientboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class CraftingStatusPacket extends ClientboundPacket {
    private int containerId;
    private CraftingStatus status;

    public CraftingStatusPacket() {
    }

    public CraftingStatusPacket(int containerId, CraftingStatus status) {
        this.containerId = containerId;
        this.status = status;
    }

    @Override
    protected void read(ByteBuf buf) {
        try {
            var data = new PacketBuffer(buf);
            this.containerId = data.readInt();
            this.status = CraftingStatus.read(data);
        } catch (RuntimeException e) {
            this.containerId = -1;
            this.status = CraftingStatus.EMPTY;
            buf.skipBytes(buf.readableBytes());
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        var data = new PacketBuffer(buf);
        data.writeInt(containerId);
        status.write(data);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.player.openContainer == null
            || minecraft.player.openContainer.windowId != containerId) {
            return;
        }

        GuiScreen screen = minecraft.currentScreen;
        if (screen instanceof GuiCraftingCPU<?> cpuScreen) {
            cpuScreen.postUpdate(status);
        }
    }
}
