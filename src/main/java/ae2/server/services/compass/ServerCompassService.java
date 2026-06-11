package ae2.server.services.compass;

import ae2.core.definitions.AEBlocks;
import ae2.core.network.InitNetwork;
import ae2.core.network.clientbound.ClearCompassCachePacket;
import ae2.util.MeteoriteCompassSearch;
import ae2.worldgen.meteorite.MeteoritesWorldData;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class ServerCompassService {
    private static final int LOADED_CHUNK_INDEX_RADIUS = 8;
    private static final Cache<ChunkIndexQuery, Boolean> CHUNK_INDEX_ATTEMPT_CACHE = CacheBuilder.newBuilder()
                                                                                                 .maximumSize(4096)
                                                                                                 .expireAfterWrite(5,
                                                                                                     TimeUnit.SECONDS)
                                                                                                 .build();
    private static final LoadingCache<RegionQuery, ObjectList<BlockPos>> METEORITE_TARGET_CACHE =
        CacheBuilder.newBuilder()
                    .maximumSize(512)
                    .expireAfterWrite(5, TimeUnit.SECONDS)
                    .build(new CacheLoader<>() {
                        @Override
                        public @NotNull ObjectList<BlockPos> load(@NotNull RegionQuery query) {
                            return findMeteoriteTargets(query.level, query.regionX, query.regionZ);
                        }
                    });

    public static Optional<BlockPos> getClosestMeteorite(WorldServer level, int regionX, int regionZ, BlockPos origin,
                                                         boolean indexLoadedChunks) {
        if (indexLoadedChunks) {
            ensureLoadedChunksIndexed(level, regionX, regionZ, origin);
        }
        return findClosest(METEORITE_TARGET_CACHE.getUnchecked(new RegionQuery(level, regionX, regionZ)), origin);
    }

    public static void clearCache() {
        CHUNK_INDEX_ATTEMPT_CACHE.invalidateAll();
        CHUNK_INDEX_ATTEMPT_CACHE.cleanUp();
        METEORITE_TARGET_CACHE.invalidateAll();
        METEORITE_TARGET_CACHE.cleanUp();
    }

    public static void clearCache(WorldServer level) {
        CHUNK_INDEX_ATTEMPT_CACHE.asMap().keySet().removeIf(query -> query.level == level);
        CHUNK_INDEX_ATTEMPT_CACHE.cleanUp();
        METEORITE_TARGET_CACHE.asMap().keySet().removeIf(query -> query.level == level);
        METEORITE_TARGET_CACHE.cleanUp();
    }

    public static void clearCacheAndNotifyClients(WorldServer level) {
        clearCache(level);
        notifyClients(level);
    }

    private static ObjectList<BlockPos> findMeteoriteTargets(WorldServer level, int regionX, int regionZ) {
        return ObjectLists.unmodifiable(MeteoritesWorldData.get(level).getMeteoriteTargetsInCompassRegion(regionX,
            regionZ, pos -> shouldUseGeneratedMeteoriteTarget(level, pos)));
    }

    private static Optional<BlockPos> findClosest(ObjectList<BlockPos> candidates, BlockPos origin) {
        long closestDistance = Long.MAX_VALUE;
        BlockPos closest = null;
        for (BlockPos pos : candidates) {
            long dx = (long) pos.getX() - origin.getX();
            long dz = (long) pos.getZ() - origin.getZ();
            long distance = dx * dx + dz * dz;
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = pos;
            }
        }
        return Optional.ofNullable(closest);
    }

    private static boolean shouldUseGeneratedMeteoriteTarget(WorldServer level, BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        Chunk chunk = level.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
        if (chunk != null) {
            return chunk.getBlockState(pos).getBlock() == AEBlocks.MYSTERIOUS_CUBE.block();
        }

        CompassRegion compassRegion = CompassRegion.get(level, new ChunkPos(chunkX, chunkZ));
        int sectionIndex = pos.getY() >> 4;
        return !compassRegion.isChunkChecked(chunkX, chunkZ)
            || compassRegion.hasCompassTarget(chunkX, chunkZ, sectionIndex);
    }

    private static void ensureLoadedChunksIndexed(WorldServer level, int regionX, int regionZ, BlockPos origin) {
        int minChunkX = MeteoriteCompassSearch.getRegionMinChunk(regionX);
        int maxChunkX = MeteoriteCompassSearch.getRegionMaxChunk(regionX);
        int minChunkZ = MeteoriteCompassSearch.getRegionMinChunk(regionZ);
        int maxChunkZ = MeteoriteCompassSearch.getRegionMaxChunk(regionZ);
        int originChunkX = origin.getX() >> 4;
        int originChunkZ = origin.getZ() >> 4;
        indexLoadedChunks(level,
            Math.max(minChunkX, originChunkX - LOADED_CHUNK_INDEX_RADIUS),
            Math.min(maxChunkX, originChunkX + LOADED_CHUNK_INDEX_RADIUS),
            Math.max(minChunkZ, originChunkZ - LOADED_CHUNK_INDEX_RADIUS),
            Math.min(maxChunkZ, originChunkZ + LOADED_CHUNK_INDEX_RADIUS));
    }

    private static void indexLoadedChunks(WorldServer level, int minChunkX, int maxChunkX, int minChunkZ,
                                          int maxChunkZ) {
        boolean changed = false;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                Chunk chunk = level.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }

                var query = new ChunkIndexQuery(level, chunkX, chunkZ);
                if (CHUNK_INDEX_ATTEMPT_CACHE.getIfPresent(query) != null) {
                    continue;
                }
                CHUNK_INDEX_ATTEMPT_CACHE.put(query, Boolean.TRUE);

                MeteoritesWorldData meteorites = MeteoritesWorldData.get(level);
                CompassRegion compassRegion = CompassRegion.get(level, new ChunkPos(chunkX, chunkZ));
                if (compassRegion.isChunkChecked(chunkX, chunkZ)
                    && !meteorites.hasCompassTargetsInChunk(chunkX, chunkZ)) {
                    continue;
                }

                changed |= updateAreaWithoutNotifying(level, chunk);
            }
        }
        if (changed) {
            clearCacheAndNotifyClients(level);
        }
    }

    public static void updateArea(WorldServer level, Chunk chunk) {
        boolean changed = updateAreaWithoutNotifying(level, chunk);
        if (changed) {
            clearCacheAndNotifyClients(level);
        }
    }

    private static boolean updateAreaWithoutNotifying(WorldServer level, Chunk chunk) {
        boolean changed = false;
        var compassRegion = CompassRegion.get(level, chunk.getPos());
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        for (var i = 0; i < sections.length; i++) {
            changed |= updateArea(level, compassRegion, chunk, i);
        }
        changed |= compassRegion.markChunkChecked(chunk.x, chunk.z);
        return changed;
    }

    public static void notifyBlockChange(WorldServer level, BlockPos pos) {
        Chunk chunk = level.getChunkProvider().getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return;
        }

        var compassRegion = CompassRegion.get(level, chunk.getPos());
        int sectionIndex = pos.getY() >> 4;
        if (sectionIndex < 0 || sectionIndex >= chunk.getBlockStorageArray().length) {
            return;
        }

        boolean changed = updateArea(level, compassRegion, chunk, sectionIndex);
        changed |= compassRegion.markChunkChecked(chunk.x, chunk.z);
        if (changed) {
            clearCacheAndNotifyClients(level);
        }
    }

    private static boolean updateArea(WorldServer level, CompassRegion compassRegion, Chunk chunk, int sectionIndex) {
        int cx = chunk.x;
        int cz = chunk.z;
        var section = chunk.getBlockStorageArray()[sectionIndex];
        if (section == null || section.isEmpty()) {
            boolean changed = compassRegion.setHasCompassTarget(cx, cz, sectionIndex, false);
            changed |= MeteoritesWorldData.get(level)
                                          .syncCompassTargetsInSection(cx, cz, sectionIndex, Collections.emptyList());
            return changed;
        }

        var desiredBlock = AEBlocks.MYSTERIOUS_CUBE.block();
        ObjectList<BlockPos> targets = new ObjectArrayList<>();
        for (int localX = 0; localX < 16; localX++) {
            for (int localY = 0; localY < 16; localY++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    if (section.get(localX, localY, localZ).getBlock() == desiredBlock) {
                        targets.add(new BlockPos((cx << 4) + localX, (sectionIndex << 4) + localY,
                            (cz << 4) + localZ));
                    }
                }
            }
        }
        boolean changed = compassRegion.setHasCompassTarget(cx, cz, sectionIndex, !targets.isEmpty());
        changed |= MeteoritesWorldData.get(level).syncCompassTargetsInSection(cx, cz, sectionIndex, targets);
        return changed;
    }

    private static void notifyClients(WorldServer level) {
        var packet = new ClearCompassCachePacket();
        for (var player : level.playerEntities) {
            if (player instanceof EntityPlayerMP playerMp) {
                InitNetwork.sendToClient(playerMp, packet);
            }
        }
    }

    private record RegionQuery(WorldServer level, int regionX, int regionZ) {

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof RegionQuery(WorldServer level1, int regionX1, int regionZ1))) {
                return false;
            }
            return this.level == level1 && this.regionX == regionX1 && this.regionZ == regionZ1;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(this.level);
            result = 31 * result + this.regionX;
            result = 31 * result + this.regionZ;
            return result;
        }
    }

    private record ChunkIndexQuery(WorldServer level, int chunkX, int chunkZ) {

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ChunkIndexQuery(WorldServer level1, int chunkX1, int chunkZ1))) {
                return false;
            }
            return this.level == level1 && this.chunkX == chunkX1 && this.chunkZ == chunkZ1;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(this.level);
            result = 31 * result + this.chunkX;
            result = 31 * result + this.chunkZ;
            return result;
        }
    }
}
