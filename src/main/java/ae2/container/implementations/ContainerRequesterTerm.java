package ae2.container.implementations;

import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.storage.ILinkStatus;
import ae2.container.SlotSemantics;
import ae2.container.guisync.ILinkStatusAwareContainer;
import ae2.container.me.common.AbstractContainerRequester;
import ae2.container.slot.RestrictedInputSlot;
import ae2.core.network.clientbound.RequesterSyncPacket;
import ae2.helpers.WirelessTerminalGuiHost;
import ae2.api.storage.IRequesterTermContainerHost;
import ae2.requester.abstraction.RequestTracker;
import ae2.tile.crafting.TileRequester;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.entity.player.InventoryPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

public class ContainerRequesterTerm extends AbstractContainerRequester implements ILinkStatusAwareContainer {

    private final IRequesterTermContainerHost host;

    private final Long2ObjectOpenHashMap<RequestTracker> byId = new Long2ObjectOpenHashMap<>();
    private final Map<TileRequester, RequestTracker> byRequesters = new IdentityHashMap<>();
    private ILinkStatus linkStatus = ILinkStatus.ofDisconnected();

    public ContainerRequesterTerm(InventoryPlayer playerInventory, IRequesterTermContainerHost host) {
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
    protected RequestTracker getRequestTracker(long requesterId) {
        return this.byId.get(requesterId);
    }

    private VisitorState visitRequesters(IGrid grid) {
        VisitorState state = new VisitorState();
        for(var requester : grid.getActiveMachines(TileRequester.class)) {
            RequestTracker requestTracker = byRequesters.get(requester);
            if(requestTracker == null || !requestTracker.getName().equals(requester.getRequesterName())) {
                state.forceFullUpdate = true;
                return state;
            }
            state.total++;
        }
        return state;
    }

    @Override
    protected void sendFullUpdate(@Nullable IGrid grid) {
        assert grid != null;
        this.byId.clear();
        this.byRequesters.clear();

        sendPacketToClient(RequesterSyncPacket.clearAll());

        for(var requester : grid.getActiveMachines(TileRequester.class)) {
            this.byRequesters.put(requester, createTracker(requester));
        }

        for(var requestTracker : this.byRequesters.values()) {
            this.byId.put(requestTracker.getId(), requestTracker);
            syncRequestTrackerFull(requestTracker);
        }
    }

    @Override
    protected void sendPartialUpdate() {
        for (RequestTracker tracker : this.byRequesters.values()) {
            syncRequestTrackerPartial(tracker);
        }
    }

    private static class VisitorState {

        private int total;
        private boolean forceFullUpdate;
    }
}
