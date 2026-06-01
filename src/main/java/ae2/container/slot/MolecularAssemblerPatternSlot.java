package ae2.container.slot;

import ae2.api.crafting.IAssemblerPattern;
import ae2.api.inventories.InternalInventory;
import ae2.api.stacks.AEItemKey;
import ae2.client.Point;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class MolecularAssemblerPatternSlot extends AppEngSlot implements IOptionalSlot {
    private final Host host;

    public MolecularAssemblerPatternSlot(Host host, InternalInventory inventory, int slotIndex) {
        super(inventory, slotIndex);
        this.host = host;
    }

    public MolecularAssemblerPatternSlot(Host host, InternalInventory inventory, int slotIndex, int x, int y) {
        super(inventory, slotIndex, x, y);
        this.host = host;
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        var pattern = this.host.getCurrentPattern();
        return super.isItemValid(stack)
            && pattern != null
            && pattern.isItemValid(this.getSlotIndex(), AEItemKey.of(stack), this.host.getWorld());
    }

    @Override
    protected boolean getCurrentValidationState() {
        ItemStack stack = getStack();
        return stack.isEmpty() || isItemValid(stack);
    }

    @Override
    public boolean isRenderDisabled() {
        return true;
    }

    @Override
    public boolean isSlotEnabled() {
        int slotIndex = getSlotIndex();
        if (slotIndex >= 0 && slotIndex < getInventory().size() && !getInventory().getStackInSlot(slotIndex).isEmpty()) {
            return true;
        }

        var pattern = this.host.getCurrentPattern();
        return slotIndex >= 0 && slotIndex < 9 && pattern != null && pattern.isSlotEnabled(slotIndex);
    }

    @Override
    public Point getBackgroundPos() {
        return new Point(this.xPos - 1, this.yPos - 1);
    }

    public interface Host {
        IAssemblerPattern getCurrentPattern();

        World getWorld();
    }
}
