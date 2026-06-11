package ae2.hooks;

import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.RequestClosestMeteoritePacket;
import ae2.util.MeteoriteCompassSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CompassManager {
    public static final CompassManager INSTANCE = new CompassManager();

    private static final long REFRESH_CACHE_AFTER = 30000L;
    private static final long EXPIRE_CACHE_AFTER = 60000L;

    private final Map<WindowKey, CachedWindow> windows = new ConcurrentHashMap<>();
    private long lastRequestTick = Long.MIN_VALUE;
    private int requestsThisTick;
    private long nextRequestId;

    private CompassManager() {
    }

    private static long distanceSq(int x1, int z1, int x2, int z2) {
        long dx = (long) x1 - x2;
        long dz = (long) z1 - z2;
        return dx * dx + dz * dz;
    }

    @Nullable
    private static Integer getCurrentDimension() {
        var minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null) {
            return null;
        }
        return minecraft.world.provider.getDimension();
    }

    private static int getWindowIndex(int searchRegionX, int searchRegionZ, int regionX, int regionZ) {
        int dx = regionX - searchRegionX;
        int dz = regionZ - searchRegionZ;
        int radius = MeteoriteCompassSearch.PREFETCH_RADIUS;
        if (Math.abs(dx) > radius || Math.abs(dz) > radius) {
            return -1;
        }
        int width = radius * 2 + 1;
        return (dx + radius) * width + dz + radius;
    }

    public void postResult(int dimension, int originChunkX, int originChunkZ, int searchRegionX, int searchRegionZ,
                           ChunkPos requestedRegion, long requestId, @Nullable BlockPos closestMeteorite) {
        Integer currentDimension = getCurrentDimension();
        if (currentDimension == null || currentDimension != dimension) {
            return;
        }
        if (MeteoriteCompassSearch.getRegion(originChunkX) != searchRegionX
            || MeteoriteCompassSearch.getRegion(originChunkZ) != searchRegionZ) {
            return;
        }
        var minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null) {
            return;
        }
        ChunkPos currentChunk = new ChunkPos(minecraft.player.getPosition());
        if (MeteoriteCompassSearch.getRegion(currentChunk.x) != searchRegionX
            || MeteoriteCompassSearch.getRegion(currentChunk.z) != searchRegionZ) {
            return;
        }
        int index = getWindowIndex(searchRegionX, searchRegionZ, requestedRegion.x, requestedRegion.z);
        if (index < 0) {
            return;
        }

        var window = this.windows.get(new WindowKey(dimension, searchRegionX, searchRegionZ));
        if (window == null) {
            return;
        }
        var request = window.requests[index];
        if (request == null || request.requestId != requestId) {
            return;
        }

        long now = System.currentTimeMillis();
        window.results[index] = new CachedResult(closestMeteorite, now);
        window.lastAccess = now;
    }

    public void clear() {
        this.windows.clear();
    }

    @Nullable
    public BlockPos getClosestMeteorite(ChunkPos chunkPos, boolean prefetch) {
        return getClosestMeteorite(new BlockPos(chunkPos.x * 16 + 8, 0, chunkPos.z * 16 + 8), prefetch);
    }

    @Nullable
    public BlockPos getClosestMeteorite(BlockPos pos, boolean prefetch) {
        Integer dimension = getCurrentDimension();
        if (dimension == null) {
            return null;
        }

        var now = System.currentTimeMillis();
        expireOld(now);

        var minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null) {
            return null;
        }
        var originChunk = new ChunkPos(minecraft.player.getPosition());
        int regionX = MeteoriteCompassSearch.getRegion(originChunk.x);
        int regionZ = MeteoriteCompassSearch.getRegion(originChunk.z);
        var window = this.windows.computeIfAbsent(new WindowKey(dimension, regionX, regionZ),
            ignored -> new CachedWindow());
        window.lastAccess = now;
        getClosestMeteoriteForRegion(window, originChunk, regionX, regionZ, regionX, regionZ, now);

        if (prefetch) {
            // Prefetch the full 5x5 window of 24-chunk compass/search regions around the player.
            for (int dx = -MeteoriteCompassSearch.PREFETCH_RADIUS; dx <= MeteoriteCompassSearch.PREFETCH_RADIUS; dx++) {
                for (int dz = -MeteoriteCompassSearch.PREFETCH_RADIUS; dz <= MeteoriteCompassSearch.PREFETCH_RADIUS; dz++) {
                    if (dx != 0 || dz != 0) {
                        getClosestMeteoriteForRegion(window, originChunk, regionX, regionZ, regionX + dx,
                            regionZ + dz, now);
                    }
                }
            }
        }

        return findClosestKnownResult(window, pos);
    }

    private void getClosestMeteoriteForRegion(CachedWindow window, ChunkPos originChunk, int searchRegionX,
                                              int searchRegionZ, int regionX, int regionZ, long now) {
        int index = getWindowIndex(searchRegionX, searchRegionZ, regionX, regionZ);
        if (index < 0) {
            return;
        }
        var cached = window.requests[index];
        boolean request = cached == null || now - cached.received >= REFRESH_CACHE_AFTER;

        if (request) {
            if (!acquireRequestBudget()) {
                return;
            }
            long requestId = ++this.nextRequestId;
            window.requests[index] = new CachedRequest(now, requestId);
            InitNetwork.sendToServer(new RequestClosestMeteoritePacket(originChunk, regionX, regionZ, searchRegionX,
                searchRegionZ, requestId));
        }
    }

    private boolean acquireRequestBudget() {
        var minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null) {
            return false;
        }

        long currentTick = minecraft.world.getTotalWorldTime();
        if (this.lastRequestTick != currentTick) {
            this.lastRequestTick = currentTick;
            this.requestsThisTick = 0;
        }
        if (this.requestsThisTick >= MeteoriteCompassSearch.MAX_PREFETCH_REQUESTS) {
            return false;
        }
        this.requestsThisTick++;
        return true;
    }

    private void expireOld(long now) {
        this.windows.entrySet().removeIf(entry -> now - entry.getValue().lastAccess > EXPIRE_CACHE_AFTER);
    }

    @Nullable
    private BlockPos findClosestKnownResult(CachedWindow window, BlockPos origin) {
        long closestDistance = Long.MAX_VALUE;
        BlockPos result = null;
        for (var cached : window.results) {
            if (cached == null || cached.closestMeteoritePos == null) {
                continue;
            }

            var closestPos = cached.closestMeteoritePos;
            long distance = distanceSq(origin.getX(), origin.getZ(), closestPos.getX(), closestPos.getZ());
            if (distance < closestDistance) {
                closestDistance = distance;
                result = closestPos;
            }
        }
        return result;
    }

    private record WindowKey(int dimension, int searchRegionX, int searchRegionZ) {
    }

    private static final class CachedWindow {
        private final CachedRequest[] requests = new CachedRequest[MeteoriteCompassSearch.MAX_PREFETCH_REQUESTS];
        private final CachedResult[] results = new CachedResult[MeteoriteCompassSearch.MAX_PREFETCH_REQUESTS];
        private long lastAccess;
    }

    private record CachedRequest(long received, long requestId) {
    }

    private record CachedResult(@Nullable BlockPos closestMeteoritePos, long received) {
    }
}
