package ae2.container.me.common;

import ae2.api.behaviors.ContainerItemStrategies;
import ae2.api.networking.IGrid;
import ae2.api.stacks.GenericStack;
import ae2.container.AEBaseContainer;
import ae2.container.SlotSemantics;
import ae2.container.slot.RequestSlot;
import ae2.core.AELog;
import ae2.core.network.clientbound.RequesterSyncPacket;
import ae2.helpers.InventoryAction;
import ae2.requester.Request;
import ae2.requester.RequestManager;
import ae2.requester.abstraction.RequestTracker;
import ae2.tile.crafting.TileRequester;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractContainerRequester extends AEBaseContainer {
    public static final int REQUEST_SLOT_COUNT = 64;
    public static final int REQUEST_SLOT_X = 27;
    public static final int REQUEST_SLOT_FIRST_Y = 21;
    public static final int REQUEST_SLOT_SPACING = 19;

    private long idSerial = Long.MIN_VALUE;
    private final List<RequestSlot> requestSlots = new ArrayList<>(REQUEST_SLOT_COUNT);

    protected AbstractContainerRequester(InventoryPlayer playerInventory, Object host) {
        super(playerInventory, host);
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

    public final void updateRequestSlot(int row, boolean visible, long requesterId, int requestIndex, boolean locked,
                                        ItemStack stack) {
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

        RequestTracker tracker = getRequestTracker(requesterId);
        if (tracker == null || requestIndex < 0 || requestIndex >= tracker.getServer().size()) {
            slot.clearRequest();
            slot.setSlotEnabled(false);
            slot.setActive(false);
            return;
        }

        slot.setRequester(requesterId, requestIndex);
        slot.setLocked(locked);
        slot.setStack(GenericStack.fromItemStack(stack));
        slot.setSlotEnabled(true);
        slot.setActive(true);
    }

    @Override
    public void doAction(EntityPlayerMP player, InventoryAction action, int slot, long id) {
        RequestTracker inv = getRequestTracker(id);
        if(inv == null) {
            return;
        }

        if (slot < 0 || slot >= inv.getServer().size()) {
            AELog.warn("Requester Screen refers to invalid slot {} of {}", slot, inv.getName().getFormattedText());
            return;
        }

        Request request = inv.getServer().get(slot);
        var carried = getCarried();

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
        RequestTracker inv = getRequestTracker(slot.getRequesterId());
        if (inv == null) {
            return;
        }

        int requestIndex = slot.getRequestIndex();
        if (requestIndex < 0 || requestIndex >= inv.getServer().size()) {
            AELog.warn("Requester Screen refers to invalid slot {} of {}", requestIndex, inv.getName().getFormattedText());
            return;
        }

        inv.getServer().get(requestIndex).updateConfiguredStack(slot.getConfiguredStack());
    }

    public void updateRequesterState(long requesterId, int requestIndex, boolean enabled, boolean forceStart) {
        var requestTracker = getRequestTracker(requesterId);
        if (requestTracker == null) return;
        if (requestIndex < 0 || requestIndex >= requestTracker.getServer().size()) return;
        var request = requestTracker.getServer().get(requestIndex);
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
        request.updateAmount(amount);
        request.updateBatchSize(batchSize);
    }

    protected final RequestTracker createTracker(TileRequester requester) {
        return new RequestTracker(requester, this.idSerial++);
    }

    protected abstract void sendFullUpdate(@Nullable IGrid grid);

    protected abstract void sendPartialUpdate();

    protected final void syncRequestTrackerFull(RequestTracker tracker) {
        RequestManager server = tracker.getServer();
        RequestManager client = tracker.getClient();
        var rows = new Int2ObjectOpenHashMap<NBTTagCompound>(server.size());
        for (int i = 0; i < server.size(); i++) {
            NBTTagCompound serverData = server.get(i).writeToNBT();
            rows.put(i, serverData);
            client.get(i).readFromNBT(serverData);
        }

        sendPacketToClient(RequesterSyncPacket.fullUpdate(tracker.getId(), tracker.getName(), tracker.getSortBy(),
            server.size(), rows));
    }

    protected final void syncRequestTrackerPartial(RequestTracker tracker) {
        RequestManager server = tracker.getServer();
        RequestManager client = tracker.getClient();
        var rows = new Int2ObjectOpenHashMap<NBTTagCompound>();
        for (int i = 0; i < server.size(); i++) {
            Request serverRequest = server.get(i);
            Request clientRequest = client.get(i);
            NBTTagCompound serverData = serverRequest.writeToNBT();
            if (!serverData.equals(clientRequest.writeToNBT())) {
                rows.put(i, serverData);
                clientRequest.readFromNBT(serverData);
            }
        }

        if (!rows.isEmpty()) {
            sendPacketToClient(RequesterSyncPacket.incrementalUpdate(tracker.getId(), rows));
        }
    }

    @Nullable
    protected abstract RequestTracker getRequestTracker(long requesterId);
}
