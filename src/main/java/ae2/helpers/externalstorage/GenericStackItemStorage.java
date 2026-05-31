package ae2.helpers.externalstorage;

import ae2.api.behaviors.GenericInternalInventory;
import ae2.api.config.Actionable;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKeyType;
import com.google.common.primitives.Ints;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

public class GenericStackItemStorage implements IItemHandler {
    private final GenericInternalInventory inv;

    public GenericStackItemStorage(GenericInternalInventory inv) {
        this.inv = inv;
    }

    @Override
    public int getSlots() {
        return this.inv.size();
    }

    @Override
    @NotNull
    public ItemStack getStackInSlot(int slot) {
        if (this.inv.getKey(slot) instanceof AEItemKey what) {
            return what.toStack(Ints.saturatedCast(this.inv.getAmount(slot)));
        }
        return ItemStack.EMPTY;
    }

    @Override
    @NotNull
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        var what = AEItemKey.of(stack);
        if (what == null) {
            return stack;
        }

        int inserted = Ints.saturatedCast(this.inv.insert(slot, what, stack.getCount(), Actionable.ofSimulate(simulate)));
        if (inserted <= 0) {
            return stack;
        }
        if (inserted >= stack.getCount()) {
            return ItemStack.EMPTY;
        }

        ItemStack remainder = stack.copy();
        remainder.shrink(inserted);
        return remainder;
    }

    @Override
    @NotNull
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!(this.inv.getKey(slot) instanceof AEItemKey what)) {
            return ItemStack.EMPTY;
        }

        int extracted = Ints.saturatedCast(this.inv.extract(slot, what, amount, Actionable.ofSimulate(simulate)));
        return extracted > 0 ? what.toStack(extracted) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return Ints.saturatedCast(this.inv.getCapacity(AEKeyType.items()));
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        var what = AEItemKey.of(stack);
        return what != null && this.inv.isAllowedIn(slot, what);
    }
}
