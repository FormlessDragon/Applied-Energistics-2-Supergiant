package ae2.container.slot;

import ae2.api.inventories.InternalInventory;
import ae2.client.Point;
import net.minecraft.item.ItemStack;

public class OptionalFakeSlot extends FakeSlot implements IOptionalSlot {
    private final int groupNum;
    private final IOptionalSlotHost host;
    private boolean renderDisabled = true;

    public OptionalFakeSlot(InternalInventory inventory, IOptionalSlotHost host, int slotIndex, int x, int y,
                            int groupNum) {
        super(inventory, slotIndex, x, y);
        this.groupNum = groupNum;
        this.host = host;
    }

    @Override
    public ItemStack getStack() {
        if (!this.isSlotEnabled()) {
            this.clearStack();
        }
        return super.getStack();
    }

    @Override
    public boolean isSlotEnabled() {
        return this.host != null && this.host.isSlotEnabled(this.groupNum);
    }

    @Override
    public boolean isRenderDisabled() {
        return this.renderDisabled;
    }

    public void setRenderDisabled(boolean renderDisabled) {
        this.renderDisabled = renderDisabled;
    }

    @Override
    public Point getBackgroundPos() {
        return new Point(this.xPos - 1, this.yPos - 1);
    }
}
