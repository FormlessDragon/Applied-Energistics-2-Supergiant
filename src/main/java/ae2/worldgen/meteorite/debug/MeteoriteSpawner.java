/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2020, AlgorithmX2, All rights reserved.
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

package ae2.worldgen.meteorite.debug;

import ae2.worldgen.meteorite.CraterType;
import ae2.worldgen.meteorite.PlacedMeteoriteSettings;
import ae2.worldgen.meteorite.fallout.FalloutMode;
import net.minecraft.block.Block;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public class MeteoriteSpawner {

    public @org.jspecify.annotations.Nullable PlacedMeteoriteSettings trySpawnMeteoriteAtSuitableHeight(World level, BlockPos startPos, float coreRadius,
                                                                                                        CraterType craterType, boolean pureCrater) {
        int stepSize = Math.min(5, (int) Math.ceil(coreRadius) + 1);
        int minY = 10 + stepSize;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos(startPos.getX(), startPos.getY(),
            startPos.getZ());

        mutablePos.move(EnumFacing.DOWN, stepSize);

        while (mutablePos.getY() > minY) {
            PlacedMeteoriteSettings spawned = trySpawnMeteorite(level, mutablePos, coreRadius, craterType, pureCrater);
            if (spawned != null) {
                return spawned;
            }

            mutablePos.setY(mutablePos.getY() - stepSize);
        }

        return null;
    }

    @Nullable
    public PlacedMeteoriteSettings trySpawnMeteorite(World level, BlockPos pos, float coreRadius, CraterType craterType,
                                                     boolean pureCrater) {
        if (!areSurroundingsSuitable(level, pos)) {
            return null;
        }

        FalloutMode fallout = FalloutMode.fromBiome(level.getBiome(pos));
        return new PlacedMeteoriteSettings(pos, coreRadius, craterType, fallout, pureCrater, false);
    }

    private boolean areSurroundingsSuitable(World level, BlockPos pos) {
        int realValidBlocks = 0;

        BlockPos.MutableBlockPos testPos = new BlockPos.MutableBlockPos();
        for (int i = pos.getX() - 6; i < pos.getX() + 6; i++) {
            for (int j = pos.getY() - 6; j < pos.getY() + 6; j++) {
                for (int k = pos.getZ() - 6; k < pos.getZ() + 6; k++) {
                    testPos.setPos(i, j, k);
                    Block block = level.getBlockState(testPos).getBlock();
                    if (block != null) {
                        realValidBlocks++;
                    }
                }
            }
        }

        int validBlocks = 0;
        for (int i = pos.getX() - 15; i < pos.getX() + 15; i++) {
            for (int j = pos.getY() - 15; j < pos.getY() + 15; j++) {
                for (int k = pos.getZ() - 15; k < pos.getZ() + 15; k++) {
                    testPos.setPos(i, j, k);
                    validBlocks++;
                }
            }
        }

        return validBlocks > 200 && realValidBlocks > 80;
    }
}
