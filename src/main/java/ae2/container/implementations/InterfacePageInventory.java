package ae2.container.implementations;

import ae2.api.inventories.BaseInternalInventory;
import ae2.api.inventories.InternalInventory;
import net.minecraft.item.ItemStack;

final class InterfacePageInventory extends BaseInternalInventory {
    private final InternalInventory delegate;
    private final int slotsPerPage;
    private int page;

    InterfacePageInventory(InternalInventory delegate, int slotsPerPage) {
        this.delegate = delegate;
        this.slotsPerPage = slotsPerPage;
    }

    void setPage(int page) {
        this.page = page;
    }

    private int translateSlot(int slot) {
        return this.page * this.slotsPerPage + slot;
    }

    private boolean isValidTranslatedSlot(int slot) {
        int translatedSlot = translateSlot(slot);
        return slot >= 0 && slot < this.slotsPerPage && translatedSlot >= 0 && translatedSlot < this.delegate.size();
    }

    @Override
    public int size() {
        return this.slotsPerPage;
    }

    @Override
    public int getSlotLimit(int slot) {
        return this.delegate.getSlotLimit(translateSlot(slot));
    }

    @Override
    public ItemStack getStackInSlot(int slotIndex) {
        if (!isValidTranslatedSlot(slotIndex)) {
            return ItemStack.EMPTY;
        }
        return this.delegate.getStackInSlot(translateSlot(slotIndex));
    }

    @Override
    public void setItemDirect(int slotIndex, ItemStack stack) {
        if (isValidTranslatedSlot(slotIndex)) {
            this.delegate.setItemDirect(translateSlot(slotIndex), stack);
        }
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return isValidTranslatedSlot(slot) && this.delegate.isItemValid(translateSlot(slot), stack);
    }

    @Override
    public InternalInventory getSlotInv(int slotIndex) {
        return this.delegate.getSlotInv(translateSlot(slotIndex));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!isValidTranslatedSlot(slot)) {
            return stack;
        }
        return this.delegate.insertItem(translateSlot(slot), stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!isValidTranslatedSlot(slot)) {
            return ItemStack.EMPTY;
        }
        return this.delegate.extractItem(translateSlot(slot), amount, simulate);
    }
}
