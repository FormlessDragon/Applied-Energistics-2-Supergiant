package ae2.core.network.clientbound;

import ae2.container.implementations.ContainerNetworkStatus;
import ae2.container.networking.NetworkStatus;
import ae2.core.network.ClientboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


public class NetworkStatusPacket extends ClientboundPacket {
    private int windowId = -1;
    private long sourceGeneration;
    private long snapshotRevision;
    private int pageIndex;
    private int pageCount = 1;
    private boolean oversize;
    private NetworkStatus status;
    private boolean canExportGrid;

    public NetworkStatusPacket() {
    }

    public NetworkStatusPacket(NetworkStatus status, boolean canExportGrid) {
        this(-1, 0, 0, 0, 1, false, status, canExportGrid);
    }

    public NetworkStatusPacket(int windowId, long sourceGeneration, long snapshotRevision,
                               NetworkStatus status, boolean canExportGrid) {
        this(windowId, sourceGeneration, snapshotRevision, 0, 1, false, status, canExportGrid);
    }

    public NetworkStatusPacket(int windowId, long sourceGeneration, long snapshotRevision,
                               int pageIndex, int pageCount, boolean oversize,
                               NetworkStatus status, boolean canExportGrid) {
        this.windowId = windowId;
        this.sourceGeneration = sourceGeneration;
        this.snapshotRevision = snapshotRevision;
        this.pageIndex = pageIndex;
        this.pageCount = pageCount;
        this.oversize = oversize;
        this.status = status;
        this.canExportGrid = canExportGrid;
    }

    @Override
    protected void read(ByteBuf buf) {
        try {
            PacketBuffer packetBuffer = new PacketBuffer(buf);
            this.windowId = packetBuffer.readVarInt();
            this.sourceGeneration = packetBuffer.readVarLong();
            this.snapshotRevision = packetBuffer.readVarLong();
            this.pageIndex = packetBuffer.readVarInt();
            this.pageCount = packetBuffer.readVarInt();
            this.oversize = packetBuffer.readBoolean();
            this.status = this.oversize ? null : NetworkStatus.read(packetBuffer);
            this.canExportGrid = packetBuffer.readBoolean();
            if (this.pageCount <= 0 || this.pageIndex < 0 || this.pageIndex >= this.pageCount
                || this.oversize && (this.pageIndex != 0 || this.pageCount != 1)
                || packetBuffer.isReadable()) {
                throw new IllegalArgumentException("Invalid network status page identity");
            }
        } catch (RuntimeException e) {
            this.status = null;
            this.windowId = -1;
            this.sourceGeneration = 0;
            this.snapshotRevision = 0;
            this.pageIndex = 0;
            this.pageCount = 0;
            this.oversize = false;
            this.canExportGrid = false;
            buf.skipBytes(buf.readableBytes());
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeVarInt(this.windowId);
        packetBuffer.writeVarLong(this.sourceGeneration);
        packetBuffer.writeVarLong(this.snapshotRevision);
        packetBuffer.writeVarInt(this.pageIndex);
        packetBuffer.writeVarInt(this.pageCount);
        packetBuffer.writeBoolean(this.oversize);
        if (!this.oversize) {
            status.write(packetBuffer);
        }
        packetBuffer.writeBoolean(this.canExportGrid);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (minecraft.player != null && minecraft.player.openContainer instanceof ContainerNetworkStatus container
            && container.acceptStatusPage(this.windowId, this.sourceGeneration, this.snapshotRevision,
            this.pageIndex, this.pageCount, this.oversize, this.status, this.canExportGrid)) {
        }
    }
}
