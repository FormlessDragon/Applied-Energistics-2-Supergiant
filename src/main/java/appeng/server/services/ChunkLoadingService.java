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

package appeng.server.services;

import appeng.core.AppEngBase;
import appeng.tile.spatial.TileSpatialAnchor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

import javax.annotation.Nullable;
import java.util.List;

public class ChunkLoadingService implements ForgeChunkManager.LoadingCallback {

    private static final String TAG_OWNER_X = "ownerX";
    private static final String TAG_OWNER_Y = "ownerY";
    private static final String TAG_OWNER_Z = "ownerZ";
    private static final ChunkLoadingService INSTANCE = new ChunkLoadingService();

    private final Int2ObjectMap<Object2ObjectMap<BlockPos, Ticket>> ticketsByDimension = new Int2ObjectOpenHashMap<>();
    private boolean running = true;

    public static ChunkLoadingService getInstance() {
        return INSTANCE;
    }

    public void onServerAboutToStart() {
        this.running = true;
        this.ticketsByDimension.clear();
    }

    public void onServerStopping() {
        this.running = false;
        this.ticketsByDimension.clear();
    }

    @Override
    public void ticketsLoaded(List<Ticket> tickets, World world) {
        if (!(world instanceof WorldServer serverLevel)) {
            return;
        }

        for (Ticket ticket : tickets) {
            NBTTagCompound modData = ticket.getModData();
            if (modData == null) {
                ForgeChunkManager.releaseTicket(ticket);
                continue;
            }

            BlockPos ownerPos = new BlockPos(modData.getInteger(TAG_OWNER_X), modData.getInteger(TAG_OWNER_Y),
                modData.getInteger(TAG_OWNER_Z));
            TileEntity tileEntity = serverLevel.getTileEntity(ownerPos);
            if (!(tileEntity instanceof TileSpatialAnchor anchor)) {
                ForgeChunkManager.releaseTicket(ticket);
                continue;
            }

            this.ticketsByDimension.computeIfAbsent(serverLevel.provider.getDimension(),
                    ignored -> new Object2ObjectOpenHashMap<>())
                                   .put(ownerPos, ticket);

            for (ChunkPos chunkPos : ticket.getChunkList()) {
                anchor.registerChunk(chunkPos);
            }
        }
    }

    public boolean forceChunk(WorldServer level, BlockPos owner, ChunkPos position) {
        if (!this.running) {
            return false;
        }

        Ticket ticket = this.getOrCreateTicket(level, owner);
        if (ticket == null) {
            return false;
        }

        ForgeChunkManager.forceChunk(ticket, position);
        return true;
    }

    public boolean releaseChunk(WorldServer level, BlockPos owner, ChunkPos position) {
        Object2ObjectMap<BlockPos, Ticket> tickets = this.ticketsByDimension.get(level.provider.getDimension());
        if (tickets == null) {
            return false;
        }

        Ticket ticket = tickets.get(owner);
        if (ticket == null) {
            return false;
        }

        ForgeChunkManager.unforceChunk(ticket, position);
        if (ticket.getChunkList().isEmpty()) {
            ForgeChunkManager.releaseTicket(ticket);
            tickets.remove(owner);
            if (tickets.isEmpty()) {
                this.ticketsByDimension.remove(level.provider.getDimension());
            }
        }
        return true;
    }

    private @Nullable Ticket getOrCreateTicket(WorldServer level, BlockPos owner) {
        Object2ObjectMap<BlockPos, Ticket> tickets = this.ticketsByDimension.computeIfAbsent(level.provider.getDimension(),
            ignored -> new Object2ObjectOpenHashMap<>());
        Ticket existing = tickets.get(owner);
        if (existing != null) {
            return existing;
        }

        Ticket ticket = ForgeChunkManager.requestTicket(AppEngBase.instance(), level, ForgeChunkManager.Type.NORMAL);
        if (ticket == null) {
            return null;
        }

        NBTTagCompound modData = ticket.getModData();
        modData.setInteger(TAG_OWNER_X, owner.getX());
        modData.setInteger(TAG_OWNER_Y, owner.getY());
        modData.setInteger(TAG_OWNER_Z, owner.getZ());
        tickets.put(owner, ticket);
        return ticket;
    }
}
