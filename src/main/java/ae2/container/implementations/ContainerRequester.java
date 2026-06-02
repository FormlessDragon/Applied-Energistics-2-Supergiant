package ae2.container.implementations;

import ae2.api.networking.IGrid;
import ae2.container.me.common.AbstractContainerRequester;
import ae2.container.me.common.RequesterServerView;
import ae2.core.network.clientbound.RequesterSyncPacket;
import ae2.tile.crafting.TileRequester;
import net.minecraft.entity.player.InventoryPlayer;
import org.jetbrains.annotations.Nullable;

public class ContainerRequester extends AbstractContainerRequester {
    private static final long REQUESTER_ID = 0;

    private final TileRequester requester;
    private final RequesterServerView requestTracker;
    private boolean sentFullUpdate;

    public ContainerRequester(InventoryPlayer playerInventory, TileRequester requester) {
        super(playerInventory, requester);
        this.requester = requester;
        this.requestTracker = new RequesterServerView(requester, REQUESTER_ID);

        this.addRequestSlots();
        this.addPlayerInventorySlots(8, 131);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (isServerSide()) {
            if (!this.sentFullUpdate) {
                sendFullUpdate(null);
            } else {
                sendPartialUpdate();
            }
        }
    }

    public TileRequester getRequester() {
        return this.requester;
    }

    public long getRequesterId() {
        return this.requestTracker.getId();
    }

    @Nullable
    @Override
    protected RequesterServerView getRequestTracker(long requesterId) {
        return requesterId == this.requestTracker.getId() ? this.requestTracker : null;
    }

    @Override
    protected void sendFullUpdate(@Nullable IGrid grid) {
        sendPacketToClient(RequesterSyncPacket.clearAll());
        syncRequestTrackerFull(this.requestTracker);
        this.sentFullUpdate = true;
    }

    @Override
    protected void sendPartialUpdate() {
        syncRequestTrackerPartial(this.requestTracker);
    }
}
