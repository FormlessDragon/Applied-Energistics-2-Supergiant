package ae2.container.slot;

import ae2.client.gui.me.common.ClientReadOnlySlot;
import net.minecraft.item.ItemStack;

public class PatternTermSlot extends ClientReadOnlySlot {
    private ItemStack resultItem = ItemStack.EMPTY;
    private boolean active = true;

    public PatternTermSlot() {
    }

    public PatternTermSlot(int x, int y) {
        super(x, y);
    }

    @Override
    public ItemStack getStack() {
        return this.resultItem;
    }

    public void setResultItem(ItemStack resultItem) {
        this.resultItem = resultItem;
    }

    @Override
    public boolean isEnabled() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
