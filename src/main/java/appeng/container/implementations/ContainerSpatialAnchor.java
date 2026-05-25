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

package appeng.container.implementations;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.me.service.StatisticsService;
import appeng.tile.spatial.TileSpatialAnchor;
import com.google.common.collect.Multiset;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;

public class ContainerSpatialAnchor extends AEBaseContainer {

    private static final int UPDATE_DELAY = 20;
    @GuiSync(0)
    public long powerConsumption;
    @GuiSync(1)
    public int loadedChunks;
    @GuiSync(2)
    public YesNo overlayMode = YesNo.NO;
    @GuiSync(10)
    public int allLoadedWorlds;
    @GuiSync(11)
    public int allLoadedChunks;
    @GuiSync(20)
    public int allWorlds;
    @GuiSync(21)
    public int allChunks;
    private int delay = UPDATE_DELAY;

    public ContainerSpatialAnchor( InventoryPlayer ip, TileSpatialAnchor host) {
        super(ip, host);
    }

    @Override
    public void broadcastChanges() {
        if (this.isServerSide()) {
            TileSpatialAnchor anchor = (TileSpatialAnchor) this.getTileEntity();
            if (anchor != null) {
                this.overlayMode = anchor.getConfigManager().getSetting(Settings.OVERLAY_MODE);
                IGridNode gridNode = anchor.getGridNode();
                this.delay++;
                if (this.delay > UPDATE_DELAY && gridNode != null) {
                    IGrid grid = gridNode.grid();
                    if (grid == null) {
                        super.broadcastChanges();
                        return;
                    }
                    StatisticsService statistics = grid.getService(StatisticsService.class);
                    this.powerConsumption = (long) gridNode.getIdlePowerUsage();
                    this.loadedChunks = anchor.countLoadedChunks();

                    var stats = new Reference2IntOpenHashMap<WorldServer>();
                    stats.defaultReturnValue(0);
                    for (TileSpatialAnchor machine : grid.getMachines(TileSpatialAnchor.class)) {
                        WorldServer level = (WorldServer) machine.getWorld();
                        stats.put(level, Math.max(stats.getInt(level), machine.countLoadedChunks()));
                    }

                    this.allLoadedChunks = 0;
                    for (int loadedChunkCount : stats.values()) {
                        this.allLoadedChunks += loadedChunkCount;
                    }
                    this.allLoadedWorlds = stats.size();
                    this.allWorlds = statistics.getChunks().size();
                    this.allChunks = 0;
                    for (Multiset<ChunkPos> value : statistics.getChunks().values()) {
                        this.allChunks += value.elementSet().size();
                    }
                    this.delay = 0;
                }
            }
        }
        super.broadcastChanges();
    }
}
