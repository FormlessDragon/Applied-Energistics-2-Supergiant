package appeng.server.services.compass;

import appeng.core.definitions.AEBlocks;
import appeng.core.network.InitNetwork;
import appeng.core.network.clientbound.ClearCompassCachePacket;
import appeng.tile.misc.TileMysteriousCube;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class ServerCompassService {
    private static final int MAX_RANGE = 174;
    private static final int CHUNK_SIZE = 16;
    private static final LoadingCache<Query, Optional<BlockPos>> CLOSEST_METEORITE_CACHE = CacheBuilder.newBuilder()
                                                                                                       .maximumSize(100)
                                                                                                       .expireAfterWrite(5, TimeUnit.SECONDS)
                                                                                                       .build(new CacheLoader<>() {
                                                                                                           @Override
                                                                                                           public @NonNull Optional<BlockPos> load(@NonNull Query query) {
                                                                                                               return Optional.ofNullable(findClosestMeteoritePos(query.level, query.chunk));
                                                                                                           }
                                                                                                       });

    public static Optional<BlockPos> getClosestMeteorite(WorldServer level, ChunkPos chunkPos) {
        return CLOSEST_METEORITE_CACHE.getUnchecked(new Query(level, chunkPos));
    }

    @Nullable
    private static BlockPos findClosestMeteoritePos(WorldServer level, ChunkPos originChunkPos) {
        var chunkPos = findClosestMeteoriteChunk(level, originChunkPos);
        if (chunkPos == null) {
            return null;
        }
        var chunk = level.getChunkProvider().getLoadedChunk(chunkPos.x, chunkPos.z);
        if (chunk == null) {
            return getChunkCenter(chunkPos);
        }

        var sourcePos = getChunkCenter(originChunkPos);
        var closestDistanceSq = Double.MAX_VALUE;
        BlockPos chosenPos = getChunkCenter(chunkPos);
        for (TileEntity blockEntity : chunk.getTileEntityMap().values()) {
            if (!(blockEntity instanceof TileMysteriousCube)) {
                continue;
            }

            BlockPos meteoritePos = blockEntity.getPos();
            var distSq = sourcePos.distanceSq(meteoritePos);
            if (distSq < closestDistanceSq) {
                chosenPos = meteoritePos;
                closestDistanceSq = distSq;
            }
        }
        return chosenPos;
    }

    @Nullable
    private static ChunkPos findClosestMeteoriteChunk(WorldServer level, ChunkPos chunkPos) {
        var cx = chunkPos.x;
        var cz = chunkPos.z;

        if (hasCompassTarget(level, cx, cz)) {
            return chunkPos;
        }

        for (int offset = 1; offset < MAX_RANGE; offset++) {
            final int minX = cx - offset;
            final int minZ = cz - offset;
            final int maxX = cx + offset;
            final int maxZ = cz + offset;

            int closest = Integer.MAX_VALUE;
            int chosenX = cx;
            int chosenZ = cz;

            for (int z = minZ; z <= maxZ; z++) {
                if (hasCompassTarget(level, minX, z)) {
                    final int closeness = dist(cx, cz, minX, z);
                    if (closeness < closest) {
                        closest = closeness;
                        chosenX = minX;
                        chosenZ = z;
                    }
                }
                if (hasCompassTarget(level, maxX, z)) {
                    final int closeness = dist(cx, cz, maxX, z);
                    if (closeness < closest) {
                        closest = closeness;
                        chosenX = maxX;
                        chosenZ = z;
                    }
                }
            }

            for (int x = minX + 1; x < maxX; x++) {
                if (hasCompassTarget(level, x, minZ)) {
                    final int closeness = dist(cx, cz, x, minZ);
                    if (closeness < closest) {
                        closest = closeness;
                        chosenX = x;
                        chosenZ = minZ;
                    }
                }
                if (hasCompassTarget(level, x, maxZ)) {
                    final int closeness = dist(cx, cz, x, maxZ);
                    if (closeness < closest) {
                        closest = closeness;
                        chosenX = x;
                        chosenZ = maxZ;
                    }
                }
            }

            if (closest < Integer.MAX_VALUE) {
                return new ChunkPos(chosenX, chosenZ);
            }
        }

        return null;
    }

    public static void updateArea(WorldServer level, Chunk chunk) {
        var compassRegion = CompassRegion.get(level, chunk.getPos());
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        for (var i = 0; i < sections.length; i++) {
            updateArea(compassRegion, chunk, i);
        }
    }

    public static void notifyBlockChange(WorldServer level, BlockPos pos) {
        Chunk chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        var compassRegion = CompassRegion.get(level, chunk.getPos());
        updateArea(compassRegion, chunk, pos.getY() >> 4);
        CLOSEST_METEORITE_CACHE.invalidateAll();
        notifyClients(level);
    }

    private static void updateArea(CompassRegion compassRegion, Chunk chunk, int sectionIndex) {
        int cx = chunk.x;
        int cz = chunk.z;
        var section = chunk.getBlockStorageArray()[sectionIndex];
        if (section == null || section.isEmpty()) {
            compassRegion.setHasCompassTarget(cx, cz, sectionIndex, false);
            return;
        }

        var desiredBlock = AEBlocks.MYSTERIOUS_CUBE.block();
        for (int localX = 0; localX < 16; localX++) {
            for (int localY = 0; localY < 16; localY++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    if (section.get(localX, localY, localZ).getBlock() == desiredBlock) {
                        compassRegion.setHasCompassTarget(cx, cz, sectionIndex, true);
                        return;
                    }
                }
            }
        }
        compassRegion.setHasCompassTarget(cx, cz, sectionIndex, false);
    }

    private static boolean hasCompassTarget(WorldServer level, int chunkX, int chunkZ) {
        return CompassRegion.get(level, new ChunkPos(chunkX, chunkZ)).hasCompassTarget(chunkX, chunkZ);
    }

    private static BlockPos getChunkCenter(ChunkPos chunkPos) {
        return new BlockPos(chunkPos.getXStart() + 8, 0, chunkPos.getZStart() + 8);
    }

    private static int dist(int ax, int az, int bx, int bz) {
        final int up = (bz - az) * CHUNK_SIZE;
        final int side = (bx - ax) * CHUNK_SIZE;
        return up * up + side * side;
    }

    private static void notifyClients(WorldServer level) {
        var packet = new ClearCompassCachePacket();
        for (var player : level.playerEntities) {
            if (player instanceof EntityPlayerMP playerMp) {
                InitNetwork.sendToClient(playerMp, packet);
            }
        }
    }

    private record Query(WorldServer level, ChunkPos chunk) {

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Query(WorldServer level1, ChunkPos chunk1))) {
                return false;
            }
            return this.level == level1 && Objects.equals(this.chunk, chunk1);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(this.level) + this.chunk.hashCode();
        }
    }
}
