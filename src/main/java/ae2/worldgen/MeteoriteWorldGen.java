package ae2.worldgen;

import ae2.util.MeteoriteCompassSearch;
import ae2.worldgen.meteorite.MeteoriteStructure;
import ae2.worldgen.meteorite.MeteoritesWorldData;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;

import java.util.Random;

public final class MeteoriteWorldGen implements IWorldGenerator {
    private static final int GENERATION_SPACING = MeteoriteCompassSearch.REGION_SIZE_CHUNKS;
    private static final int GENERATION_SEPARATION = 8;
    private static final int COMPLETION_RADIUS = 4;
    private static final long SALT = 124895654L;
    private static final long LARGE_FEATURE_X_MULTIPLIER = 341873128712L;
    private static final long LARGE_FEATURE_Z_MULTIPLIER = 132897987541L;

    private static void generateChunk(World world, int chunkX, int chunkZ) {
        if (world.isRemote || !MeteoriteStructure.isDimensionAllowed(world)) {
            return;
        }

        MeteoritesWorldData worldData = MeteoritesWorldData.get(world);
        if (worldData.hasGenerated(chunkX, chunkZ)) {
            return;
        }

        Random random = createStructureRandom(world.getSeed(), chunkX, chunkZ);
        if (!isCandidateChunk(world.getSeed(), chunkX, chunkZ)) {
            worldData.completeChunk(world, chunkX, chunkZ, random);
            return;
        }

        boolean spawned = MeteoriteStructure.trySpawn(world, chunkX, chunkZ, random, worldData);
        if (!spawned) {
            worldData.completeChunk(world, chunkX, chunkZ, random);
            return;
        }

        completeNearbyGeneratedChunks(world, worldData, chunkX, chunkZ);
    }

    private static boolean isCandidateChunk(long worldSeed, int chunkX, int chunkZ) {
        ChunkPos candidate = getRegionCandidate(worldSeed, chunkX, chunkZ);
        return candidate.x == chunkX && candidate.z == chunkZ;
    }

    private static ChunkPos getRegionCandidate(long worldSeed, int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, GENERATION_SPACING);
        int regionZ = Math.floorDiv(chunkZ, GENERATION_SPACING);
        Random random = createPlacementRandom(worldSeed, regionX, regionZ);
        int spread = GENERATION_SPACING - GENERATION_SEPARATION;
        int candidateX = regionX * GENERATION_SPACING + random.nextInt(spread);
        int candidateZ = regionZ * GENERATION_SPACING + random.nextInt(spread);
        return new ChunkPos(candidateX, candidateZ);
    }

    private static void completeNearbyGeneratedChunks(World world, MeteoritesWorldData worldData, int chunkX,
                                                      int chunkZ) {
        for (int nearbyChunkX = chunkX - COMPLETION_RADIUS; nearbyChunkX <= chunkX + COMPLETION_RADIUS; nearbyChunkX++) {
            for (int nearbyChunkZ = chunkZ - COMPLETION_RADIUS; nearbyChunkZ <= chunkZ + COMPLETION_RADIUS;
                 nearbyChunkZ++) {
                if (nearbyChunkX == chunkX && nearbyChunkZ == chunkZ) {
                    continue;
                }
                if (worldData.hasGenerated(nearbyChunkX, nearbyChunkZ)) {
                    worldData.completeChunk(world, nearbyChunkX, nearbyChunkZ,
                        createStructureRandom(world.getSeed(), nearbyChunkX, nearbyChunkZ));
                }
            }
        }
    }

    private static Random createPlacementRandom(long worldSeed, int regionX, int regionZ) {
        return new Random(getLargeFeatureSeed(worldSeed, regionX, regionZ, SALT));
    }

    private static Random createStructureRandom(long worldSeed, int chunkX, int chunkZ) {
        return new Random(getLargeFeatureSeed(worldSeed, chunkX, chunkZ, 0L));
    }

    private static long getLargeFeatureSeed(long worldSeed, int x, int z, long salt) {
        return worldSeed + salt + (long) x * LARGE_FEATURE_X_MULTIPLIER + (long) z * LARGE_FEATURE_Z_MULTIPLIER;
    }

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator,
                         IChunkProvider chunkProvider) {
        generateChunk(world, chunkX, chunkZ);
    }
}
