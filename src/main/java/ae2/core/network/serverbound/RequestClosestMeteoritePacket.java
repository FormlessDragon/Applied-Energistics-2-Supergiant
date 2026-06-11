package ae2.core.network.serverbound;

import ae2.core.network.InitNetwork;
import ae2.core.network.ServerboundPacket;
import ae2.core.network.clientbound.CompassResponsePacket;
import ae2.server.services.compass.ServerCompassService;
import ae2.util.MeteoriteCompassSearch;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RequestClosestMeteoritePacket extends ServerboundPacket {
    private static final Cache<UUID, RequestWindow> REQUEST_WINDOWS = CacheBuilder.newBuilder()
                                                                                  .expireAfterAccess(1,
                                                                                      TimeUnit.MINUTES)
                                                                                  .build();

    private ChunkPos originChunk;
    private ChunkPos requestedRegion;
    private int searchRegionX;
    private int searchRegionZ;
    private long requestId;

    public RequestClosestMeteoritePacket() {
    }

    public RequestClosestMeteoritePacket(ChunkPos originChunk, int regionX, int regionZ, int searchRegionX,
                                         int searchRegionZ, long requestId) {
        this.originChunk = originChunk;
        this.requestedRegion = new ChunkPos(regionX, regionZ);
        this.searchRegionX = searchRegionX;
        this.searchRegionZ = searchRegionZ;
        this.requestId = requestId;
    }

    private static boolean acquireRequestBudget(EntityPlayerMP player) {
        UUID playerId = player.getUniqueID();
        long currentTick = player.getServerWorld().getTotalWorldTime();
        RequestWindow window = REQUEST_WINDOWS.getIfPresent(playerId);
        if (window == null || window.tick != currentTick) {
            REQUEST_WINDOWS.put(playerId, new RequestWindow(currentTick, 1));
            return true;
        }
        if (window.requests >= MeteoriteCompassSearch.MAX_PREFETCH_REQUESTS) {
            return false;
        }
        window.requests++;
        return true;
    }

    @Override
    protected void read(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        this.originChunk = new ChunkPos(packetBuffer.readInt(), packetBuffer.readInt());
        this.requestedRegion = new ChunkPos(packetBuffer.readInt(), packetBuffer.readInt());
        this.searchRegionX = packetBuffer.readInt();
        this.searchRegionZ = packetBuffer.readInt();
        this.requestId = packetBuffer.readLong();
        if (packetBuffer.isReadable()) {
            throw new IllegalArgumentException("Trailing closest meteorite request packet payload bytes: "
                + packetBuffer.readableBytes());
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeInt(this.originChunk.x);
        packetBuffer.writeInt(this.originChunk.z);
        packetBuffer.writeInt(this.requestedRegion.x);
        packetBuffer.writeInt(this.requestedRegion.z);
        packetBuffer.writeInt(this.searchRegionX);
        packetBuffer.writeInt(this.searchRegionZ);
        packetBuffer.writeLong(this.requestId);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (this.originChunk == null || this.requestedRegion == null) {
            return;
        }
        BlockPos playerPos = player.getPosition();
        ChunkPos playerChunk = new ChunkPos(playerPos);
        if (Math.abs((long) this.originChunk.x - playerChunk.x) > 1
            || Math.abs((long) this.originChunk.z - playerChunk.z) > 1) {
            return;
        }
        int playerRegionX = MeteoriteCompassSearch.getRegion(playerChunk.x);
        int playerRegionZ = MeteoriteCompassSearch.getRegion(playerChunk.z);
        if (this.searchRegionX != playerRegionX || this.searchRegionZ != playerRegionZ) {
            return;
        }
        long regionDistanceX = (long) this.requestedRegion.x - playerRegionX;
        long regionDistanceZ = (long) this.requestedRegion.z - playerRegionZ;
        if (Math.abs(regionDistanceX) > MeteoriteCompassSearch.PREFETCH_RADIUS
            || Math.abs(regionDistanceZ) > MeteoriteCompassSearch.PREFETCH_RADIUS) {
            return;
        }
        if (!acquireRequestBudget(player)) {
            return;
        }
        var level = player.getServerWorld();
        int dimension = level.provider.getDimension();
        boolean indexLoadedChunks = regionDistanceX == 0 && regionDistanceZ == 0;
        var result = ServerCompassService.getClosestMeteorite(level, this.requestedRegion.x,
            this.requestedRegion.z, playerPos, indexLoadedChunks);
        InitNetwork.sendToClient(player, new CompassResponsePacket(dimension, this.originChunk, this.searchRegionX,
            this.searchRegionZ, this.requestedRegion, this.requestId, result.orElse(null)));
    }

    private static final class RequestWindow {
        private final long tick;
        private int requests;

        private RequestWindow(long tick, int requests) {
            this.tick = tick;
            this.requests = requests;
        }
    }
}
