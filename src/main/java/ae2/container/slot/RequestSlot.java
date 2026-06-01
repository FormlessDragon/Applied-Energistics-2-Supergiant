package ae2.container.slot;

import ae2.api.stacks.GenericStack;
import ae2.requester.Request;
import ae2.util.ConfigInventory;
import net.minecraft.item.ItemStack;

public final class RequestSlot extends FakeSlot {

    private long requesterId;
    private int requestIndex = -1;
    private boolean locked;

    public RequestSlot(int x, int y) {
        super(ConfigInventory.configStacks(1).allowOverstacking(true).build().createGuiWrapper(), 0, x, y);
        setHideAmount(true);
    }

    public void setRequester(long requesterId, int requestIndex) {
        this.requesterId = requesterId;
        this.requestIndex = requestIndex;
    }

    public boolean setRequest(Request request) {
        long oldRequesterId = this.requesterId;
        int oldRequestIndex = this.requestIndex;
        boolean oldLocked = this.locked;
        ItemStack oldStack = getConfiguredItemStack().copy();

        if (request.getRequesterReference() != null) {
            setRequester(request.getRequesterReference().getRequesterId(), request.getIndex());
        }
        setStack(request.getConfiguredStack());
        setLocked(request.getClientStatus().locksRequest());
        return oldRequesterId != this.requesterId
            || oldRequestIndex != this.requestIndex
            || oldLocked != this.locked
            || !ItemStack.areItemStacksEqual(oldStack, getConfiguredItemStack());
    }

    public boolean clearRequest() {
        boolean changed = this.requestIndex >= 0 || this.requesterId != 0 || this.locked || !getRawStack().isEmpty();
        this.requesterId = 0;
        this.requestIndex = -1;
        this.locked = false;
        this.getInventory().setItemDirect(getSlotIndex(), ItemStack.EMPTY);
        return changed;
    }

    public long getRequesterId() {
        return this.requesterId;
    }

    public int getRequestIndex() {
        return this.requestIndex;
    }

    public void setStack(GenericStack stack) {
        this.getInventory().setItemDirect(getSlotIndex(), GenericStack.wrapInItemStack(stack));
    }

    public GenericStack getConfiguredStack() {
        return GenericStack.fromItemStack(getConfiguredItemStack());
    }

    private ItemStack getConfiguredItemStack() {
        return super.getRawStack();
    }

    @Override
    public void increase(ItemStack stack) {
    }

    @Override
    public void decrease(ItemStack stack) {
    }

    @Override
    public int getSlotStackLimit() {
        return 0;
    }

    @Override
    public boolean getHasStack() {
        return !this.locked && super.getHasStack();
    }

    public boolean isLocked() {
        return this.locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    @Override
    public boolean canSetFilterTo(ItemStack stack) {
        return this.requestIndex >= 0 && !this.locked && getInventory().isItemValid(getSlotIndex(), stack);
    }
}
