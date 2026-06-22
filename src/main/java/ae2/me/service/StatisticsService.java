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

package ae2.me.service;

import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridService;
import ae2.api.networking.IGridServiceProvider;
import ae2.api.networking.events.statistics.GridChunkEvent;
import ae2.me.InWorldGridNode;
import ae2.util.JsonStreamUtil;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.gson.stream.JsonWriter;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * A grid providing precomupted statistics about a network.
 * <p>
 * Currently this tracks the chunks a network is occupying.
 */
public class StatisticsService implements IGridService, IGridServiceProvider {

    private final IGrid grid;

    /**
     * This uses a {@link Multiset} so we can simply add or remove {@link IGridNode} without having to take into account
     * that others still might exist without explicitly counting these.
     */
    private final Reference2ObjectMap<WorldServer, Multiset<ChunkPos>> chunks;

    public StatisticsService(IGrid g) {
        this.grid = g;
        this.chunks = new Reference2ObjectOpenHashMap<>();
    }

    @Override
    public void removeNode(IGridNode node) {
        if (node instanceof InWorldGridNode inWorldNode) {
            this.removeChunk(inWorldNode.getLevel(), inWorldNode.getLocation());
        }
    }

    @Override
    public void addNode(IGridNode node, @Nullable NBTTagCompound savedData) {
        if (node instanceof InWorldGridNode inWorldNode) {
            this.addChunk(inWorldNode.getLevel(), inWorldNode.getLocation());
        }
    }

    public IGrid getGrid() {
        return grid;
    }

    /**
     * A set of all {@link WorldServer} this grid spans.
     */
    @SuppressWarnings("unused")
    public Set<WorldServer> getLevels() {
        return this.chunks.keySet();
    }

    /**
     * A set of chunks this grid spans in a specific level.
     */
    @SuppressWarnings("unused")
    public Set<ChunkPos> chunks(WorldServer level) {
        return this.chunks.get(level).elementSet();
    }

    public Reference2ObjectMap<WorldServer, Multiset<ChunkPos>> getChunks() {
        return this.chunks;
    }

    /**
     * Mark the chunk of the {@link BlockPos} as location of the network.
     */
    private void addChunk(WorldServer level, BlockPos pos) {
        final ChunkPos position = new ChunkPos(pos);

        if (!this.getChunks(level).contains(position)) {
            this.grid.postEvent(new GridChunkEvent.GridChunkAdded(level, position));
        }

        this.getChunks(level).add(position);
    }

    /**
     * Remove the chunk of this {@link BlockPos} from the network locations.
     * <p>
     * This uses a {@link Multiset} to ensure it will only be marked as no longer containing a grid once all other
     * gridnodes are removed as well.
     */
    private void removeChunk(WorldServer level, BlockPos pos) {
        final ChunkPos position = new ChunkPos(pos);
        boolean removed = this.getChunks(level).remove(position);

        if (removed && !this.getChunks(level).contains(position)) {
            this.grid.postEvent(new GridChunkEvent.GridChunkRemoved(level, position));
        }

        this.clearLevel(level);
    }

    private Multiset<ChunkPos> getChunks(WorldServer level) {
        return this.chunks.computeIfAbsent(level, ignored -> HashMultiset.create());
    }

    /**
     * Cleanup the map in case a whole level is unloaded
     */
    private void clearLevel(WorldServer level) {
        if (this.chunks.get(level).isEmpty()) {
            this.chunks.remove(level);
        }
    }

    @Override
    public void debugDump(JsonWriter writer) throws IOException {
        Map<String, Object> chunksJson = new Object2ObjectOpenHashMap<>(chunks.size());
        for (var level : chunks.keySet()) {
            var levelChunks = chunks.get(level).elementSet();
            var chunkJson = new ObjectArrayList<>(levelChunks.size());
            for (var chunk : levelChunks) {
                chunkJson.add(JsonStreamUtil.toJson(chunk));
            }
            chunksJson.put(Integer.toString(level.provider.getDimension()), chunkJson);
        }

        JsonStreamUtil.writeProperties(Map.of("chunks", chunksJson), writer);
    }
}
