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

package ae2.tile.spatial;

import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.networking.GridFlags;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.events.statistics.GridChunkEvent.GridChunkAdded;
import ae2.api.networking.events.statistics.GridChunkEvent.GridChunkRemoved;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.util.AECableType;
import ae2.api.util.AEColor;
import ae2.api.util.DimensionalBlockPos;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigurableObject;
import ae2.client.render.overlay.IOverlayDataSource;
import ae2.client.render.overlay.OverlayManager;
import ae2.core.AELog;
import ae2.core.definitions.AEBlocks;
import ae2.me.service.StatisticsService;
import ae2.server.services.ChunkLoadingService;
import ae2.tile.grid.AENetworkedTile;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

public class TileSpatialAnchor extends AENetworkedTile
    implements IGridTickable, IConfigurableObject, IOverlayDataSource {

    private static final int SPATIAL_TRANSFER_TEMPORARY_CHUNK_RANGE = 4;

    static {
        GridHelper.addNodeOwnerEventHandler(GridChunkAdded.class, TileSpatialAnchor.class,
            TileSpatialAnchor::chunkAdded);
        GridHelper.addNodeOwnerEventHandler(GridChunkRemoved.class, TileSpatialAnchor.class,
            TileSpatialAnchor::chunkRemoved);
    }

    private final IConfigManager manager;
    private final Set<ChunkPos> chunks = new ObjectOpenHashSet<>();
    private final Set<ChunkPos> receivedChunks = new ObjectOpenHashSet<>();
    private IGrid observedGrid;
    private boolean observedOnline;
    private int powerlessTicks;
    private long lastPowerCheckTick = -1;
    private boolean initialized;
    private boolean chunksDirty = true;
    private boolean displayOverlay;
    private boolean active;
    private int clientChunkCount;

    public TileSpatialAnchor() {
        this.getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).addService(IGridTickable.class, this);
        this.manager = IConfigManager.builder(this::onSettingChanged)
                                     .registerSetting(Settings.OVERLAY_MODE, YesNo.NO)
                                     .build();
    }

    @Override
    public ItemStack getItemFromTile() {
        return AEBlocks.SPATIAL_ANCHOR.stack();
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.manager.readFromNBT(data);
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        this.manager.writeToNBT(data);
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(this.isActive());
        data.writeBoolean(this.displayOverlay);
        data.writeInt(this.chunks.size());
        if (this.displayOverlay) {
            for (ChunkPos chunk : this.chunks) {
                data.writeInt(chunk.x);
                data.writeInt(chunk.z);
            }
        }
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean nextActive = data.readBoolean();
        changed = changed || nextActive != this.active;
        this.active = nextActive;

        boolean nextDisplay = data.readBoolean();
        changed = changed || nextDisplay != this.displayOverlay;
        int chunkCount = data.readInt();
        // The temporary transfer radius does not limit the number of chunks in the actual network.
        if (chunkCount < 0 || nextDisplay && chunkCount > data.readableBytes() / (Integer.BYTES * 2)) {
            AELog.warn("Invalid spatial anchor chunk display count: %s", chunkCount);
            return changed;
        }
        this.clientChunkCount = chunkCount;
        this.receivedChunks.clear();
        if (nextDisplay) {
            for (int i = 0; i < chunkCount; i++) {
                this.receivedChunks.add(new ChunkPos(data.readInt(), data.readInt()));
            }
        }
        boolean overlayChanged = nextDisplay != this.displayOverlay || !this.chunks.equals(this.receivedChunks);
        this.displayOverlay = nextDisplay;
        if (overlayChanged) {
            this.chunks.clear();
            this.chunks.addAll(this.receivedChunks);
            if (this.world != null && this.world.isRemote) {
                OverlayManager.getInstance().removeHandlers(this);
                if (this.displayOverlay) {
                    OverlayManager.getInstance().showArea(this);
                }
            }
        }
        this.receivedChunks.clear();

        return changed;
    }

    @Override
    public AECableType getCableConnectionType(EnumFacing dir) {
        return super.getCableConnectionType(dir);
    }

    public void chunkAdded(GridChunkAdded changed) {
        if (changed.getLevel() == this.getServerLevel()) {
            this.wakeUp();
        }
    }

    public void chunkRemoved(GridChunkRemoved changed) {
        if (changed.getLevel() == this.getServerLevel()) {
            this.wakeUp();
        }
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        IGrid grid = this.getMainNode().getGrid();
        boolean online = this.getMainNode().isOnline();
        if (this.observedGrid == grid && this.observedOnline == online) {
            return;
        }
        this.observedGrid = grid;
        this.observedOnline = online;
        if (this.getMainNode().isOnline()) {
            this.powerlessTicks = 0;
            this.lastPowerCheckTick = -1;
        } else if (this.lastPowerCheckTick < 0 && this.world != null) {
            this.lastPowerCheckTick = this.world.getTotalWorldTime();
        }
        if (reason != IGridNodeListener.State.GRID_BOOT) {
            this.markForUpdate();
        }
        this.wakeUp();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.world != null && this.world.isRemote) {
            OverlayManager.getInstance().removeHandlers(this);
        } else {
            this.releaseAll();
        }
    }

    @Override
    public void onChunkUnloaded() {
        if (this.world != null && this.world.isRemote) {
            OverlayManager.getInstance().removeHandlers(this);
        } else {
            this.releaseAll();
        }
        super.onChunkUnloaded();
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.manager;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(20, 20, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!this.initialized) {
            if (!this.getMainNode().hasGridBooted()) {
                return TickRateModulation.SAME;
            }
            this.initialized = true;
        }

        if (!this.getMainNode().isOnline()) {
            long now = this.world.getTotalWorldTime();
            if (this.lastPowerCheckTick < 0) {
                this.lastPowerCheckTick = now;
            }
            this.powerlessTicks = Math.clamp(now - this.lastPowerCheckTick, 0, 201);
            if (this.powerlessTicks > 200) {
                this.releaseAll();
                return TickRateModulation.SLEEP;
            }
            return TickRateModulation.SAME;
        }

        this.powerlessTicks = 0;
        this.lastPowerCheckTick = -1;
        this.chunksDirty = false;
        this.cleanUp();
        return this.chunksDirty ? TickRateModulation.SAME : TickRateModulation.SLEEP;
    }

    @SuppressWarnings("unused")
    public Set<ChunkPos> getLoadedChunks() {
        return Collections.unmodifiableSet(this.chunks);
    }

    public int countLoadedChunks() {
        return this.world != null && this.world.isRemote ? this.clientChunkCount : this.chunks.size();
    }

    public boolean isActive() {
        if (this.world != null && !this.world.isRemote) {
            return this.getMainNode().isOnline();
        }
        return this.active;
    }

    public void registerChunk(ChunkPos chunkPos) {
        if (this.chunks.add(chunkPos)) {
            this.updatePowerConsumption();
            this.markForClientUpdate();
        }
    }

    public void doneMoving() {
        WorldServer serverLevel = this.getServerLevel();
        if (serverLevel == null) {
            return;
        }

        this.initialized = false;

        ChunkPos origin = new ChunkPos(this.pos);
        for (int dx = -SPATIAL_TRANSFER_TEMPORARY_CHUNK_RANGE; dx <= SPATIAL_TRANSFER_TEMPORARY_CHUNK_RANGE; dx++) {
            for (int dz = -SPATIAL_TRANSFER_TEMPORARY_CHUNK_RANGE; dz <= SPATIAL_TRANSFER_TEMPORARY_CHUNK_RANGE; dz++) {
                ChunkPos chunkPos = new ChunkPos(origin.x + dx, origin.z + dz);
                this.force(chunkPos);
            }
        }
        this.wakeUp();
    }

    private void wakeUp() {
        this.chunksDirty = true;
        this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    private void onSettingChanged() {
        boolean nextDisplay = this.manager.getSetting(Settings.OVERLAY_MODE) == YesNo.YES;
        if (this.displayOverlay != nextDisplay) {
            this.displayOverlay = nextDisplay;
            this.markForUpdate();
        }
        this.saveChanges();
    }

    private void updatePowerConsumption() {
        if (this.isInvalid()) {
            return;
        }
        double energy = 80 + (double) this.chunks.size() * (this.chunks.size() + 1L) / 2;
        this.getMainNode().setIdlePowerUsage(energy);
    }

    private void cleanUp() {
        IGrid grid = this.getMainNode().getGrid();
        if (grid == null || this.getServerLevel() == null) {
            return;
        }

        Multiset<ChunkPos> requiredChunks = grid.getService(StatisticsService.class).getChunks().get(this.getServerLevel());
        if (requiredChunks == null) {
            requiredChunks = ImmutableMultiset.of();
        }
        this.receivedChunks.clear();
        this.receivedChunks.addAll(requiredChunks.elementSet());

        boolean removed = false;
        for (Iterator<ChunkPos> iterator = this.chunks.iterator(); iterator.hasNext(); ) {
            ChunkPos chunkPos = iterator.next();
            if (!this.receivedChunks.contains(chunkPos)) {
                iterator.remove();
                ChunkLoadingService.getInstance().releaseChunk(this.getServerLevel(), this.pos, chunkPos,
                    this.chunks.isEmpty());
                removed = true;
            }
        }

        if (removed) {
            this.updatePowerConsumption();
            this.markForClientUpdate();
        }

        for (ChunkPos chunkPos : this.receivedChunks) {
            if (!this.chunks.contains(chunkPos)) {
                this.force(chunkPos);
            }
        }
        this.receivedChunks.clear();
    }

    private void force(ChunkPos chunkPos) {
        WorldServer level = this.getServerLevel();
        if (level == null || this.isInvalid() || this.chunks.contains(chunkPos)) {
            return;
        }

        boolean forced = ChunkLoadingService.getInstance().forceChunk(level, this.pos, chunkPos);
        if (forced && this.chunks.add(chunkPos)) {
            this.updatePowerConsumption();
            this.markForClientUpdate();
        }
    }

    public void releaseAll() {
        if (this.getServerLevel() != null) {
            ChunkLoadingService.getInstance().releaseOwner(this.getServerLevel(), this.pos);
        }
        boolean changed = !this.chunks.isEmpty();
        this.chunks.clear();
        if (changed) {
            this.updatePowerConsumption();
            this.markForClientUpdate();
        }
    }

    @Override
    public Set<ChunkPos> getOverlayChunks() {
        return Collections.unmodifiableSet(this.chunks);
    }

    @Override
    public TileEntity getOverlayTileEntity() {
        return this;
    }

    @Override
    public DimensionalBlockPos getOverlaySourceLocation() {
        return new DimensionalBlockPos(this);
    }

    @Override
    public int getOverlayColor() {
        return 0x80000000 | AEColor.TRANSPARENT.mediumVariant;
    }

    @Nullable
    private WorldServer getServerLevel() {
        return this.world instanceof WorldServer ? (WorldServer) this.world : null;
    }
}
