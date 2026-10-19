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

package ae2.container.implementations;

import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IInWorldGridNodeHost;
import ae2.container.AEBaseContainer;
import ae2.container.guisync.GuiSync;
import ae2.container.networking.NetworkStatus;
import ae2.core.AEConfig;
import ae2.core.network.clientbound.NetworkStatusPacket;
import ae2.me.Grid;
import ae2.me.service.IngredientFlowService;
import ae2.server.Commands;
import ae2.server.subcommands.GridsCommand;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class ContainerNetworkStatus extends AEBaseContainer {
    private static final String ACTION_EXPORT_GRID = "export_grid";
    private static final String ACTION_TOGGLE_FLOW_TRACKING = "toggle_flow_tracking";
    private static final AtomicLong NEXT_STATUS_GENERATION = new AtomicLong(1);

    @Nullable
    private IGrid grid;
    private static final int MAX_STATUS_PAGES = 64;
    private static final int MAX_STATUS_PAGE_BYTES = 900_000;
    @Nullable
    private IInWorldGridNodeHost gridHost;
    private int delay = 40;
    private NetworkStatus status = new NetworkStatus();
    private boolean canExportGrid;
    @Nullable
    private EnumFacing gridSide;
    @Nullable
    private IGridNode gridNode;
    private long statusGeneration = NEXT_STATUS_GENERATION.getAndIncrement();
    private long statusRevision;
    private long lastAcceptedStatusGeneration;
    private long lastAcceptedStatusRevision;
    @Nullable
    private List<NetworkStatus> pendingStatusPages;
    private int pendingStatusPageCount;
    private int pendingStatusPagesReceived;
    private boolean pendingCanExportGrid;
    @GuiSync(35)
    public boolean flowTrackingMode;
    @GuiSync(36)
    public boolean flowTrackingGloballyEnabled;

    public ContainerNetworkStatus(InventoryPlayer ip, IInWorldGridNodeHost host) {
        super(ip, host);

        buildForGridHost(host);
    }

    private static boolean fitsStatusPage(NetworkStatusPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            packet.toBytes(buffer);
            return buffer.readableBytes() <= MAX_STATUS_PAGE_BYTES;
        } catch (RuntimeException e) {
            return false;
        } finally {
            buffer.release();
        }
    }

    private void buildForGridHost(@Nullable IInWorldGridNodeHost gridHost) {
        this.gridHost = gridHost;
        if (gridHost != null) {
            for (var side : EnumFacing.VALUES) {
                if (findNode(gridHost, side)) {
                    break;
                }
            }
        }

        if (this.grid == null && isServerSide()) {
            this.setValidContainer(false);
        }

        registerClientAction(ACTION_EXPORT_GRID, this::exportGrid);
        registerClientAction(ACTION_TOGGLE_FLOW_TRACKING, this::toggleFlowTrackingMode);
    }

    private boolean findNode(IInWorldGridNodeHost host, EnumFacing side) {
        IGridNode node = host.getGridNode(side);
        if (node != null) {
            this.gridSide = side;
            this.gridNode = node;
            this.grid = node.grid();
            return true;
        }
        return false;
    }

    private void resolveCurrentGrid() {
        if (this.gridHost == null || this.gridSide == null) {
            return;
        }

        IGridNode currentNode = this.gridHost.getGridNode(this.gridSide);
        IGrid currentGrid = currentNode != null && currentNode.isActive() ? currentNode.grid() : null;
        if (currentNode == this.gridNode && currentGrid == this.grid) {
            return;
        }

        this.gridNode = currentNode;
        this.grid = currentGrid;
        this.statusGeneration = NEXT_STATUS_GENERATION.getAndIncrement();
        this.statusRevision = 0;
        this.status = new NetworkStatus();
        this.canExportGrid = false;
        this.refreshFlowTrackingState();
    }

    public NetworkStatus getStatus() {
        return status;
    }

    public void setStatus(NetworkStatus status) {
        this.status = status;
    }

    public void setCanExportGrid(boolean canExportGrid) {
        this.canExportGrid = canExportGrid;
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            resolveCurrentGrid();
            if (this.grid == null) {
                this.setValidContainer(false);
            }
        }
        this.delay++;
        if (isServerSide() && this.delay > 15 && this.grid != null) {
            this.delay = 0;
            long currentTick = this.getPlayerInventory().player.world.getTotalWorldTime();
            this.status = NetworkStatus.fromGridShared(this.grid, currentTick);
            this.canExportGrid = computeCanExportGrid();
            this.refreshFlowTrackingState();
            this.statusRevision++;
            sendStatusPages();
        }
        super.broadcastChanges();
    }

    private void sendStatusPages() {
        var groups = new ObjectArrayList<>(this.status.getGroupedMachines());
        int pageCount = Math.max(1, (groups.size() + NetworkStatus.MAX_MACHINE_GROUPS - 1)
            / NetworkStatus.MAX_MACHINE_GROUPS);
        if (pageCount > MAX_STATUS_PAGES) {
            sendOversizeStatus();
            return;
        }

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int from = pageIndex * NetworkStatus.MAX_MACHINE_GROUPS;
            int to = Math.min(groups.size(), from + NetworkStatus.MAX_MACHINE_GROUPS);
            var pageStatus = this.status.withGroupedMachines(groups.subList(from, to));
            var packet = new NetworkStatusPacket(this.windowId, this.statusGeneration, this.statusRevision,
                pageIndex, pageCount, false, pageStatus, this.canExportGrid);
            if (!fitsStatusPage(packet)) {
                sendOversizeStatus();
                return;
            }
            sendPacketToClient(packet);
        }
    }

    private void sendOversizeStatus() {
        sendPacketToClient(new NetworkStatusPacket(this.windowId, this.statusGeneration, this.statusRevision,
            0, 1, true, new NetworkStatus(), false));
    }

    public boolean acceptStatusIdentity(int windowId, long sourceGeneration, long snapshotRevision) {
        if (windowId != this.windowId || sourceGeneration <= 0 || snapshotRevision <= 0) {
            return false;
        }

        if (this.lastAcceptedStatusGeneration == sourceGeneration) {
            return snapshotRevision > this.lastAcceptedStatusRevision;
        }

        // A new grid source starts its own revision sequence. Generation is process-local and monotonic,
        // so a delayed packet from an older source can never replace the current source.
        if (this.lastAcceptedStatusGeneration != 0 && sourceGeneration <= this.lastAcceptedStatusGeneration) {
            return false;
        }

        this.lastAcceptedStatusGeneration = sourceGeneration;
        this.lastAcceptedStatusRevision = snapshotRevision;
        return true;
    }

    public boolean acceptStatusPage(int windowId, long sourceGeneration, long snapshotRevision,
                                    int pageIndex, int pageCount, boolean oversize,
                                    @Nullable NetworkStatus page, boolean canExportGrid) {
        boolean validPageShape = oversize
            ? page == null && pageIndex == 0 && pageCount == 1
            : page != null && pageIndex >= 0 && pageIndex < pageCount;
        if (!validPageShape || windowId != this.windowId || sourceGeneration <= 0 || snapshotRevision <= 0
            || pageCount > MAX_STATUS_PAGES) {
            return false;
        }

        if (this.lastAcceptedStatusGeneration != 0 && sourceGeneration < this.lastAcceptedStatusGeneration) {
            return false;
        }
        if (sourceGeneration > this.lastAcceptedStatusGeneration) {
            // A new Grid starts its own revision sequence; it may begin below the previous Grid's revision.
            this.pendingStatusPages = null;
            this.pendingStatusPageCount = 0;
            this.pendingStatusPagesReceived = 0;
            this.lastAcceptedStatusGeneration = sourceGeneration;
            this.lastAcceptedStatusRevision = 0;
        }
        if (this.lastAcceptedStatusGeneration == sourceGeneration) {
            if (this.pendingStatusPages == null && snapshotRevision <= this.lastAcceptedStatusRevision) {
                return false;
            }
            if (this.pendingStatusPages != null && snapshotRevision < this.lastAcceptedStatusRevision) {
                return false;
            }
        }

        if (oversize) {
            this.pendingStatusPages = null;
            this.lastAcceptedStatusRevision = snapshotRevision;
            this.status = new NetworkStatus();
            this.canExportGrid = false;
            return true;
        }

        if (this.pendingStatusPages == null || snapshotRevision != this.lastAcceptedStatusRevision
            || this.pendingStatusPageCount != pageCount) {
            this.pendingStatusPages = new ObjectArrayList<>(Collections.nCopies(pageCount, null));
            this.pendingStatusPageCount = pageCount;
            this.pendingStatusPagesReceived = 0;
            this.pendingCanExportGrid = canExportGrid;
            if (snapshotRevision > this.lastAcceptedStatusRevision) {
                this.lastAcceptedStatusGeneration = sourceGeneration;
                this.lastAcceptedStatusRevision = snapshotRevision;
            }
        }

        if (this.pendingStatusPages.get(pageIndex) != null) {
            return false;
        }
        this.pendingStatusPages.set(pageIndex, page);
        this.pendingStatusPagesReceived++;
        if (this.pendingStatusPagesReceived == this.pendingStatusPageCount) {
            this.status = NetworkStatus.mergePages(this.pendingStatusPages);
            this.canExportGrid = this.pendingCanExportGrid;
            this.pendingStatusPages = null;
            return true;
        }
        return false;
    }

    public void exportGrid() {
        if (isClientSide()) {
            sendClientAction(ACTION_EXPORT_GRID);
            return;
        }

        resolveCurrentGrid();
        if (!computeCanExportGrid()) {
            return;
        }

        if (this.grid instanceof Grid meGrid) {
            var server = getPlayerInventory().player.getServer();
            if (server != null) {
                String commandLine = GridsCommand.buildExportCommand(meGrid.getSerialNumber());
                if (commandLine.startsWith("/")) {
                    commandLine = commandLine.substring(1);
                }
                server.getCommandManager().executeCommand(getPlayerInventory().player, commandLine);
                setValidContainer(false);
            }
        }
    }

    public boolean canExportGrid() {
        if (isServerSide()) {
            return computeCanExportGrid();
        }
        return this.canExportGrid;
    }

    private boolean computeCanExportGrid() {
        if (isServerSide()) {
            resolveCurrentGrid();
        }
        if (!(this.grid instanceof Grid)) {
            return false;
        }

        var player = getPlayerInventory().player;
        var server = player.getServer();
        if (server != null && !server.getCommandManager().getCommands().containsKey("ae2")) {
            return false;
        }

        return player.canUseCommand(Commands.GRIDS.level,
            Commands.GRIDS.getName());
    }

    private IngredientFlowService getIngredientFlowGridService() {
        if (!(this.grid instanceof Grid)) {
            return null;
        }

        return this.grid.getService(IngredientFlowService.class);
    }

    private void refreshFlowTrackingState() {
        final IngredientFlowService service = this.getIngredientFlowGridService();
        this.flowTrackingMode = service != null && service.isTrackingEnabled();
        this.flowTrackingGloballyEnabled = AEConfig.instance().isIngredientFlowTrackingEnabled();
    }

    public void toggleFlowTrackingMode() {
        if (isClientSide()) {
            sendClientAction(ACTION_TOGGLE_FLOW_TRACKING);
            return;
        }

        resolveCurrentGrid();
        final IngredientFlowService service = this.getIngredientFlowGridService();
        if (service == null || !AEConfig.instance().isIngredientFlowTrackingEnabled()) {
            return;
        }

        service.setTrackingEnabled(!service.isTrackingEnabled());
        this.refreshFlowTrackingState();
        detectAndSendChanges();
    }

    public boolean isFlowTrackingMode() {
        return this.flowTrackingMode;
    }

    public boolean isFlowTrackingGloballyEnabled() {
        return this.flowTrackingGloballyEnabled;
    }
}
