package ae2.core.network.clientbound;

import ae2.container.implementations.ContainerAdvancedMemoryCard;
import ae2.core.network.ClientboundPacket;
import ae2.items.tools.advancedmemorycard.AdvancedMemoryCardP2PSnapshot;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class AdvancedMemoryCardP2PSnapshotPacket extends ClientboundPacket {
    private AdvancedMemoryCardP2PSnapshot snapshot = new AdvancedMemoryCardP2PSnapshot(java.util.List.of());

    public AdvancedMemoryCardP2PSnapshotPacket() {
    }

    public AdvancedMemoryCardP2PSnapshotPacket(AdvancedMemoryCardP2PSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    protected void read(ByteBuf buf) {
        this.snapshot = AdvancedMemoryCardP2PSnapshot.read(new PacketBuffer(buf));
    }

    @Override
    protected void write(ByteBuf buf) {
        this.snapshot.write(new PacketBuffer(buf));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (minecraft.player != null && minecraft.player.openContainer instanceof ContainerAdvancedMemoryCard container) {
            container.setSnapshot(this.snapshot);
        }
    }
}
