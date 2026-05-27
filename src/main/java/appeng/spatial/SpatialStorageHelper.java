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

package appeng.spatial;

import appeng.core.definitions.AEBlocks;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Teleporter;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;

public class SpatialStorageHelper {

    private static SpatialStorageHelper instance;

    public static SpatialStorageHelper getInstance() {
        if (instance == null) {
            instance = new SpatialStorageHelper();
        }
        return instance;
    }

    private void transverseEdges(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, ISpatialVisitor visitor) {
        for (int y = minY; y < maxY; y++) {
            for (int z = minZ; z < maxZ; z++) {
                visitor.visit(new BlockPos(minX, y, z));
                visitor.visit(new BlockPos(maxX, y, z));
            }
        }

        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                visitor.visit(new BlockPos(x, minY, z));
                visitor.visit(new BlockPos(x, maxY, z));
            }
        }

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                visitor.visit(new BlockPos(x, y, minZ));
                visitor.visit(new BlockPos(x, y, maxZ));
            }
        }
    }

    public void swapRegions(WorldServer srcLevel, int srcX, int srcY, int srcZ,
                            WorldServer dstLevel, int dstX, int dstY, int dstZ, int scaleX, int scaleY, int scaleZ) {
        Block matrixFrameBlock = AEBlocks.MATRIX_FRAME.block();
        this.transverseEdges(dstX - 1, dstY - 1, dstZ - 1, dstX + scaleX + 1, dstY + scaleY + 1, dstZ + scaleZ + 1,
            new WrapInMatrixFrame(matrixFrameBlock.getDefaultState(), dstLevel));

        AxisAlignedBB srcBox = new AxisAlignedBB(srcX, srcY, srcZ, srcX + scaleX + 1, srcY + scaleY + 1,
            srcZ + scaleZ + 1);
        AxisAlignedBB dstBox = new AxisAlignedBB(dstX, dstY, dstZ, dstX + scaleX + 1, dstY + scaleY + 1,
            dstZ + scaleZ + 1);

        CachedPlane cDst = new CachedPlane(dstLevel, dstX, dstY, dstZ, dstX + scaleX, dstY + scaleY, dstZ + scaleZ);
        CachedPlane cSrc = new CachedPlane(srcLevel, srcX, srcY, srcZ, srcX + scaleX, srcY + scaleY, srcZ + scaleZ);

        cSrc.swap(cDst);

        loadChunks(srcLevel, srcBox);
        loadChunks(dstLevel, dstBox);

        ObjectList<Entity> srcEntities = new ObjectArrayList<>(srcLevel.getEntitiesWithinAABB(Entity.class, srcBox));
        ObjectList<Entity> dstEntities = new ObjectArrayList<>(dstLevel.getEntitiesWithinAABB(Entity.class, dstBox));

        for (Entity entity : dstEntities) {
            this.teleportEntity(entity, new TelDestination(srcLevel, srcBox, entity.posX, entity.posY, entity.posZ,
                -dstX + srcX, -dstY + srcY, -dstZ + srcZ));
        }

        for (Entity entity : srcEntities) {
            this.teleportEntity(entity, new TelDestination(dstLevel, dstBox, entity.posX, entity.posY, entity.posZ,
                -srcX + dstX, -srcY + dstY, -srcZ + dstZ));
        }

        for (BlockPos pos : cDst.getUpdates()) {
            cDst.getLevel().notifyNeighborsOfStateChange(pos, Blocks.AIR, true);
        }

        for (BlockPos pos : cSrc.getUpdates()) {
            cSrc.getLevel().notifyNeighborsOfStateChange(pos, Blocks.AIR, true);
        }

        this.transverseEdges(srcX - 1, srcY - 1, srcZ - 1, srcX + scaleX + 1, srcY + scaleY + 1, srcZ + scaleZ + 1,
            new TriggerUpdates(srcLevel));
        this.transverseEdges(dstX - 1, dstY - 1, dstZ - 1, dstX + scaleX + 1, dstY + scaleY + 1, dstZ + scaleZ + 1,
            new TriggerUpdates(dstLevel));

        this.transverseEdges(srcX, srcY, srcZ, srcX + scaleX, srcY + scaleY, srcZ + scaleZ,
            new TriggerUpdates(srcLevel));
        this.transverseEdges(dstX, dstY, dstZ, dstX + scaleX, dstY + scaleY, dstZ + scaleZ,
            new TriggerUpdates(dstLevel));
    }

    private void loadChunks(WorldServer level, AxisAlignedBB box) {
        int minChunkX = MathHelper.floor(box.minX) >> 4;
        int maxChunkX = MathHelper.floor(box.maxX) >> 4;
        int minChunkZ = MathHelper.floor(box.minZ) >> 4;
        int maxChunkZ = MathHelper.floor(box.maxZ) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.getChunkProvider().provideChunk(chunkX, chunkZ);
            }
        }
    }

    @Nullable
    private Entity teleportEntity(Entity entity, TelDestination destination) {
        WorldServer oldLevel;
        WorldServer newLevel;
        try {
            oldLevel = (WorldServer) entity.world;
            newLevel = destination.dim;
        } catch (Throwable ignored) {
            return entity;
        }

        if (oldLevel == null || newLevel == null) {
            return entity;
        }

        ObjectList<Entity> passengersOnOtherSide = new ObjectArrayList<>();
        if (entity.isBeingRidden()) {
            ObjectList<Entity> passengers = new ObjectArrayList<>(entity.getPassengers());
            for (Entity passenger : passengers) {
                passenger.dismountRidingEntity();
                Entity movedPassenger = this.teleportEntity(passenger, destination);
                if (movedPassenger != null) {
                    passengersOnOtherSide.add(movedPassenger);
                }
            }
        }

        if (entity.isRiding()) {
            Entity riding = entity.getRidingEntity();
            if (riding != null) {
                return this.teleportEntity(riding, destination);
            }
        }

        if (oldLevel.provider.getDimension() == newLevel.provider.getDimension()) {
            if (entity instanceof EntityPlayerMP player) {
                player.connection.setPlayerLocation(destination.x, destination.y, destination.z, player.rotationYaw,
                    player.rotationPitch);
                for (Entity passenger : passengersOnOtherSide) {
                    passenger.startRiding(player, true);
                }
                return player;
            }

            entity.setLocationAndAngles(destination.x, destination.y, destination.z, entity.rotationYaw,
                entity.rotationPitch);
            oldLevel.updateEntityWithOptionalForce(entity, false);
            for (Entity passenger : passengersOnOtherSide) {
                passenger.startRiding(entity, true);
            }
            return entity;
        }

        SpatialTeleporter teleporter = new SpatialTeleporter(newLevel, destination.x, destination.y, destination.z);
        if (entity instanceof EntityPlayerMP player) {
            player.getServer().getPlayerList().transferPlayerToDimension(player, newLevel.provider.getDimension(),
                teleporter);
            player.connection.setPlayerLocation(destination.x, destination.y, destination.z, player.rotationYaw,
                player.rotationPitch);
            for (Entity passenger : passengersOnOtherSide) {
                passenger.startRiding(player, true);
            }
            return player;
        }

        Entity movedEntity = entity.changeDimension(newLevel.provider.getDimension(), teleporter);
        if (movedEntity != null) {
            for (Entity passenger : passengersOnOtherSide) {
                passenger.startRiding(movedEntity, true);
            }
        }
        return movedEntity;
    }

    private record TriggerUpdates(World dst) implements ISpatialVisitor {

        @Override
        public void visit(BlockPos pos) {
            IBlockState state = this.dst.getBlockState(pos);
            this.dst.neighborChanged(pos, state.getBlock(), pos);
        }
    }

    private record WrapInMatrixFrame(IBlockState state, World dst) implements ISpatialVisitor {

        @Override
        public void visit(BlockPos pos) {
            this.dst.setBlockState(pos, this.state, 2);
        }
    }

    private static class TelDestination {
        private final WorldServer dim;
        private final double x;
        private final double y;
        private final double z;

        private TelDestination(WorldServer dimension, AxisAlignedBB srcBox, double x, double y, double z,
                               int dx, int dy, int dz) {
            this.dim = dimension;
            this.x = Math.clamp(x + dx, srcBox.minX + 0.5, srcBox.maxX - 0.5);
            this.y = Math.clamp(y + dy, srcBox.minY + 0.5, srcBox.maxY - 0.5);
            this.z = Math.clamp(z + dz, srcBox.minZ + 0.5, srcBox.maxZ - 0.5);
        }
    }

    private static class SpatialTeleporter extends Teleporter {

        private final double x;
        private final double y;
        private final double z;

        private SpatialTeleporter(WorldServer worldIn, double x, double y, double z) {
            super(worldIn);
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public void placeInPortal(Entity entity, float rotationYaw) {
            entity.setLocationAndAngles(this.x, this.y, this.z, entity.rotationYaw, entity.rotationPitch);
        }

        @Override
        public boolean placeInExistingPortal(Entity entityIn, float rotationYaw) {
            this.placeInPortal(entityIn, rotationYaw);
            return true;
        }

        @Override
        public boolean makePortal(Entity entityIn) {
            return true;
        }

        @Override
        public void removeStalePortalLocations(long worldTime) {
        }
    }
}
