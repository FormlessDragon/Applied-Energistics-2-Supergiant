package ae2.container.implementations;

import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.storage.ILinkStatus;
import ae2.container.SlotSemantics;
import ae2.container.guisync.ILinkStatusAwareContainer;
import ae2.container.me.common.AbstractContainerRequester;
import ae2.container.me.common.RequesterServerView;
import ae2.container.slot.RestrictedInputSlot;
import ae2.core.network.clientbound.RequesterSyncPacket;
import ae2.helpers.RequesterTerminalHost;
import ae2.helpers.WirelessTerminalGuiHost;
import ae2.tile.crafting.TileRequester;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.entity.player.InventoryPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

public class ContainerRequesterTerm extends AbstractContainerRequester implements ILinkStatusAwareContainer {

    private final RequesterTerminalHost host;

    private final Long2ObjectOpenHashMap<RequesterServerView> byId = new Long2ObjectOpenHashMap<>();
    private final Map<TileRequester, RequesterServerView> byRequesters = new IdentityHashMap<>();
    private ILinkStatus linkStatus = ILinkStatus.ofDisconnected();

    public ContainerRequesterTerm(InventoryPlayer playerInventory, RequesterTerminalHost host) {
        super(playerInventory, host);
        this.host = host;
        if (host instanceof WirelessTerminalGuiHost<?> wirelessHost) {
            setupUpgrades(wirelessHost.getUpgrades());
            RestrictedInputSlot slot = new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.QE_SINGULARITY,
                wirelessHost.getSingularityStorage(), 0, 0, 0);
            slot.setStackLimit(1);
            this.addSlot(slot, SlotSemantics.WIRELESS_SINGULARITY);
        }
        this.addRequestSlots();
        this.addPlayerInventorySlots(8, 104);
    }

    @Override
    public void broadcastChanges() {
        if (isClientSide()) {
            return;
        }
        super.broadcastChanges();

        IGrid grid = getGrid();
        if (grid == null) {
            if (!this.byId.isEmpty() || !this.byRequesters.isEmpty()) {
                sendFullUpdate(null);
            }
            return;
        }

        VisitorState state = visitRequesters(grid);
        if (state.forceFullUpdate || state.total != byRequesters.size()) {
            sendFullUpdate(grid);
        } else {
            sendPartialUpdate();
        }
    }

    public ILinkStatus getLinkStatus() {
        return this.linkStatus;
    }

    @Override
    public void setLinkStatus(ILinkStatus linkStatus) {
        this.linkStatus = linkStatus;
    }

    @Nullable
    private IGrid getGrid() {
        IGridNode node = this.host.getGridNode();
        if (node != null && node.isActive()) {
            return node.grid();
        }
        return null;
    }

    @Nullable
    @Override
    protected RequesterServerView getRequestTracker(long requesterId) {
        RequesterServerView tracker = this.byId.get(requesterId);
        if (tracker == null) {
            return null;
        }

        IGrid grid = getGrid();
        if (grid != null && grid.getActiveMachines(TileRequester.class).contains(tracker.getRequester())) {
            return tracker;
        }

        this.byId.remove(requesterId);
        this.byRequesters.remove(tracker.getRequester());
        return null;
    }

    private VisitorState visitRequesters(IGrid grid) {
        VisitorState state = new VisitorState();
        for (var requester : grid.getActiveMachines(TileRequester.class)) {
            RequesterServerView requestTracker = byRequesters.get(requester);
            if (requestTracker == null
                || !requestTracker.getName().equals(requester.getRequesterName())
                || requestTracker.getSortBy() != requester.getRequesterSortValue()
                || requestTracker.getServer().size() != requester.getRequests().size()) {
                state.forceFullUpdate = true;
                return state;
            }
            state.total++;
        }
        return state;
    }

    @Override
    protected void sendFullUpdate(@Nullable IGrid grid) {
        this.byId.clear();
        this.byRequesters.clear();

        sendPacketToClient(RequesterSyncPacket.clearAll());

        if (grid == null) {
            return;
        }

        for (var requester : grid.getActiveMachines(TileRequester.class)) {
            this.byRequesters.put(requester, createTracker(requester));
        }

        for (var requestTracker : this.byRequesters.values()) {
            this.byId.put(requestTracker.getId(), requestTracker);
            syncRequestTrackerFull(requestTracker);
        }
    }

    @Override
    protected void sendPartialUpdate() {
        for (RequesterServerView tracker : this.byRequesters.values()) {
            syncRequestTrackerPartial(tracker);
        }
    }

    private static class VisitorState {

        private int total;
        private boolean forceFullUpdate;
    }
}
