package ae2.api.crafting;

import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.KeyCounter;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

public interface IAssemblerPattern extends IPatternDetails {
    ItemStack assemble(InventoryCrafting input, World level);

    default NonNullList<ItemStack> getRemainingItems(InventoryCrafting input) {
        return NonNullList.withSize(input.getSizeInventory(), ItemStack.EMPTY);
    }

    boolean isItemValid(int slot, AEItemKey key, World level);

    boolean isSlotEnabled(int slot);

    void fillCraftingGrid(KeyCounter[] table, CraftingGridAccessor gridAccessor);

    boolean canSubstitute();

    boolean canSubstituteFluids();

    @Override
    default boolean supportsPushInputsToExternalInventory() {
        return false;
    }

    @FunctionalInterface
    interface CraftingGridAccessor {
        void set(int slot, ItemStack stack);
    }
}
