package ae2.container.slot;

import ae2.api.inventories.InternalInventory;
import net.minecraft.item.ItemStack;

public class OutputSlot extends AppEngSlot {
    public OutputSlot(InternalInventory inventory, int slotIndex, int x, int y) {
        super(inventory, slotIndex, x, y);
    }

    public OutputSlot(InternalInventory inventory, int slotIndex, int x, int y, SlotBackgroundIcon backgroundIcon) {
        this(inventory, slotIndex, x, y);
        this.setBackgroundIcon(backgroundIcon);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return false;
    }
}
