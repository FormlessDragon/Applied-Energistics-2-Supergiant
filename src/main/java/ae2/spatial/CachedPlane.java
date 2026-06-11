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

package ae2.spatial;

import ae2.api.movable.BlockEntityMoveStrategies;
import ae2.api.movable.IBlockEntityMoveStrategy;
import ae2.core.AELog;
import ae2.core.definitions.AEBlocks;
import ae2.server.services.compass.ServerCompassService;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import org.jetbrains.annotations.Nullable;
import java.util.List;

public class CachedPlane {

    private final int xSize;
    private final int ySize;
    private final int zSize;
    private final int xOffset;
    private final int yOffset;
    private final int zOffset;
    private final WorldServer level;
    private final IBlockState[] blockStates;
    private final boolean[] skipped;
    private final ObjectList<BlockEntityMoveRecord> blockEntities = new ObjectArrayList<>();
    private final ObjectList<NextTickListEntry> scheduledTicks = new ObjectArrayList<>();
    private final ObjectList<BlockPos> updates = new ObjectArrayList<>();
    private final IBlockState matrixFrame;

    public CachedPlane(WorldServer level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.level = level;
        this.xOffset = minX;
        this.yOffset = minY;
        this.zOffset = minZ;
        this.xSize = maxX - minX + 1;
        this.ySize = maxY - minY + 1;
        this.zSize = maxZ - minZ + 1;
        this.blockStates = new IBlockState[this.xSize * this.ySize * this.zSize];
        this.skipped = new boolean[this.blockStates.length];
        this.matrixFrame = AEBlocksHolder.getMatrixFrame();

        for (int x = 0; x < this.xSize; x++) {
            for (int y = 0; y < this.ySize; y++) {
                for (int z = 0; z < this.zSize; z++) {
                    BlockPos pos = new BlockPos(this.xOffset + x, this.yOffset + y, this.zOffset + z);
                    this.blockStates[index(x, y, z)] = level.getBlockState(pos);
                }
            }
        }

        for (BlockPos pos : BlockPos.getAllInBoxMutable(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ))) {
            TileEntity tile = level.getTileEntity(pos);
            if (tile == null) {
                continue;
            }

            int stateIndex = index(pos.getX() - this.xOffset, pos.getY() - this.yOffset, pos.getZ() - this.zOffset);

            IBlockEntityMoveStrategy strategy = BlockEntityMoveStrategies.get(tile);
            NBTTagCompound savedData = strategy.beginMove(tile);
            if (savedData == null) {
                this.skipped[stateIndex] = true;
                continue;
            }

            this.blockEntities.add(new BlockEntityMoveRecord(
                strategy,
                tile,
                this.blockStates[stateIndex],
                savedData,
                pos.toImmutable()));
            level.removeTileEntity(pos);
        }

        List<NextTickListEntry> entries = level.getPendingBlockUpdates(
            new StructureBoundingBox(minX, minY, minZ, maxX, maxY, maxZ),
            true);
        if (entries != null) {
            this.scheduledTicks.addAll(entries);
        }
    }

    public void swap(CachedPlane dst) {
        if (this.xSize != dst.xSize || this.ySize != dst.ySize || this.zSize != dst.zSize) {
            throw new IllegalArgumentException("Spatial swap requires equal source and destination sizes.");
        }

        for (int x = 0; x < this.xSize; x++) {
            for (int y = 0; y < this.ySize; y++) {
                for (int z = 0; z < this.zSize; z++) {
                    int index = index(x, y, z);
                    if (this.skipped[index] || dst.skipped[index]) {
                        this.markForUpdate(this.xOffset + x, this.yOffset + y, this.zOffset + z);
                        dst.markForUpdate(dst.xOffset + x, dst.yOffset + y, dst.zOffset + z);
                        continue;
                    }

                    BlockPos srcPos = new BlockPos(this.xOffset + x, this.yOffset + y, this.zOffset + z);
                    BlockPos dstPos = new BlockPos(dst.xOffset + x, dst.yOffset + y, dst.zOffset + z);

                    IBlockState srcState = normalize(this.blockStates[index]);
                    IBlockState dstState = normalize(dst.blockStates[index]);

                    this.level.setBlockState(srcPos, dstState, 2);
                    dst.level.setBlockState(dstPos, srcState, 2);
                }
            }
        }

        for (BlockEntityMoveRecord moveRecord : this.blockEntities) {
            BlockPos pos = moveRecord.pos();
            dst.addBlockEntity(pos.getX() - this.xOffset, pos.getY() - this.yOffset, pos.getZ() - this.zOffset,
                moveRecord);
        }

        for (BlockEntityMoveRecord moveRecord : dst.blockEntities) {
            BlockPos pos = moveRecord.pos();
            this.addBlockEntity(pos.getX() - dst.xOffset, pos.getY() - dst.yOffset, pos.getZ() - dst.zOffset,
                moveRecord);
        }

        for (NextTickListEntry entry : this.scheduledTicks) {
            BlockPos moved = entry.position.add(-this.xOffset + dst.xOffset, -this.yOffset + dst.yOffset,
                -this.zOffset + dst.zOffset);
            scheduleTick(dst.level, moved, entry);
        }

        for (NextTickListEntry entry : dst.scheduledTicks) {
            BlockPos moved = entry.position.add(-dst.xOffset + this.xOffset, -dst.yOffset + this.yOffset,
                -dst.zOffset + this.zOffset);
            scheduleTick(this.level, moved, entry);
        }

        updateCompassRegions();
        dst.updateCompassRegions();
    }

    private IBlockState normalize(IBlockState state) {
        return state.getBlock() == this.matrixFrame.getBlock() ? Blocks.AIR.getDefaultState() : state;
    }

    private void scheduleTick(WorldServer level, BlockPos pos, NextTickListEntry entry) {
        long delay = Math.max(0, entry.scheduledTime - level.getTotalWorldTime());
        level.scheduleBlockUpdate(pos, entry.getBlock(), (int) Math.min(Integer.MAX_VALUE, delay), entry.priority);
    }

    private void addBlockEntity(int x, int y, int z, BlockEntityMoveRecord moveRecord) {
        int index = index(x, y, z);
        if (this.skipped[index]) {
            AELog.warn("Skipping moved tile entity %s because destination is blocked.", moveRecord.tileEntity());
            return;
        }

        BlockPos newPosition = new BlockPos(this.xOffset + x, this.yOffset + y, this.zOffset + z);
        try {
            if (!moveRecord.strategy().completeMove(moveRecord.tileEntity(), moveRecord.state(), moveRecord.savedData(), this.level,
                newPosition)) {
                this.attemptRecovery(newPosition, moveRecord);
                return;
            }
            IBlockState currentState = this.level.getBlockState(newPosition);
            this.level.notifyBlockUpdate(newPosition, currentState, currentState, 3);
        } catch (Throwable e) {
            AELog.warn(e, "Failed to restore spatially moved tile entity %s", moveRecord.tileEntity());
            this.attemptRecovery(newPosition, moveRecord);
        }
    }

    private void attemptRecovery(BlockPos newPosition, BlockEntityMoveRecord moveRecord) {
        NBTTagCompound recoveredData = moveRecord.savedData().copy();
        recoveredData.setInteger("x", newPosition.getX());
        recoveredData.setInteger("y", newPosition.getY());
        recoveredData.setInteger("z", newPosition.getZ());

        TileEntity recoveredTile = TileEntity.create(this.level, recoveredData);
        if (recoveredTile == null) {
            AELog.warn("Failed to recover spatially moved tile entity %s", moveRecord.tileEntity());
            return;
        }

        this.level.removeTileEntity(newPosition);
        this.level.setBlockState(newPosition, moveRecord.state(), 2);
        this.level.setTileEntity(newPosition, recoveredTile);
        this.level.notifyBlockUpdate(newPosition, moveRecord.state(), moveRecord.state(), 3);
    }

    private void markForUpdate(int x, int y, int z) {
        this.updates.add(new BlockPos(x, y, z));
        this.updates.add(new BlockPos(x + 1, y, z));
        this.updates.add(new BlockPos(x - 1, y, z));
        this.updates.add(new BlockPos(x, y + 1, z));
        this.updates.add(new BlockPos(x, y - 1, z));
        this.updates.add(new BlockPos(x, y, z + 1));
        this.updates.add(new BlockPos(x, y, z - 1));
    }

    private void updateCompassRegions() {
        int minChunkX = this.xOffset >> 4;
        int maxChunkX = (this.xOffset + this.xSize - 1) >> 4;
        int minChunkZ = this.zOffset >> 4;
        int maxChunkZ = (this.zOffset + this.zSize - 1) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                Chunk chunk = this.level.getChunkProvider().provideChunk(chunkX, chunkZ);
                ServerCompassService.updateArea(this.level, chunk);
            }
        }
    }

    private int index(int x, int y, int z) {
        return (x * this.ySize + y) * this.zSize + z;
    }

    public WorldServer getLevel() {
        return this.level;
    }

    public List<BlockPos> getUpdates() {
        return this.updates;
    }

    private record BlockEntityMoveRecord(
        IBlockEntityMoveStrategy strategy,
        TileEntity tileEntity,
        IBlockState state,
        NBTTagCompound savedData,
        BlockPos pos) {
    }

    private static final class AEBlocksHolder {
        @Nullable
        private static IBlockState matrixFrame;

        private static IBlockState getMatrixFrame() {
            if (matrixFrame == null) {
                matrixFrame = AEBlocks.MATRIX_FRAME.block().getDefaultState();
            }
            return matrixFrame;
        }
    }
}
