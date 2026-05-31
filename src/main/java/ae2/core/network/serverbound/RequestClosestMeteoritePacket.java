package ae2.core.network.serverbound;

import ae2.core.network.ServerboundPacket;
import ae2.core.network.clientbound.CompassResponsePacket;
import ae2.server.services.compass.ServerCompassService;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.ChunkPos;

public class RequestClosestMeteoritePacket extends ServerboundPacket {
    private ChunkPos pos;

    public RequestClosestMeteoritePacket() {
    }

    public RequestClosestMeteoritePacket(ChunkPos pos) {
        this.pos = pos;
    }

    @Override
    protected void read(io.netty.buffer.ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        this.pos = new ChunkPos(packetBuffer.readInt(), packetBuffer.readInt());
    }

    @Override
    protected void write(io.netty.buffer.ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeInt(this.pos.x);
        packetBuffer.writeInt(this.pos.z);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        var result = ServerCompassService.getClosestMeteorite(player.getServerWorld(), this.pos);
        ae2.core.network.InitNetwork.sendToClient(player, new CompassResponsePacket(this.pos, result));
    }
}
