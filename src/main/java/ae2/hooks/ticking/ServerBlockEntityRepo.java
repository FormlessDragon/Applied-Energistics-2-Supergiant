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
package ae2.hooks.ticking;

import ae2.util.ChunkPosUtils;
import ae2.util.EmptyArrays;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.Consumer;

class ServerBlockEntityRepo {
    private final Object2ObjectMap<World, Long2ObjectMap<ObjectList<FirstTickInfo<?>>>> blockEntities = new Object2ObjectOpenHashMap<>();

    synchronized void clear() {
        this.blockEntities.clear();
    }

    synchronized <T extends TileEntity> void addBlockEntity(T blockEntity, Consumer<? super T> initFunction) {
        World level = blockEntity.getWorld();
        long chunkPos = ChunkPosUtils.asLong(blockEntity.getPos().getX() >> 4, blockEntity.getPos().getZ() >> 4);

        Long2ObjectMap<ObjectList<FirstTickInfo<?>>> worldQueue = this.blockEntities.computeIfAbsent(level,
            ignored -> new Long2ObjectOpenHashMap<>());

        worldQueue.computeIfAbsent(chunkPos, ignored -> new ObjectArrayList<>())
                  .add(new FirstTickInfo<>(blockEntity, initFunction));
    }

    synchronized void removeLevel(World level) {
        this.blockEntities.remove(level);
    }

    synchronized ObjectList<FirstTickInfo<?>> removeChunk(World level, long chunkPos) {
        Long2ObjectMap<ObjectList<FirstTickInfo<?>>> queue = this.blockEntities.get(level);
        if (queue != null) {
            return queue.remove(chunkPos);
        }
        return null;
    }

    synchronized long[] getQueuedChunks(World level) {
        Long2ObjectMap<ObjectList<FirstTickInfo<?>>> queue = this.blockEntities.get(level);
        if (queue == null) {
            return EmptyArrays.EMPTY_LONG_ARRAY;
        }
        return queue.keySet().toLongArray();
    }

    public synchronized List<ITextComponent> getReport() {
        List<ITextComponent> result = new ObjectArrayList<>();

        for (Object2ObjectMap.Entry<World, Long2ObjectMap<ObjectList<FirstTickInfo<?>>>> levelEntry : this.blockEntities.object2ObjectEntrySet()) {
            if (levelEntry.getValue().isEmpty()) {
                continue;
            }

            result.add(new TextComponentString(levelEntry.getKey().provider.getDimensionType().getName())
                .setStyle(new Style().setBold(true)));

            for (Long2ObjectMap.Entry<ObjectList<FirstTickInfo<?>>> chunkEntry : levelEntry.getValue().long2ObjectEntrySet()) {
                result.add(new TextComponentString(ChunkPosUtils.getX(chunkEntry.getLongKey()) + "," + ChunkPosUtils.getZ(chunkEntry.getLongKey()) + ": " + chunkEntry.getValue().size())
                    .setStyle(new Style().setColor(TextFormatting.WHITE)));
            }
        }

        return result;
    }

    record FirstTickInfo<T extends TileEntity>(T blockEntity, Consumer<? super T> initFunction) {
        void callInit() {
            this.initFunction.accept(this.blockEntity);
        }
    }
}
