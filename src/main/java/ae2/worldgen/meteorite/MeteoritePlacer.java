/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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
import ae2.core.definitions.AEBlocks;
import ae2.worldgen.meteorite.fallout.Fallout;
import ae2.worldgen.meteorite.fallout.FalloutCopy;
import ae2.worldgen.meteorite.fallout.FalloutMode;
import ae2.worldgen.meteorite.fallout.FalloutSand;
import ae2.worldgen.meteorite.fallout.FalloutSnow;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class MeteoritePlacer {
    private final IBlockState skyStone;
    private final List<IBlockState> quartzBlocks;
    private final List<IBlockState> quartzBuds;
    private final MeteoriteBlockPutter putter = new MeteoriteBlockPutter();
    private final World level;
    private final Random random;
    private final Fallout type;
    private final BlockPos pos;
    private final int x;
    private final int y;
    private final int z;
    private final double meteoriteSize;
    private final double squaredMeteoriteSize;
    private final double crater;
    private final boolean placeCrater;
    private final CraterType craterType;
    private final boolean pureCrater;
    private final boolean craterLake;
    private final StructureBoundingBox boundingBox;

    private MeteoritePlacer(World level, PlacedMeteoriteSettings settings, StructureBoundingBox boundingBox,
                            Random random) {
        this.boundingBox = boundingBox;
        this.level = level;
        this.random = random;
        this.pos = settings.pos();
        this.x = settings.pos().getX();
        this.y = settings.pos().getY();
        this.z = settings.pos().getZ();
        this.meteoriteSize = settings.meteoriteRadius();
        this.placeCrater = settings.shouldPlaceCrater();
        this.craterType = settings.craterType();
        this.pureCrater = settings.pureCrater();
        this.craterLake = settings.craterLake();
        this.squaredMeteoriteSize = this.meteoriteSize * this.meteoriteSize;

        double realCrater = this.meteoriteSize * 2 + 5;
        this.crater = realCrater * realCrater;

        this.quartzBlocks = getQuartzBudList();
        this.quartzBuds = Arrays.asList(
            AEBlocks.SMALL_QUARTZ_BUD.block().getDefaultState(),
            AEBlocks.MEDIUM_QUARTZ_BUD.block().getDefaultState(),
            AEBlocks.LARGE_QUARTZ_BUD.block().getDefaultState());
        this.skyStone = AEBlocks.SKY_STONE_BLOCK.block().getDefaultState();

        this.type = getFallout(level, new BlockPos(
            (boundingBox.minX + boundingBox.maxX) / 2,
            (boundingBox.minY + boundingBox.maxY) / 2,
            (boundingBox.minZ + boundingBox.maxZ) / 2), settings.fallout());
    }

    public static void place(World world, PlacedMeteoriteSettings settings, StructureBoundingBox boundingBox,
                             Random random) {
        MeteoritePlacer placer = new MeteoritePlacer(world, settings, boundingBox, random);
        placer.place();
    }

    private List<IBlockState> getQuartzBudList() {
        if (AEConfig.instance().isSpawnFlawlessOnlyEnabled()) {
            return Collections.singletonList(AEBlocks.FLAWLESS_BUDDING_QUARTZ.block().getDefaultState());
        }
        return Arrays.asList(
            AEBlocks.QUARTZ_BLOCK.block().getDefaultState(),
            AEBlocks.DAMAGED_BUDDING_QUARTZ.block().getDefaultState(),
            AEBlocks.CHIPPED_BUDDING_QUARTZ.block().getDefaultState(),
            AEBlocks.FLAWED_BUDDING_QUARTZ.block().getDefaultState(),
            AEBlocks.FLAWLESS_BUDDING_QUARTZ.block().getDefaultState());
    }

    public void place() {
        if (placeCrater) {
            this.placeCrater();
        }

        this.placeMeteorite();

        if (placeCrater) {
            this.decay();
        }
        if (craterLake) {
            this.placeCraterLake();
        }
    }

    private int minX(int x) {
        if (x < boundingBox.minX) {
            return boundingBox.minX;
        } else if (x > boundingBox.maxX) {
            return boundingBox.maxX;
        }
        return x;
    }

    private int minZ(int x) {
        if (x < boundingBox.minZ) {
            return boundingBox.minZ;
        } else if (x > boundingBox.maxZ) {
            return boundingBox.maxZ;
        }
        return x;
    }

    private int maxX(int x) {
        if (x < boundingBox.minX) {
            return boundingBox.minX;
        } else if (x > boundingBox.maxX) {
            return boundingBox.maxX;
        }
        return x;
    }

    private int maxZ(int x) {
        if (x < boundingBox.minZ) {
            return boundingBox.minZ;
        } else if (x > boundingBox.maxZ) {
            return boundingBox.maxZ;
        }
        return x;
    }

    private void placeCrater() {
        int maxY = level.getActualHeight();
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        IBlockState filler = craterType.getFiller().getDefaultState();

        for (int j = y - 5; j <= maxY; j++) {
            for (int i = boundingBox.minX; i <= boundingBox.maxX; i++) {
                for (int k = boundingBox.minZ; k <= boundingBox.maxZ; k++) {
                    blockPos.setPos(i, j, k);
                    final double dx = i - x;
                    final double dz = k - z;
                    final double h = y - this.meteoriteSize + 1 + this.type.adjustCrater();

                    final double distanceFrom = dx * dx + dz * dz;

                    if (j > h + distanceFrom * 0.02) {
                        IBlockState currentBlock = level.getBlockState(blockPos);

                        if (craterType != CraterType.NORMAL && j < y && currentBlock.isFullBlock()) {
                            this.putter.put(level, blockPos, filler);
                        } else {
                            this.putter.put(level, blockPos, Blocks.AIR.getDefaultState());
                        }

                    }
                }
            }
        }

        for (EntityItem e : level.getEntitiesWithinAABB(EntityItem.class,
            new AxisAlignedBB(minX(x - 30), y - 5, minZ(z - 30), maxX(x + 30), y + 30, maxZ(z + 30)))) {
            e.setDead();
        }
    }

    private void placeMeteorite() {
        this.placeMeteoriteSkyStone();

        if (boundingBox.isVecInside(pos)) {
            placeChest();
        }
    }

    private void placeChest() {
        if (AEConfig.instance().isSpawnPressesInMeteoritesEnabled()) {
            this.putter.put(level, pos, AEBlocks.MYSTERIOUS_CUBE.block().getDefaultState());
        }
    }

    private void placeMeteoriteSkyStone() {
        final int meteorXLength = minX(x - 8);
        final int meteorXHeight = maxX(x + 8);
        final int meteorZLength = minZ(z - 8);
        final int meteorZHeight = maxZ(z + 8);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = meteorXLength; i <= meteorXHeight; i++) {
            for (int j = y - 8; j < y + 8; j++) {
                for (int k = meteorZLength; k <= meteorZHeight; k++) {
                    pos.setPos(i, j, k);
                    int dx = i - x;
                    int dy = j - y;
                    int dz = k - z;

                    if (dx * dx * 0.7 + dy * dy * (j > y ? 1.4 : 0.8) + dz * dz * 0.7 < this.squaredMeteoriteSize) {
                        if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1 && Math.abs(dz) <= 1) {
                            if (dy == -1) {
                                int certusIndex = random.nextInt(quartzBlocks.size());
                                this.putter.put(level, pos, quartzBlocks.get(certusIndex));
                                if (certusIndex != 0 && (dx != 0 || dz != 0) && random.nextFloat() <= 0.7F) {
                                    IBlockState bud = quartzBuds.get(random.nextInt(quartzBuds.size()))
                                                                .withProperty(BlockDirectional.FACING, EnumFacing.UP);
                                    this.putter.put(level, pos.up(), bud);
                                }
                            }
                        } else {
                            this.putter.put(level, pos, skyStone);
                        }
                    }
                }
            }
        }
    }

    private void decay() {
        double randomShit = 0;

        final int meteorXLength = minX(x - 30);
        final int meteorXHeight = maxX(x + 30);
        final int meteorZLength = minZ(z - 30);
        final int meteorZHeight = maxZ(z + 30);

        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos blockPosUp = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos blockPosDown = new BlockPos.MutableBlockPos();
        for (int i = meteorXLength; i <= meteorXHeight; i++) {
            for (int k = meteorZLength; k <= meteorZHeight; k++) {
                for (int j = y - 9; j < y + 30; j++) {
                    blockPos.setPos(i, j, k);
                    blockPosUp.setPos(i, j + 1, k);
                    blockPosDown.setPos(i, j - 1, k);
                    IBlockState state = level.getBlockState(blockPos);
                    Block blk = level.getBlockState(blockPos).getBlock();

                    if (this.pureCrater && blk == craterType.getFiller()) {
                        continue;
                    }

                    if (state.getMaterial().isReplaceable()) {
                        if (!level.isAirBlock(blockPosUp)) {
                            final IBlockState stateUp = level.getBlockState(blockPosUp);
                            level.setBlockState(blockPos, stateUp, 3);
                        } else if (randomShit < 100 * this.crater) {
                            final double dx = i - x;
                            final double dy = j - y;
                            final double dz = k - z;
                            final double dist = dx * dx + dy * dy + dz * dz;

                            final IBlockState xf = level.getBlockState(blockPosDown);
                            if (!xf.getMaterial().isReplaceable()) {
                                final double extraRange = random.nextDouble() * 0.6;
                                final double height = this.crater * (extraRange + 0.2)
                                    - Math.abs(dist - this.crater * 1.7);

                                if (!xf.getMaterial().isLiquid() && !xf.getBlock().isAir(xf, level, blockPosDown)
                                    && height > 0 && random.nextDouble() > 0.6) {
                                    randomShit++;
                                    this.type.getRandomFall(level, blockPos);
                                }
                            }
                        }
                    } else if (level.isAirBlock(blockPosUp) && random.nextDouble() > 0.4) {
                        final double dx = i - x;
                        final double dy = j - y;
                        final double dz = k - z;
                        double dr2 = dx * dx + dy * dy + dz * dz;

                        if (!(Math.abs(dx) <= 1 && Math.abs(dy) <= 1 && Math.abs(dz) <= 1) && dr2 < this.crater * 1.6) {
                            this.type.getRandomInset(level, blockPos);
                        }
                    }
                }
            }
        }
    }

    private void placeCraterLake() {
        final int maxY = level.getSeaLevel() - 1;
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

        for (int currentX = boundingBox.minX; currentX <= boundingBox.maxX; currentX++) {
            for (int currentZ = boundingBox.minZ; currentZ <= boundingBox.maxZ; currentZ++) {
                for (int currentY = y - 5; currentY <= maxY; currentY++) {
                    blockPos.setPos(currentX, currentY, currentZ);

                    final double dx = currentX - x;
                    final double dz = currentZ - z;
                    final double h = y - this.meteoriteSize + 1 + this.type.adjustCrater();

                    final double distanceFrom = dx * dx + dz * dz;

                    if (currentY > h + distanceFrom * 0.02) {
                        IBlockState currentBlock = level.getBlockState(blockPos);
                        if (currentBlock.getBlock() == Blocks.AIR) {
                            this.putter.put(level, blockPos, Blocks.WATER.getDefaultState());

                            if (currentY == maxY) {
                                level.scheduleUpdate(blockPos, Blocks.WATER, 0);
                            }
                        }
                    } else if (maxY + (maxY - currentY) * 2 + 2 > h + distanceFrom * 0.02) {
                        pillarDownSlopeBlocks(blockPos);
                    }
                }
            }
        }
    }

    private void pillarDownSlopeBlocks(BlockPos.MutableBlockPos blockPos) {
        BlockPos.MutableBlockPos enclosingBlockPos = new BlockPos.MutableBlockPos(blockPos.getX(), blockPos.getY(),
            blockPos.getZ());

        for (int i = 0; i < 20; i++) {
            if (placeEnclosingBlock(enclosingBlockPos)) {
                break;
            }
            enclosingBlockPos.move(EnumFacing.DOWN);
        }
    }

    private boolean placeEnclosingBlock(BlockPos.MutableBlockPos enclosingBlockPos) {
        IBlockState currentState = level.getBlockState(enclosingBlockPos);
        if (currentState.getBlock() == Blocks.AIR ||
            (!currentState.getMaterial().isLiquid() &&
                (currentState.getMaterial().isReplaceable()
                    || currentState.getBlock().isReplaceable(level, enclosingBlockPos)))) {

            if (craterType == CraterType.LAVA && level.rand.nextFloat() < 0.075f) {
                this.putter.put(level, enclosingBlockPos, Blocks.MAGMA.getDefaultState());
            } else {
                this.type.getRandomFall(level, enclosingBlockPos);
            }
        } else {
            return true;
        }
        return false;
    }

    private Fallout getFallout(World level, BlockPos pos, FalloutMode mode) {
        return switch (mode) {
            case SAND -> new FalloutSand(level, pos, this.putter, this.skyStone, random);
            case TERRACOTTA -> new FalloutCopy(level, pos, this.putter, this.skyStone, random);
            case ICE_SNOW -> new FalloutSnow(level, pos, this.putter, this.skyStone, random);
            default -> new Fallout(this.putter, this.skyStone, random);
        };
    }
}
