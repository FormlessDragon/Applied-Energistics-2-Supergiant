package ae2.util.inv;

import ae2.api.storage.cells.StorageCell;
import net.minecraft.item.ItemStack;

public class AppEngCellInventory extends AppEngInternalInventory {
    private final StorageCell[] handlers;

    public AppEngCellInventory(InternalInventoryHost host, int slots) {
        super(host, slots, 1);
        this.handlers = new StorageCell[slots];
    }

    public void setHandler(int slot, StorageCell handler) {
        persist(slot);
        this.handlers[slot] = handler;
    }

    @Override
    public void setItemDirect(int slot, ItemStack stack) {
        persist(slot);
        super.setItemDirect(slot, stack);
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        persist(slot);
        return super.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        persist(slot);
        return super.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        persist(slot);
        return super.extractItem(slot, amount, simulate);
    }

    public void persist() {
        for (int i = 0; i < this.size(); i++) {
            persist(i);
        }
    }

    private void persist(int slot) {
        var handler = this.handlers[slot];
        if (handler != null) {
            handler.persist();
        }
    }
}
