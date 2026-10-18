package ae2.container.me.common;

import ae2.api.behaviors.ContainerItemStrategies;
import ae2.api.networking.IGrid;
import ae2.api.stacks.GenericStack;
import ae2.container.AEBaseContainer;
import ae2.container.SlotSemantics;
import ae2.container.slot.RequestSlot;
import ae2.core.network.clientbound.RequesterSyncPacket;
import ae2.helpers.InventoryAction;
import ae2.tile.crafting.TileRequester;
import ae2.tile.crafting.requester.Request;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public abstract class AbstractContainerRequester extends AEBaseContainer {
    public static final int REQUEST_SLOT_COUNT = 64;
    public static final int REQUEST_SLOT_X = 27;
    public static final int REQUEST_SLOT_FIRST_Y = 21;
    public static final int REQUEST_SLOT_SPACING = 19;
    private final List<RequestSlot> requestSlots = new ArrayList<>(REQUEST_SLOT_COUNT);
    private static final AtomicLong NEXT_SYNC_GENERATION = new AtomicLong(1);
    private long syncGeneration = NEXT_SYNC_GENERATION.getAndIncrement();
    private Consumer<RequesterSyncPacket> requesterPacketSender = createRequesterPacketSender();
    private long idSerial = Long.MIN_VALUE;

    protected AbstractContainerRequester(InventoryPlayer playerInventory, Object host) {
        super(playerInventory, host);
    }

    protected final long getSyncGeneration() {
        return this.syncGeneration;
    }

    protected final void advanceSyncSource() {
        this.syncGeneration = NEXT_SYNC_GENERATION.getAndIncrement();
        this.requesterPacketSender = createRequesterPacketSender();
    }

    private Consumer<RequesterSyncPacket> createRequesterPacketSender() {
        long generation = this.syncGeneration;
        return packet -> sendPacketToClient(packet.withIdentity(this.windowId, generation));
    }

    protected final void sendRequesterPacket(RequesterSyncPacket packet) {
        this.requesterPacketSender.accept(packet);
    }

    protected final void addRequestSlots() {
        if (!this.requestSlots.isEmpty()) {
            throw new IllegalStateException("Request slots have already been added");
        }

        for (int row = 0; row < REQUEST_SLOT_COUNT; row++) {
            RequestSlot slot = new RequestSlot(REQUEST_SLOT_X, REQUEST_SLOT_FIRST_Y + row * REQUEST_SLOT_SPACING);
            slot.setSlotEnabled(false);
            slot.setActive(false);
            this.requestSlots.add(slot);
            addSlot(slot, SlotSemantics.REQUEST);
        }
    }

    public final List<RequestSlot> getRequestSlots() {
        return Collections.unmodifiableList(this.requestSlots);
    }

    public final void updateRequestSlot(int row, boolean visible, long requesterId, int requestIndex) {
        if (row < 0 || row >= this.requestSlots.size()) {
            return;
        }

        RequestSlot slot = this.requestSlots.get(row);
        if (!visible) {
            slot.clearRequest();
            slot.setSlotEnabled(false);
            slot.setActive(false);
            return;
        }

        RequesterServerView tracker = getRequestTracker(requesterId);
        if (tracker == null || requestIndex < 0 || requestIndex >= tracker.getServer().size()) {
            slot.clearRequest();
            slot.setSlotEnabled(false);
            slot.setActive(false);
            return;
        }

        slot.setRequester(requesterId, requestIndex);
        Request request = tracker.getServer().get(requestIndex);
        slot.setLocked(request.getClientStatus().locksRequest());
        slot.setStack(request.getConfiguredStack());
        slot.setSlotEnabled(true);
        slot.setActive(true);
    }

    @Override
    public void doAction(EntityPlayerMP player, InventoryAction action, int slot, long id) {
        RequesterServerView inv = getRequestTracker(id);
        if (inv == null) {
            return;
        }

        if (slot < 0 || slot >= inv.getServer().size()) {
            return;
        }

        Request request = inv.getServer().get(slot);
        var carried = getCarried();
        if (request.getClientStatus().locksRequest()) {
            return;
        }

        switch (action) {
            case PICKUP_OR_SET_DOWN -> request.updateConfiguredStack(GenericStack.fromItemStack(carried));
            case SPLIT_OR_PLACE_SINGLE -> {
                if (carried.isEmpty()) {
                    request.updateConfiguredStack(null);
                } else {
                    ItemStack copy = carried.copy();
                    copy.setCount(1);
                    request.updateConfiguredStack(GenericStack.fromItemStack(copy));
                }
            }
            case SHIFT_CLICK -> request.updateConfiguredStack(null);
            case EMPTY_ITEM -> {
                var emptyingAction = ContainerItemStrategies.getEmptyingAction(carried);
                if (emptyingAction != null) {
                    request.updateConfiguredStack(new GenericStack(emptyingAction.what(), emptyingAction.maxAmount()));
                }
            }
            case CREATIVE_DUPLICATE -> {
                if (player.capabilities.isCreativeMode && carried.isEmpty() && request.getConfiguredStack() != null) {
                    setCarried(GenericStack.wrapInItemStack(request.getConfiguredStack()));
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void onSlotChange(Slot slot) {
        if (isServerSide() && slot instanceof RequestSlot requestSlot) {
            updateRequestFromSlot(requestSlot);
        }
    }

    private void updateRequestFromSlot(RequestSlot slot) {
        RequesterServerView inv = getRequestTracker(slot.getRequesterId());
        if (inv == null) {
            return;
        }

        int requestIndex = slot.getRequestIndex();
        if (requestIndex < 0 || requestIndex >= inv.getServer().size()) {
            return;
        }

        Request request = inv.getServer().get(requestIndex);
        if (request.getClientStatus().locksRequest()) {
            return;
        }

        request.updateConfiguredStack(slot.getConfiguredStack());
    }

    public void updateRequesterState(long requesterId, int requestIndex, boolean enabled, boolean forceStart) {
        var requestTracker = getRequestTracker(requesterId);
        if (requestTracker == null) {
            return;
        }
        if (requestIndex < 0 || requestIndex >= requestTracker.getServer().size()) {
            return;
        }
        var request = requestTracker.getServer().get(requestIndex);
        if (request.getClientStatus().locksRequest() && (enabled || forceStart != request.isForceStart())) {
            return;
        }
        request.updateState(enabled, forceStart);
    }

    public void updateRequesterNumbers(long requesterId, int requestIndex, long amount, long batchSize) {
        var requestTracker = getRequestTracker(requesterId);
        if (requestTracker == null) {
            return;
        }
        if (requestIndex < 0 || requestIndex >= requestTracker.getServer().size()) {
            return;
        }
        var request = requestTracker.getServer().get(requestIndex);
        if (request.getClientStatus().locksRequest()) {
            return;
        }
        request.updateAmount(amount);
        if (amount <= 0) {
            return;
        }
        request.updateBatchSize(batchSize);
    }

    protected final RequesterServerView createTracker(TileRequester requester) {
        return new RequesterServerView(requester, this.idSerial++);
    }

    protected abstract void sendFullUpdate(@Nullable IGrid grid);

    protected abstract void sendPartialUpdate();

    protected final void syncRequestTrackerFull(RequesterServerView tracker) {
        tracker.synchronize(true, this.requesterPacketSender);
    }

    protected final void syncRequestTrackerPartial(RequesterServerView tracker) {
        tracker.synchronize(false, this.requesterPacketSender);
    }

    @Nullable
    protected abstract RequesterServerView getRequestTracker(long requesterId);
}
