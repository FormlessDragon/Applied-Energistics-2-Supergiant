package ae2.core.network.clientbound;

import ae2.core.network.ClientboundPacket;
import ae2.hooks.CompassManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.Optional;

public class CompassResponsePacket extends ClientboundPacket {
    private ChunkPos pos;
    private Optional<BlockPos> closestMeteorite = Optional.empty();

    public CompassResponsePacket() {
    }

    public CompassResponsePacket(ChunkPos pos, Optional<BlockPos> closestMeteorite) {
        this.pos = pos;
        this.closestMeteorite = closestMeteorite;
    }

    @Override
    protected void read(io.netty.buffer.ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        this.pos = new ChunkPos(packetBuffer.readInt(), packetBuffer.readInt());
        this.closestMeteorite = packetBuffer.readBoolean() ? Optional.of(new BlockPos(packetBuffer.readInt(),
            packetBuffer.readInt(), packetBuffer.readInt())) : Optional.empty();
    }

    @Override
    protected void write(io.netty.buffer.ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeInt(this.pos.x);
        packetBuffer.writeInt(this.pos.z);
        packetBuffer.writeBoolean(this.closestMeteorite.isPresent());
        this.closestMeteorite.ifPresent(blockPos -> {
            packetBuffer.writeInt(blockPos.getX());
            packetBuffer.writeInt(blockPos.getY());
            packetBuffer.writeInt(blockPos.getZ());
        });
    }

    @Override
    public void handleClient(Minecraft minecraft) {
        CompassManager.INSTANCE.postResult(this.pos, this.closestMeteorite.orElse(null));
    }
}
