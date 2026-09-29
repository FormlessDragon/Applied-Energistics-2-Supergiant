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
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.Nullable;

public class ContainerNetworkStatus extends AEBaseContainer {
    private static final String ACTION_EXPORT_GRID = "export_grid";
    private static final String ACTION_TOGGLE_FLOW_TRACKING = "toggle_flow_tracking";

    @Nullable
    private IGrid grid;
    private int delay = 40;
    private NetworkStatus status = new NetworkStatus();
    private boolean canExportGrid;
    @GuiSync(35)
    public boolean flowTrackingMode;
    @GuiSync(36)
    public boolean flowTrackingGloballyEnabled;

    public ContainerNetworkStatus(InventoryPlayer ip, IInWorldGridNodeHost host) {
        super(ip, host);

        buildForGridHost(host);
    }

    private void buildForGridHost(@Nullable IInWorldGridNodeHost gridHost) {
        if (gridHost != null) {
            for (var side : EnumFacing.VALUES) {
                findNode(gridHost, side);
            }
        }

        if (this.grid == null && isServerSide()) {
            this.setValidContainer(false);
        }

        registerClientAction(ACTION_EXPORT_GRID, this::exportGrid);
        registerClientAction(ACTION_TOGGLE_FLOW_TRACKING, this::toggleFlowTrackingMode);
    }

    private void findNode(IInWorldGridNodeHost host, EnumFacing side) {
        if (this.grid == null) {
            IGridNode node = host.getGridNode(side);
            if (node != null) {
                this.grid = node.grid();
            }
        }
    }

    @Override
    public void broadcastChanges() {
        this.delay++;
        if (isServerSide() && this.delay > 15 && this.grid != null) {
            this.delay = 0;
            this.status = NetworkStatus.fromGrid(this.grid);
            this.canExportGrid = computeCanExportGrid();
            this.refreshFlowTrackingState();
            sendPacketToClient(new NetworkStatusPacket(this.status, this.canExportGrid));
        }
        super.broadcastChanges();
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

    public void exportGrid() {
        if (isClientSide()) {
            sendClientAction(ACTION_EXPORT_GRID);
            return;
        }

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
