package ae2.hooks;

import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.RequestClosestMeteoritePacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CompassManager {
    public static final CompassManager INSTANCE = new CompassManager();

    private static final long REFRESH_CACHE_AFTER = 30000L;
    private static final long EXPIRE_CACHE_AFTER = 60000L;

    private final Map<Long, CachedResult> requests = new ConcurrentHashMap<>();

    private CompassManager() {
    }

    private static long asLong(int x, int z) {
        return ((long) x & 0xffffffffL) | (((long) z & 0xffffffffL) << 32);
    }

    private static long distanceSq(int x1, int z1, int x2, int z2) {
        long dx = x1 - x2;
        long dz = z1 - z2;
        return dx * dx + dz * dz;
    }

    public void postResult(ChunkPos requestedPos, @Nullable BlockPos closestMeteorite) {
        this.requests.put(asLong(requestedPos.x, requestedPos.z),
            new CachedResult(closestMeteorite, System.currentTimeMillis()));
    }

    public void clear() {
        this.requests.clear();
    }

    @Nullable
    public BlockPos getClosestMeteorite(ChunkPos chunkPos, boolean prefetch) {
        var now = System.currentTimeMillis();
        expireOld(now);

        var key = asLong(chunkPos.x, chunkPos.z);
        var cached = this.requests.get(key);
        BlockPos result = cached != null ? cached.closestMeteoritePos : null;
        boolean request = cached == null || now - cached.received >= REFRESH_CACHE_AFTER;

        if (result == null) {
            result = findClosestKnownResult(chunkPos);
        }

        if (request) {
            this.requests.put(key, new CachedResult(result, now));
            InitNetwork.sendToServer(new RequestClosestMeteoritePacket(chunkPos));
        }

        if (prefetch) {
            for (int dx = 0; dx < 3; dx++) {
                for (int dz = 0; dz < 3; dz++) {
                    if (dx != 0 || dz != 0) {
                        getClosestMeteorite(new ChunkPos(chunkPos.x + dx, chunkPos.z + dz), false);
                    }
                }
            }
        }

        return result;
    }

    @Nullable
    public BlockPos getClosestMeteorite(BlockPos pos, boolean prefetch) {
        return getClosestMeteorite(new ChunkPos(pos), prefetch);
    }

    private void expireOld(long now) {
        this.requests.entrySet().removeIf(entry -> now - entry.getValue().received > EXPIRE_CACHE_AFTER);
    }

    @Nullable
    private BlockPos findClosestKnownResult(ChunkPos chunkPos) {
        long closestDistance = Long.MAX_VALUE;
        BlockPos result = null;
        for (var entry : this.requests.entrySet()) {
            var closestPos = entry.getValue().closestMeteoritePos;
            if (closestPos != null) {
                long packed = entry.getKey();
                int requestedX = (int) (packed & 0xffffffffL);
                int requestedZ = (int) (packed >>> 32);
                long distance = distanceSq(chunkPos.x, chunkPos.z, requestedX, requestedZ);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    result = closestPos;
                }
            }
        }
        return result;
    }

    private record CachedResult(@Nullable BlockPos closestMeteoritePos, long received) {
    }
}
