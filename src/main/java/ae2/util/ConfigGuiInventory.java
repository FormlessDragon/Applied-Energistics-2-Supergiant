package ae2.util;

import ae2.api.behaviors.GenericStackDisplayInventory;
import ae2.api.inventories.InternalInventory;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.GenericStack;
import ae2.helpers.externalstorage.GenericStackInv;
import com.google.common.primitives.Ints;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Wraps this configuration inventory as an {@link ItemStack} based inventory for use in a GUI container. It will automatically
 * convert appropriately from {@link ItemStack}s set by the player to the internal key-based representation with the
 * help of a matching {@link AEKeyType}.
 */
public class ConfigGuiInventory implements InternalInventory, GenericStackDisplayInventory {
    private final GenericStackInv inv;

    public ConfigGuiInventory(GenericStackInv inv) {
        this.inv = Objects.requireNonNull(inv);
    }

    public GenericStackInv getDelegate() {
        return inv;
    }

    @Override
    public int size() {
        return inv.size();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }

        var genericStack = convertToSuitableStack(stack);
        return genericStack != null && inv.isAllowedIn(slot, genericStack.what());
    }

    @Override
    public int getSlotLimit(int slot) {
        return (int) Math.min(Integer.MAX_VALUE, inv.getCapacity(AEKeyType.items()));
    }

    @Override
    public ItemStack getStackInSlot(int slotIndex) {
        GenericStack stack = inv.getStack(slotIndex);

        if (stack != null && stack.what() instanceof AEItemKey itemKey) {
            if (inv.getMode() == ConfigInventory.Mode.CONFIG_TYPES) {
                return itemKey.toStack();
            } else if (stack.amount() > 0 && stack.amount() <= itemKey.getMaxStackSize()) {
                return itemKey.toStack((int) stack.amount());
            }
        }

        return GenericStack.wrapInItemStack(stack);
    }

    @Override
    public boolean hasGenericDisplayStack(int slot) {
        return inv.getKey(slot) != null;
    }

    @Override
    @Nullable
    public AEKey getDisplayKey(int slot) {
        return inv.getKey(slot);
    }

    @Override
    public long getDisplayAmount(int slot) {
        return inv.getAmount(slot);
    }

    @Override
    public void setItemDirect(int slotIndex, @NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            inv.setStack(slotIndex, null);
        } else {
            var converted = convertToSuitableStack(stack);
            if (converted != null) {
                inv.setStack(slotIndex, converted);
            }
        }
    }

    @Nullable
    public GenericStack convertToSuitableStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        var unwrapped = GenericStack.unwrapItemStack(stack);
        if (unwrapped != null) {
            if (unwrapped.what() instanceof AEItemKey itemKey) {
                stack = itemKey.toStack(Math.max(1, Ints.saturatedCast(unwrapped.amount())));
            } else if (inv.isSupportedType(unwrapped.what())) {
                return unwrapped;
            } else {
                return null;
            }
        }

        if (inv.isSupportedType(AEKeyType.items())) {
            var what = AEItemKey.of(stack);
            if (what != null) {
                return new GenericStack(what, stack.getCount());
            }
        }

        return null;
    }
}
