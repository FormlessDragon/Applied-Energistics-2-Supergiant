package ae2.recipes.game;

import ae2.core.definitions.AEItems;
import ae2.core.definitions.AEParts;
import ae2.core.definitions.ItemDefinition;
import ae2.items.parts.FacadeItem;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.registries.IForgeRegistryEntry;

public final class FacadeRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
    private final ItemDefinition<?> anchor = AEParts.CABLE_ANCHOR;
    private final FacadeItem facade = AEItems.FACADE.item();

    @Override
    public boolean matches(InventoryCrafting inv, World worldIn) {
        return !getOutput(inv, false).isEmpty();
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        return getOutput(inv, true);
    }

    @Override
    public boolean canFit(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return ItemStack.EMPTY;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv) {
        return ForgeHooks.defaultRecipeGetRemainingItems(inv);
    }

    private ItemStack getOutput(IInventory inv, boolean createFacade) {
        if (inv.getSizeInventory() < 9) {
            return ItemStack.EMPTY;
        }

        if (inv.getStackInSlot(0).isEmpty() && inv.getStackInSlot(2).isEmpty() && inv.getStackInSlot(6).isEmpty()
            && inv.getStackInSlot(8).isEmpty()
            && this.anchor.is(inv.getStackInSlot(1))
            && this.anchor.is(inv.getStackInSlot(3))
            && this.anchor.is(inv.getStackInSlot(5))
            && this.anchor.is(inv.getStackInSlot(7))) {
            ItemStack result = this.facade.createFacadeForItem(inv.getStackInSlot(4), !createFacade);
            if (!result.isEmpty() && createFacade) {
                result.setCount(4);
            }
            return result;
        }

        return ItemStack.EMPTY;
    }
}
