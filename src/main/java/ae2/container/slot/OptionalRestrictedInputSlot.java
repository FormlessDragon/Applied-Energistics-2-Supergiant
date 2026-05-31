package ae2.container.slot;

import ae2.api.inventories.InternalInventory;

public class OptionalRestrictedInputSlot extends RestrictedInputSlot {
    private final int groupNum;
    private final IOptionalSlotHost host;

    public OptionalRestrictedInputSlot(PlacableItemType type, InternalInventory inventory, IOptionalSlotHost host,
                                       int slotIndex, int x, int y, int groupNum) {
        super(type, inventory, slotIndex, x, y);
        this.groupNum = groupNum;
        this.host = host;
    }

    @Override
    public boolean isSlotEnabled() {
        return this.host != null && this.host.isSlotEnabled(this.groupNum);
    }
}
