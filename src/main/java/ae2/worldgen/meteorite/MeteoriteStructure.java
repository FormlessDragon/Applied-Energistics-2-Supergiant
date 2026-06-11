/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.worldgen.meteorite;

import ae2.core.AEConfig;
import ae2.worldgen.meteorite.fallout.FalloutMode;
import com.google.common.math.StatsAccumulator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeDictionary.Type;

import java.util.Random;

public class MeteoriteStructure {

    public static boolean trySpawn(World world, int chunkX, int chunkZ, Random random,
                                   MeteoritesWorldData worldData) {
        if (!isDimensionAllowed(world)) {
            return false;
        }

        if (!random.nextBoolean()) {
            return false;
        }

        return forceSpawn(world, chunkX, chunkZ, random, worldData);
    }

    public static boolean forceSpawn(World world, int chunkX, int chunkZ, Random random,
                                     MeteoritesWorldData worldData) {
        if (!isDimensionAllowed(world)) {
            return false;
        }
        if (!(world instanceof WorldServer worldServer)) {
            return false;
        }

        int centerX = (chunkX << 4) + random.nextInt(16);
        int centerZ = (chunkZ << 4) + random.nextInt(16);
        float radius = 2.0f + random.nextFloat() * 6.0f;
        int yOffset = (int) Math.ceil(radius) + 1;
        Biome spawnBiome = world.getBiome(new BlockPos(centerX, world.getSeaLevel(), centerZ));
        int centerY = getMeteoriteCenterY(world, centerX, centerZ, radius, yOffset, spawnBiome);

        BlockPos pos = new BlockPos(centerX, centerY, centerZ);
        boolean craterLake = locateWaterAroundTheCrater(world, pos, radius);
        CraterType craterType = determineCraterType(spawnBiome, random);
        boolean pureCrater = random.nextFloat() > 0.9f;
        FalloutMode fallout = FalloutMode.fromBiome(spawnBiome);

        PlacedMeteoriteSettings settings = new PlacedMeteoriteSettings(pos, radius, craterType, fallout, pureCrater,
            craterLake);
        worldData.addMeteorite(worldServer, settings);
        worldData.completeChunk(world, chunkX, chunkZ, createChunkRandom(world, chunkX, chunkZ));
        return true;
    }

    private static Random createChunkRandom(World world, int chunkX, int chunkZ) {
        long seed = world.getSeed() ^ ((long) chunkX * 341873128712L) ^ ((long) chunkZ * 132897987541L);
        return new Random(seed);
    }

    public static boolean isDimensionAllowed(World world) {
        int dim = world.provider.getDimension();
        int[] whitelist = getMeteoriteDimensionWhitelist();
        for (int allowed : whitelist) {
            if (allowed == dim) {
                return true;
            }
        }
        return false;
    }

    private static boolean locateWaterAroundTheCrater(World world, BlockPos pos, float radius) {
        int seaLevel = world.getSeaLevel();
        int maxY = seaLevel - 1;
        for (int x = pos.getX() - 32; x <= pos.getX() + 32; x++) {
            for (int z = pos.getZ() - 32; z <= pos.getZ() + 32; z++) {
                double dx = x - pos.getX();
                double dz = z - pos.getZ();
                double h = pos.getY() - radius + 1;
                double distanceFrom = dx * dx + dz * dz;
                if (maxY > h + distanceFrom * 0.0175 && maxY < h + distanceFrom * 0.02) {
                    int height = findOceanFloor(world, x, z);
                    if (height < seaLevel) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static CraterType determineCraterType(Biome biome, Random random) {
        float temp = biome.getDefaultTemperature();
        if (BiomeDictionary.hasType(biome, Type.OCEAN)) {
            return CraterType.NONE;
        }

        boolean specialMeteor = random.nextFloat() > 0.5f;
        if (!specialMeteor) {
            return CraterType.NORMAL;
        }

        boolean canSnow = biome.getEnableSnow();
        if (temp >= 1.0f) {
            boolean lava = random.nextFloat() > 0.5f;
            if (!biome.canRain()) {
                return lava ? CraterType.LAVA : CraterType.NORMAL;
            } else if (!canSnow) {
                boolean obsidian = random.nextFloat() > 0.75f;
                CraterType alternativeObsidian = obsidian ? CraterType.OBSIDIAN : CraterType.LAVA;
                return lava ? alternativeObsidian : CraterType.NORMAL;
            }
        }

        if (temp < 1.0f && temp >= 0.2f) {
            boolean lake = random.nextFloat() > 0.25f;
            boolean lava = random.nextFloat() > 0.8f;
            if (!biome.canRain()) {
                return lava ? CraterType.LAVA : CraterType.NORMAL;
            } else if (!canSnow) {
                boolean obsidian = random.nextFloat() > 0.75f;
                CraterType alternativeObsidian = obsidian ? CraterType.OBSIDIAN : CraterType.LAVA;
                CraterType craterLake = lake ? CraterType.WATER : CraterType.NORMAL;
                return lava ? alternativeObsidian : craterLake;
            } else {
                boolean snow = random.nextFloat() > 0.75f;
                CraterType water = lake ? CraterType.WATER : CraterType.NORMAL;
                return snow ? CraterType.SNOW : water;
            }
        }

        if (temp < 0.2f) {
            boolean lake = random.nextFloat() > 0.25f;
            boolean lava = random.nextFloat() > 0.95f;
            boolean frozen = random.nextFloat() > 0.25f;
            if (!biome.canRain()) {
                return lava ? CraterType.LAVA : CraterType.NORMAL;
            } else if (!canSnow) {
                CraterType frozenLake = frozen ? CraterType.ICE : CraterType.WATER;
                CraterType craterLake = lake ? frozenLake : CraterType.NORMAL;
                return lava ? CraterType.LAVA : craterLake;
            } else {
                CraterType snowCovered = lake ? CraterType.SNOW : CraterType.NORMAL;
                return lava ? CraterType.LAVA : snowCovered;
            }
        }

        return CraterType.NORMAL;
    }

    private static int getMeteoriteCenterY(World world, int centerX, int centerZ, float radius, int yOffset,
                                           Biome spawnBiome) {
        boolean ocean = BiomeDictionary.hasType(spawnBiome, Type.OCEAN);
        StatsAccumulator stats = new StatsAccumulator();
        int scanRadius = Math.max(1, (int) (radius * 2.0f));
        for (int x = -scanRadius; x <= scanRadius; x++) {
            for (int z = -scanRadius; z <= scanRadius; z++) {
                int height = ocean ? findOceanFloor(world, centerX + x, centerZ + z)
                    : findWorldSurface(world, centerX + x, centerZ + z);
                stats.add(height);
            }
        }

        int centerY = (int) stats.mean();
        if (stats.populationVariance() > 5.0d) {
            centerY -= (int) Math.round((stats.mean() - stats.min()) * 0.75d);
        }

        centerY -= yOffset;
        return Math.max(yOffset, centerY);
    }

    private static int findWorldSurface(World world, int x, int z) {
        return world.getTopSolidOrLiquidBlock(new BlockPos(x, 0, z)).getY();
    }

    private static int findOceanFloor(World world, int x, int z) {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos(x, world.getSeaLevel(), z);
        while (mutablePos.getY() > 1) {
            if (world.getBlockState(mutablePos).getMaterial().blocksMovement()) {
                return mutablePos.getY();
            }
            mutablePos.setPos(mutablePos.getX(), mutablePos.getY() - 1, mutablePos.getZ());
        }
        return 1;
    }

    private static int[] getMeteoriteDimensionWhitelist() {
        return AEConfig.instance().getMeteoriteDimensionWhitelist();
    }
}
