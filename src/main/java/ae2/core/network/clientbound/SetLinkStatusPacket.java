package ae2.core.network.clientbound;

import ae2.api.storage.ILinkStatus;
import ae2.api.storage.LinkStatus;
import ae2.container.guisync.ILinkStatusAwareContainer;
import ae2.core.network.ClientboundPacket;
import ae2.text.TextComponents;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class SetLinkStatusPacket extends ClientboundPacket {
    private ILinkStatus linkStatus;

    public SetLinkStatusPacket() {
    }

    public SetLinkStatusPacket(ILinkStatus linkStatus) {
        this.linkStatus = linkStatus;
    }

    @Override
    protected void read(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        var connected = packetBuffer.readBoolean();
        ITextComponent statusDescription = TextComponents.readFromPacket(packetBuffer);
        this.linkStatus = new LinkStatus(connected, statusDescription);
    }

    @Override
    protected void write(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeBoolean(this.linkStatus.connected());
        var statusDescription = this.linkStatus.statusDescription();
        TextComponents.writeToPacket(packetBuffer, statusDescription);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (minecraft.player != null && minecraft.player.openContainer instanceof ILinkStatusAwareContainer container) {
            container.setLinkStatus(this.linkStatus);
        }
    }
}
