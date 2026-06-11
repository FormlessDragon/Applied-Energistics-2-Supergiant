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
import ae2.core.definitions.AEBlocks;
import ae2.me.service.StatisticsService;
import ae2.server.services.ChunkLoadingService;
import ae2.tile.grid.AENetworkedTile;
import com.google.common.collect.HashMultiset;
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
    private static final int MAX_SYNCED_CHUNKS = (SPATIAL_TRANSFER_TEMPORARY_CHUNK_RANGE * 2 + 1)
        * (SPATIAL_TRANSFER_TEMPORARY_CHUNK_RANGE * 2 + 1);

    static {
        GridHelper.addNodeOwnerEventHandler(GridChunkAdded.class, TileSpatialAnchor.class,
            TileSpatialAnchor::chunkAdded);
        GridHelper.addNodeOwnerEventHandler(GridChunkRemoved.class, TileSpatialAnchor.class,
            TileSpatialAnchor::chunkRemoved);
    }

    private final IConfigManager manager;
    private final Set<ChunkPos> chunks = new ObjectOpenHashSet<>();
    private int powerlessTicks;
    private boolean initialized;
    private boolean displayOverlay;
    private boolean active;

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
        for (ChunkPos chunk : this.chunks) {
            data.writeInt(chunk.x);
            data.writeInt(chunk.z);
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
        this.displayOverlay = nextDisplay;

        this.chunks.clear();
        if (this.world != null && this.world.isRemote) {
            OverlayManager.getInstance().removeHandlers(this);
        }

        int chunkCount = data.readInt();
        if (chunkCount < 0 || chunkCount > MAX_SYNCED_CHUNKS || data.readableBytes() < chunkCount * Integer.BYTES * 2) {
            return changed;
        }
        for (int i = 0; i < chunkCount; i++) {
            this.chunks.add(new ChunkPos(data.readInt(), data.readInt()));
        }

        if (this.displayOverlay && this.world != null && this.world.isRemote) {
            OverlayManager.getInstance().showArea(this);
        }

        return changed;
    }

    @Override
    public AECableType getCableConnectionType(EnumFacing dir) {
        return super.getCableConnectionType(dir);
    }

    public void chunkAdded(GridChunkAdded changed) {
        if (changed.getLevel() == this.getServerLevel()) {
            this.force(changed.getChunkPos());
        }
    }

    public void chunkRemoved(GridChunkRemoved changed) {
        if (changed.getLevel() == this.getServerLevel()) {
            this.release(changed.getChunkPos(), true);
            this.wakeUp();
        }
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
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

        this.cleanUp();

        if (this.powerlessTicks > 200) {
            if (!this.getMainNode().isOnline()) {
                this.releaseAll();
            }
            this.powerlessTicks = 0;
            return TickRateModulation.SLEEP;
        }

        if (!this.getMainNode().isOnline()) {
            this.powerlessTicks += ticksSinceLastCall;
            return TickRateModulation.SAME;
        }

        return TickRateModulation.SLEEP;
    }

    @SuppressWarnings("unused")
    public Set<ChunkPos> getLoadedChunks() {
        return Collections.unmodifiableSet(this.chunks);
    }

    public int countLoadedChunks() {
        return this.chunks.size();
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
        this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    private void onSettingChanged() {
        if (Settings.OVERLAY_MODE == Settings.OVERLAY_MODE) {
            this.displayOverlay = this.manager.getSetting(Settings.OVERLAY_MODE) == YesNo.YES;
            this.markForUpdate();
        }
        this.saveChanges();
    }

    private void updatePowerConsumption() {
        if (this.isInvalid()) {
            return;
        }
        int energy = 80 + this.chunks.size() * (this.chunks.size() + 1) / 2;
        this.getMainNode().setIdlePowerUsage(energy);
    }

    private void cleanUp() {
        IGrid grid = this.getMainNode().getGrid();
        if (grid == null || this.getServerLevel() == null) {
            return;
        }

        Multiset<ChunkPos> requiredChunks = grid.getService(StatisticsService.class).getChunks().get(this.getServerLevel());
        if (requiredChunks == null) {
            requiredChunks = HashMultiset.create();
        }

        for (Iterator<ChunkPos> iterator = this.chunks.iterator(); iterator.hasNext(); ) {
            ChunkPos chunkPos = iterator.next();
            if (!requiredChunks.contains(chunkPos)) {
                this.release(chunkPos, false);
                iterator.remove();
            }
        }

        for (ChunkPos chunkPos : requiredChunks.elementSet()) {
            if (!this.chunks.contains(chunkPos)) {
                this.force(chunkPos);
            }
        }
    }

    private void force(ChunkPos chunkPos) {
        WorldServer level = this.getServerLevel();
        if (level == null || this.isInvalid()) {
            return;
        }

        boolean forced = ChunkLoadingService.getInstance().forceChunk(level, this.pos, chunkPos);
        if (forced && this.chunks.add(chunkPos)) {
            this.updatePowerConsumption();
            this.markForClientUpdate();
        }
    }

    private void release(ChunkPos chunkPos, boolean remove) {
        WorldServer level = this.getServerLevel();
        if (level == null) {
            return;
        }

        boolean released = ChunkLoadingService.getInstance().releaseChunk(level, this.pos, chunkPos);
        if (released && remove && this.chunks.remove(chunkPos)) {
            this.updatePowerConsumption();
            this.markForClientUpdate();
        }
    }

    public void releaseAll() {
        for (ChunkPos chunkPos : new ObjectOpenHashSet<>(this.chunks)) {
            this.release(chunkPos, true);
        }
        this.chunks.clear();
        this.updatePowerConsumption();
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
