package ae2.recipes.game;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistryEntry;

public class StorageCellUpgradeRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
    private final Item inputCell;
    private final Item inputComponent;
    private final Item resultCell;
    private final Item resultComponent;

    public StorageCellUpgradeRecipe(Item inputCell, Item inputComponent, Item resultCell, Item resultComponent) {
        this.inputCell = inputCell;
        this.inputComponent = inputComponent;
        this.resultCell = resultCell;
        this.resultComponent = resultComponent;
    }

    public Item getInputCell() {
        return this.inputCell;
    }

    public Item getInputComponent() {
        return this.inputComponent;
    }

    public Item getResultCell() {
        return this.resultCell;
    }

    public Item getResultComponent() {
        return this.resultComponent;
    }

    @Override
    public boolean matches(InventoryCrafting inv, World worldIn) {
        int cellsFound = 0;
        int componentsFound = 0;

        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() == this.inputCell) {
                cellsFound += stack.getCount();
            } else if (stack.getItem() == this.inputComponent) {
                componentsFound += stack.getCount();
            } else {
                return false;
            }
            if (cellsFound > 1 || componentsFound > 1) {
                return false;
            }
        }

        return cellsFound == 1 && componentsFound == 1;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        ItemStack cell = ItemStack.EMPTY;
        int componentsFound = 0;

        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() == this.inputCell) {
                if (stack.getCount() > 1 || !cell.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                cell = stack;
            } else if (stack.getItem() == this.inputComponent) {
                if (++componentsFound > 1) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        }

        if (cell.isEmpty() || componentsFound == 0) {
            return ItemStack.EMPTY;
        }

        ItemStack upgraded = new ItemStack(this.resultCell);
        NBTTagCompound tag = cell.getTagCompound();
        if (tag != null) {
            upgraded.setTagCompound(tag.copy());
        }
        return upgraded;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(inv.getSizeInventory(), ItemStack.EMPTY);
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.getItem() == this.inputCell) {
                remaining.set(i, new ItemStack(this.getResultComponent()));
            } else if (stack.getItem().hasContainerItem(stack)) {
                remaining.set(i, stack.getItem().getContainerItem(stack));
            }
        }
        return remaining;
    }

    @Override
    public boolean canFit(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return new ItemStack(this.resultCell);
    }
}
