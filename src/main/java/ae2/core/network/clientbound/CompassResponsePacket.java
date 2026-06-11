package ae2.core.network.clientbound;

import ae2.core.network.ClientboundPacket;
import ae2.hooks.CompassManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.jetbrains.annotations.Nullable;

public class CompassResponsePacket extends ClientboundPacket {
    private int dimension;
    private ChunkPos originChunk;
    private int searchRegionX;
    private int searchRegionZ;
    private ChunkPos requestedRegion;
    private long requestId;
    @Nullable
    private BlockPos closestMeteorite;

    public CompassResponsePacket() {
    }

    public CompassResponsePacket(int dimension, ChunkPos originChunk, int searchRegionX, int searchRegionZ,
                                 ChunkPos requestedRegion, long requestId, @Nullable BlockPos closestMeteorite) {
        this.dimension = dimension;
        this.originChunk = originChunk;
        this.searchRegionX = searchRegionX;
        this.searchRegionZ = searchRegionZ;
        this.requestedRegion = requestedRegion;
        this.requestId = requestId;
        this.closestMeteorite = closestMeteorite;
    }

    @Override
    protected void read(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        this.dimension = packetBuffer.readInt();
        this.originChunk = new ChunkPos(packetBuffer.readInt(), packetBuffer.readInt());
        this.searchRegionX = packetBuffer.readInt();
        this.searchRegionZ = packetBuffer.readInt();
        this.requestedRegion = new ChunkPos(packetBuffer.readInt(), packetBuffer.readInt());
        this.requestId = packetBuffer.readLong();
        this.closestMeteorite = packetBuffer.readBoolean() ? new BlockPos(packetBuffer.readInt(),
            packetBuffer.readInt(), packetBuffer.readInt()) : null;
        if (packetBuffer.isReadable()) {
            throw new IllegalArgumentException("Trailing compass response packet payload bytes: "
                + packetBuffer.readableBytes());
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeInt(this.dimension);
        packetBuffer.writeInt(this.originChunk.x);
        packetBuffer.writeInt(this.originChunk.z);
        packetBuffer.writeInt(this.searchRegionX);
        packetBuffer.writeInt(this.searchRegionZ);
        packetBuffer.writeInt(this.requestedRegion.x);
        packetBuffer.writeInt(this.requestedRegion.z);
        packetBuffer.writeLong(this.requestId);
        packetBuffer.writeBoolean(this.closestMeteorite != null);
        if (this.closestMeteorite != null) {
            packetBuffer.writeInt(this.closestMeteorite.getX());
            packetBuffer.writeInt(this.closestMeteorite.getY());
            packetBuffer.writeInt(this.closestMeteorite.getZ());
        }
    }

    @Override
    public void handleClient(Minecraft minecraft) {
        CompassManager.INSTANCE.postResult(this.dimension, this.originChunk.x, this.originChunk.z, this.searchRegionX,
            this.searchRegionZ, this.requestedRegion, this.requestId, this.closestMeteorite);
    }
}
