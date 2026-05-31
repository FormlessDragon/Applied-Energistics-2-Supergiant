package ae2.container.slot;

import ae2.api.inventories.InternalInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

public class InaccessibleSlot extends AppEngSlot {
    private ItemStack displayStack = ItemStack.EMPTY;

    public InaccessibleSlot(InternalInventory inventory, int slotIndex, int x, int y) {
        super(inventory, slotIndex, x, y);
    }

    public InaccessibleSlot(InternalInventory inventory, int slotIndex) {
        super(inventory, slotIndex);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canTakeStack(EntityPlayer player) {
        return false;
    }

    @Override
    public void onSlotChanged() {
        super.onSlotChanged();
        this.displayStack = ItemStack.EMPTY;
    }

    @Override
    public ItemStack getDisplayStack() {
        if (this.displayStack.isEmpty()) {
            var stack = super.getDisplayStack();
            if (!stack.isEmpty()) {
                this.displayStack = stack.copy();
            }
        }
        return this.displayStack;
    }
}
