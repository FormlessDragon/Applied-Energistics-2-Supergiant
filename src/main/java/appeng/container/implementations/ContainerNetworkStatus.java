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

package appeng.container.implementations;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.container.AEBaseContainer;
import appeng.container.networking.INetworkStatusContainer;
import appeng.container.networking.NetworkStatus;
import appeng.core.network.clientbound.NetworkStatusPacket;
import appeng.items.contents.NetworkToolGuiHost;
import appeng.server.subcommands.GridsCommand;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.Nullable;

public class ContainerNetworkStatus extends AEBaseContainer implements INetworkStatusContainer {
    private static final String ACTION_EXPORT_GRID = "export_grid";

    @Nullable
    private IGrid grid;
    private int delay = 40;
    private NetworkStatus status = new NetworkStatus();
    private boolean canExportGrid;

    public ContainerNetworkStatus(InventoryPlayer ip, NetworkToolGuiHost<?> host) {
        super(ip, host);

        buildForGridHost(host.getGridHost());
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

        if (this.grid instanceof appeng.me.Grid meGrid) {
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
        if (!(this.grid instanceof appeng.me.Grid)) {
            return false;
        }

        var player = getPlayerInventory().player;
        var server = player.getServer();
        if (server != null && !server.getCommandManager().getCommands().containsKey("ae2")) {
            return false;
        }

        return player.canUseCommand(appeng.server.Commands.GRIDS.level,
            appeng.server.Commands.GRIDS.getName());
    }
}
