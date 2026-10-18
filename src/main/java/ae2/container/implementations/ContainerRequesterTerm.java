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
    @Nullable
    private IGrid syncGrid;
    @Nullable
    private IGrid directoryGrid;
    private long directoryRevision = -1;
    private boolean syncSourceInitialized;
    private boolean sourceResetPending;
    private boolean synchronizingRequesters;

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
        if (this.synchronizingRequesters || isClientSide()) {
            return;
        }
        this.synchronizingRequesters = true;
        try {
            super.broadcastChanges();
            synchronizeRequesters();
        } finally {
            this.synchronizingRequesters = false;
        }
    }

    private void synchronizeRequesters() {
        IGrid grid = getGrid();
        if (!this.syncSourceInitialized || this.syncGrid != grid) {
            this.syncSourceInitialized = true;
            this.syncGrid = grid;
            this.sourceResetPending = true;
            advanceSyncSource();
        }
        if (this.sourceResetPending) {
            sendFullUpdate(grid);
            return;
        }
        if (grid == null) {
            if (!this.byId.isEmpty() || !this.byRequesters.isEmpty()) {
                sendFullUpdate(null);
            }
            return;
        }

        updateRequesterDirectory(grid);
        sendPartialUpdate();
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
        if (grid != null && grid == this.syncGrid
            && grid.getActiveMachines(TileRequester.class).contains(tracker.getRequester())
            && tracker.getServer() == tracker.getRequester().getRequests()) {
            return tracker;
        }

        return null;
    }

    private void updateRequesterDirectory(IGrid grid) {
        long revision = grid.getActiveMachineSetRevision();
        if (revision >= 0 && this.directoryGrid == grid && this.directoryRevision == revision) {
            return;
        }
        var active = grid.getActiveMachines(TileRequester.class);
        for (var requester : active) {
            if (!this.byRequesters.containsKey(requester)) {
                var requestTracker = createTracker(requester);
                this.byRequesters.put(requester, requestTracker);
                this.byId.put(requestTracker.getId(), requestTracker);
            }
        }

        // Every active requester is now represented; extra entries can only be removals.
        if (this.byRequesters.size() != active.size()) {
            var previous = this.byRequesters.entrySet().iterator();
            while (previous.hasNext()) {
                var entry = previous.next();
                if (!active.contains(entry.getKey())) {
                    var tracker = entry.getValue();
                    sendRequesterPacket(RequesterSyncPacket.removeRequester(tracker.getId()));
                    this.byId.remove(tracker.getId());
                    previous.remove();
                }
            }
        }
        // Capture before enumeration/callbacks: a change during reconciliation must be seen next tick.
        this.directoryGrid = grid;
        this.directoryRevision = revision;
    }

    @Override
    protected void sendFullUpdate(@Nullable IGrid grid) {
        this.sourceResetPending = true;
        this.directoryGrid = null;
        this.directoryRevision = -1;
        long revision = grid == null ? -1 : grid.getActiveMachineSetRevision();
        this.byId.clear();
        this.byRequesters.clear();

        sendRequesterPacket(RequesterSyncPacket.clearAll());

        if (grid == null) {
            this.sourceResetPending = false;
            return;
        }

        for (var requester : grid.getActiveMachines(TileRequester.class)) {
            this.byRequesters.put(requester, createTracker(requester));
        }

        for (var requestTracker : this.byRequesters.values()) {
            this.byId.put(requestTracker.getId(), requestTracker);
            syncRequestTrackerFull(requestTracker);
        }
        this.directoryGrid = grid;
        this.directoryRevision = revision;
        this.sourceResetPending = false;
    }

    @Override
    protected void sendPartialUpdate() {
        for (var entry : this.byRequesters.entrySet()) {
            RequesterServerView tracker = entry.getValue();
            if (!tracker.refreshMetadata()) {
                var replacement = createTracker(entry.getKey());
                sendRequesterPacket(RequesterSyncPacket.removeRequester(tracker.getId()));
                this.byId.remove(tracker.getId());
                entry.setValue(replacement);
                this.byId.put(replacement.getId(), replacement);
                tracker = replacement;
            }
            syncRequestTrackerPartial(tracker);
        }
    }

}
