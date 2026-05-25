package appeng.container.networking;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.container.AEBaseContainer;
import appeng.core.network.clientbound.NetworkStatusPacket;
import appeng.server.subcommands.GridsCommand;
import appeng.tile.networking.TileController;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.Nullable;

public class ContainerControllerStatus extends AEBaseContainer implements INetworkStatusContainer {
    private static final String ACTION_EXPORT_GRID = "export_grid";

    @Nullable
    private IGrid grid;
    private int delay = 40;
    private NetworkStatus status = new NetworkStatus();
    private boolean canExportGrid;

    public ContainerControllerStatus( InventoryPlayer playerInventory, TileController host) {
        super(playerInventory, host);
        buildForGridHost(host);
    }

    private void buildForGridHost(@Nullable IInWorldGridNodeHost gridHost) {
        if (gridHost != null) {
            for (EnumFacing side : EnumFacing.VALUES) {
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

    @Override
    public NetworkStatus getStatus() {
        return this.status;
    }

    @Override
    public void setStatus(NetworkStatus status) {
        this.status = status;
    }

    @Override
    public void setCanExportGrid(boolean canExportGrid) {
        this.canExportGrid = canExportGrid;
    }

    @Override
    public void exportGrid() {
        if (isClientSide()) {
            sendClientAction(ACTION_EXPORT_GRID);
            return;
        }

        if (!computeCanExportGrid()) {
            return;
        }

        if (this.grid instanceof appeng.me.Grid grid) {
            net.minecraft.server.MinecraftServer server = getPlayerInventory().player.getServer();
            if (server != null) {
                String commandLine = GridsCommand.buildExportCommand(grid.getSerialNumber());
                if (commandLine.startsWith("/")) {
                    commandLine = commandLine.substring(1);
                }
                server.getCommandManager().executeCommand(getPlayerInventory().player, commandLine);
                setValidContainer(false);
            }
        }
    }

    @Override
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

        net.minecraft.entity.player.EntityPlayer player = getPlayerInventory().player;
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server != null && !server.getCommandManager().getCommands().containsKey("ae2")) {
            return false;
        }

        return player.canUseCommand(appeng.server.Commands.GRIDS.level,
            appeng.server.Commands.GRIDS.getName());
    }
}
