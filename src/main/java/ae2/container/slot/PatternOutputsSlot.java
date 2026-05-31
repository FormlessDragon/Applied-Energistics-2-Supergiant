package ae2.container.slot;

import ae2.api.inventories.InternalInventory;

public class PatternOutputsSlot extends OptionalFakeSlot {
    public PatternOutputsSlot(InternalInventory inventory, IOptionalSlotHost host, int slotIndex, int groupNum) {
        this(inventory, host, slotIndex, 0, 0, groupNum);
    }

    public PatternOutputsSlot(InternalInventory inventory, IOptionalSlotHost host, int slotIndex, int x, int y,
                              int groupNum) {
        super(inventory, host, slotIndex, x, y, groupNum);
    }

    @Override
    public boolean isSlotEnabled() {
        return true;
    }
}
