package ae2.container.slot;

import ae2.api.config.Actionable;
import ae2.api.inventories.InternalInventory;
import ae2.api.stacks.GenericStack;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.InventoryActionPacket;
import ae2.helpers.InventoryAction;
import ae2.util.ConfigGuiInventory;
import ae2.util.ConfigInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

public class FakeSlot extends AppEngSlot {
    public FakeSlot(InternalInventory inventory, int slotIndex, int x, int y) {
        super(inventory, slotIndex, x, y);
    }

    @Override
    public ItemStack onTake(EntityPlayer player, ItemStack stack) {
        return stack;
    }

    @Override
    public ItemStack decrStackSize(int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return false;
    }

    @Override
    public void putStack(ItemStack stack) {
        if (!canSetFilterTo(stack)) {
            return;
        }

        if (stack.isEmpty()) {
            super.putStack(ItemStack.EMPTY);
            return;
        }

        ItemStack copy = stack.copy();
        super.putStack(copy);
    }

    @Override
    public boolean canTakeStack(EntityPlayer player) {
        return false;
    }

    public boolean canSetFilterTo(ItemStack stack) {
        return getSlotIndex() < getInventory().size() && getInventory().isItemValid(getSlotIndex(), stack);
    }

    public void increase(ItemStack stack) {
        if (getInventory() instanceof ConfigGuiInventory configInv) {
            var realInv = configInv.getDelegate();
            if (realInv.getMode() == ConfigInventory.Mode.CONFIG_STACKS) {
                var newFilter = configInv.convertToSuitableStack(stack);
                if (newFilter != null && newFilter.what().equals(realInv.getKey(getSlotIndex()))) {
                    realInv.insert(getSlotIndex(), newFilter.what(), newFilter.amount(), Actionable.MODULATE);
                    return;
                }
            }
        }

        putStack(stack);
    }

    public void decrease(ItemStack stack) {
        if (getInventory() instanceof ConfigGuiInventory configInv) {
            var realInv = configInv.getDelegate();
            if (realInv.getMode() == ConfigInventory.Mode.CONFIG_STACKS) {
                var newFilter = configInv.convertToSuitableStack(stack);
                if (newFilter != null) {
                    realInv.extract(getSlotIndex(), newFilter.what(), newFilter.amount(), Actionable.MODULATE);
                    return;
                }
            }
        }

        var current = getStack();
        if (stack.isEmpty()) {
            current = current.copy();
            current.shrink(1);
            putStack(current);
        } else if (ItemStack.areItemsEqual(current, stack) && ItemStack.areItemStackTagsEqual(current, stack)) {
            current = current.copy();
            current.grow(1);
            putStack(current);
        } else {
            var copy = stack.copy();
            copy.setCount(1);
            putStack(copy);
        }
    }

    public void setFilterTo(ItemStack stack) {
        InitNetwork.sendToServer(new InventoryActionPacket(this.getContainer().windowId, InventoryAction.SET_FILTER,
            this.slotNumber, stack));
    }

    public void setGenericFilter(GenericStack stack) {
        putStack(GenericStack.wrapInItemStack(stack));
    }
}
