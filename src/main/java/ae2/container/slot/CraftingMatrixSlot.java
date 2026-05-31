package ae2.container.slot;

import ae2.api.inventories.InternalInventory;
import ae2.container.AEBaseContainer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

public class CraftingMatrixSlot extends AppEngSlot {
    private final AEBaseContainer container;
    private final IInventory wrappedInventory;

    public CraftingMatrixSlot(AEBaseContainer container, InternalInventory inventory, int slotIndex, int x, int y) {
        super(inventory, slotIndex, x, y);
        this.container = container;
        this.wrappedInventory = inventory.toContainer();
    }

    @Override
    public void clearStack() {
        super.clearStack();
        this.container.onCraftMatrixChanged(this.wrappedInventory);
    }

    @Override
    public void putStack(ItemStack stack) {
        super.putStack(stack);
        this.container.onCraftMatrixChanged(this.wrappedInventory);
    }

    @Override
    public ItemStack decrStackSize(int amount) {
        ItemStack result = super.decrStackSize(amount);
        this.container.onCraftMatrixChanged(this.wrappedInventory);
        return result;
    }
}
