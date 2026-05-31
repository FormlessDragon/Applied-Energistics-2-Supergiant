/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

import ae2.api.features.IPlayerRegistry;
import ae2.api.networking.GridFlags;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridMultiblock;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.IGridServiceProvider;
import ae2.api.networking.events.GridBootingStatusChange;
import ae2.api.networking.events.GridChannelRequirementChanged;
import ae2.api.networking.events.GridControllerChange;
import ae2.api.networking.pathing.ChannelMode;
import ae2.api.networking.pathing.ControllerState;
import ae2.api.networking.pathing.IPathingService;
import ae2.core.AEConfig;
import ae2.core.AELog;
import ae2.core.AppEng;
import ae2.core.stats.IAdvancementTrigger;
import ae2.me.Grid;
import ae2.me.pathfinding.AdHocChannelUpdater;
import ae2.me.pathfinding.ChannelFinalizer;
import ae2.me.pathfinding.ControllerValidator;
import ae2.me.pathfinding.PathingCalculation;
import ae2.tile.networking.TileController;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.Nullable;

public class PathingService implements IPathingService, IGridServiceProvider {
    private static final String TAG_CHANNEL_MODE = "cm";

    static {
        GridHelper.addGridServiceEventHandler(GridChannelRequirementChanged.class,
            IPathingService.class,
            (service, event) -> ((PathingService) service).updateNodReq(event));
    }

    private final ObjectSet<TileController> controllers = new ObjectOpenHashSet<>();
    private final ReferenceSet<IGridNode> nodesNeedingChannels = new ReferenceOpenHashSet<>();
    private final ReferenceSet<IGridNode> cannotCarryCompressedNodes = new ReferenceOpenHashSet<>();
    private final Grid grid;
    private int channelsInUse = 0;
    private int channelsByBlocks = 0;
    private double channelPowerUsage = 0.0;
    private boolean recalculateControllerNextTick = true;
    // Flag to indicate a reboot should occur next tick
    private boolean reboot = true;
    private boolean booting = false;
    @Nullable
    private AdHocNetworkError adHocNetworkError;
    private ControllerState controllerState = ControllerState.NO_CONTROLLER;
    private int lastChannels = 0;
    /**
     * This can be used for testing to set a specific channel mode on this grid that will not be overwritten by
     * repathing.
     */
    private boolean channelModeLocked;
    private ChannelMode channelMode = AEConfig.instance().getChannelMode();

    public PathingService(IGrid g) {
        this.grid = (Grid) g;
    }

    @Override
    public void onServerEndTick() {
        if (this.recalculateControllerNextTick) {
            this.updateControllerState();
        }

        if (this.reboot) {
            this.reboot = false;

            // Preserve the illusion that the network is booting for a while before channel assignment completes.
            this.booting = true;
            this.postBootingStatusChange();

            this.channelsInUse = 0;
            this.adHocNetworkError = null;

            // updateControllerState / postBootingStatusChange called above can cause the grid to be destroyed,
            // and the pivot to become null.
            if (grid.isEmpty()) {
                return;
            }

            if (this.controllerState == ControllerState.NO_CONTROLLER) {
                // Returns 0 if there's an error
                this.channelsInUse = this.calculateAdHocChannels();

                var nodes = this.grid.size();
                this.channelsByBlocks = nodes * this.channelsInUse;
                this.setChannelPowerUsage(this.channelsByBlocks / 128.0);

                this.grid.getPivot().beginVisit(new AdHocChannelUpdater(this.channelsInUse));
            } else if (this.controllerState == ControllerState.CONTROLLER_CONFLICT) {
                this.grid.getPivot().beginVisit(new AdHocChannelUpdater(0));
                this.channelsInUse = 0;
                this.channelsByBlocks = 0;
            } else {
                var calculation = new PathingCalculation(grid);
                calculation.compute();
                this.channelsInUse = calculation.getChannelsInUse();
                this.channelsByBlocks = calculation.getChannelsByBlocks();
            }

            this.achievementPost();

            this.booting = false;
            this.setChannelPowerUsage(this.channelsByBlocks / 128.0);
            // Notify of channel changes AFTER we set booting to false, this ensures that any activeness check will
            // properly return true.
            this.grid.getPivot().beginVisit(new ChannelFinalizer());
            this.postBootingStatusChange();
        }
    }

    private void postBootingStatusChange() {
        this.grid.postEvent(new GridBootingStatusChange(this.booting));
        this.grid.notifyAllNodes(IGridNodeListener.State.GRID_BOOT);
    }

    @Override
    public void removeNode(IGridNode gridNode) {
        if (gridNode.getOwner() instanceof TileController controller) {
            this.controllers.remove(controller);
            this.recalculateControllerNextTick = true;
        }

        if (gridNode.hasFlag(GridFlags.REQUIRE_CHANNEL)) {
            this.nodesNeedingChannels.remove(gridNode);
        }

        if (gridNode.hasFlag(GridFlags.CANNOT_CARRY_COMPRESSED)) {
            this.cannotCarryCompressedNodes.remove(gridNode);
        }

        this.repath();
    }

    @Override
    public void addNode(IGridNode gridNode, @Nullable NBTTagCompound savedData) {
        if (savedData != null) {
            restoreChannelMode(savedData);
        }

        if (gridNode.getOwner() instanceof TileController controller) {
            this.controllers.add(controller);
            this.recalculateControllerNextTick = true;
        }

        if (gridNode.hasFlag(GridFlags.REQUIRE_CHANNEL)) {
            this.nodesNeedingChannels.add(gridNode);
        }

        if (gridNode.hasFlag(GridFlags.CANNOT_CARRY_COMPRESSED)) {
            this.cannotCarryCompressedNodes.add(gridNode);
        }

        this.repath();
    }

    private void restoreChannelMode(NBTTagCompound savedData) {
        // Adding a node to the grid will restore its saved channel mode to the grid
        // in case of conflict (i.e. merging two grids with conflicting modes),
        // the more relaxed mode will win.
        if (savedData.hasKey(TAG_CHANNEL_MODE, Constants.NBT.TAG_STRING)) {
            var channelModeName = savedData.getString(TAG_CHANNEL_MODE);
            try {
                var nodeChannelMode = ChannelMode.valueOf(channelModeName);
                if (!this.channelModeLocked
                    || nodeChannelMode.getAdHocNetworkChannels() > channelMode.getAdHocNetworkChannels()) {
                    channelModeLocked = true;
                    channelMode = nodeChannelMode;
                }
            } catch (IllegalArgumentException e) {
                AELog.warn("Invalid channel mode stored on grid node: %s", channelModeName);
            }
        }
    }

    private void updateControllerState() {
        this.recalculateControllerNextTick = false;
        final ControllerState old = this.controllerState;

        this.controllerState = ControllerValidator.calculateState(controllers);

        if (old != this.controllerState) {
            this.grid.postEvent(new GridControllerChange());
        }
    }

    @Nullable
    public AdHocNetworkError getAdHocNetworkError() {
        return adHocNetworkError;
    }

    private int calculateAdHocChannels() {
        var ignore = new ReferenceOpenHashSet<IGridNode>();

        this.adHocNetworkError = null;

        int channels = 0;
        for (var node : this.nodesNeedingChannels) {
            if (!ignore.contains(node)) {
                // Prevent ad-hoc networks from being connected to the outside and inside node of P2P tunnels at the
                // same time
                // this effectively prevents the nesting of P2P-tunnels in ad-hoc networks.
                if (node.hasFlag(GridFlags.COMPRESSED_CHANNEL) && !this.cannotCarryCompressedNodes.isEmpty()) {
                    this.adHocNetworkError = AdHocNetworkError.NESTED_P2P_TUNNEL;
                    return 0;
                }

                channels++;

                // Multiblocks only require a single channel. Add the remainder of the multi-block to the ignore-list,
                // to make this method skip them for channel calculation.
                if (node.hasFlag(GridFlags.MULTIBLOCK)) {
                    var multiblock = node.getService(IGridMultiblock.class);
                    if (multiblock != null) {
                        var it = multiblock.getMultiblockNodes();
                        while (it.hasNext()) {
                            ignore.add(it.next());
                        }
                    }
                }
            }
        }

        if (channels > channelMode.getAdHocNetworkChannels()) {
            this.adHocNetworkError = AdHocNetworkError.TOO_MANY_CHANNELS;
            return 0;
        }

        return channels;
    }

    private void achievementPost() {
        var server = grid.getPivot().getLevel().getMinecraftServer();

        if (this.lastChannels != this.channelsInUse) {
            final IAdvancementTrigger currentBracket = this.getAchievementBracket(this.channelsInUse);
            final IAdvancementTrigger lastBracket = this.getAchievementBracket(this.lastChannels);
            if (currentBracket != lastBracket && currentBracket != null) {
                for (var n : this.nodesNeedingChannels) {
                    var player = IPlayerRegistry.getConnected(server, n.getOwningPlayerId());
                    if (player != null) {
                        currentBracket.trigger(player);
                    }
                }
            }
        }
        this.lastChannels = this.channelsInUse;
    }

    @Nullable
    private IAdvancementTrigger getAchievementBracket(int ch) {
        if (ch < 8) {
            return null;
        }

        if (ch < 128) {
            return AppEng.instance().getAdvancementTriggers().getNetworkApprentice();
        }

        if (ch < 2048) {
            return AppEng.instance().getAdvancementTriggers().getNetworkEngineer();
        }

        return AppEng.instance().getAdvancementTriggers().getNetworkAdmin();
    }

    private void updateNodReq(GridChannelRequirementChanged ev) {
        final IGridNode gridNode = ev.node;

        if (gridNode.hasFlag(GridFlags.REQUIRE_CHANNEL)) {
            this.nodesNeedingChannels.add(gridNode);
        } else {
            this.nodesNeedingChannels.remove(gridNode);
        }

        this.repath();
    }

    @Override
    public boolean isNetworkBooting() {
        return this.booting;
    }

    @Override
    public ControllerState getControllerState() {
        return this.controllerState;
    }

    @Override
    public void repath() {
        if (!this.channelModeLocked) {
            this.channelMode = AEConfig.instance().getChannelMode();
        }

        this.channelsByBlocks = 0;
        this.reboot = true;
    }

    double getChannelPowerUsage() {
        return this.channelPowerUsage;
    }

    private void setChannelPowerUsage(double channelPowerUsage) {
        this.channelPowerUsage = channelPowerUsage;
    }

    @Override
    public ChannelMode getChannelMode() {
        return channelMode;
    }

    @Override
    public int getUsedChannels() {
        return channelsInUse;
    }

    @Override
    public void saveNodeData(IGridNode gridNode, NBTTagCompound savedData) {
        if (channelModeLocked) {
            savedData.setString(TAG_CHANNEL_MODE, channelMode.name());
        }
    }
}
