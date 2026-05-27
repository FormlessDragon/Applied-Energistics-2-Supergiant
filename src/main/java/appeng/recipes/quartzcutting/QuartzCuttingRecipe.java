package appeng.recipes.quartzcutting;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistryEntry;

import java.util.List;

public class QuartzCuttingRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
    private final ItemStack result;
    private final NonNullList<Ingredient> ingredients;

    public QuartzCuttingRecipe(ItemStack result, NonNullList<Ingredient> ingredients) {
        this.result = result.copy();
        this.ingredients = ingredients;
    }

    @Override
    public boolean matches(InventoryCrafting inv, World worldIn) {
        List<ItemStack> inputs = new ObjectArrayList<>();
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                inputs.add(stack);
            }
        }

        if (inputs.size() != this.ingredients.size()) {
            return false;
        }

        boolean[] matched = new boolean[inputs.size()];
        for (var ingredient : this.ingredients) {
            boolean found = false;
            for (int i = 0; i < inputs.size(); i++) {
                if (!matched[i] && ingredient.apply(inputs.get(i))) {
                    matched[i] = true;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        return this.result.copy();
    }

    @Override
    public boolean canFit(int width, int height) {
        return width * height >= this.ingredients.size();
    }

    @Override
    public ItemStack getRecipeOutput() {
        return this.result.copy();
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(inv.getSizeInventory(), ItemStack.EMPTY);
        boolean damagedKnife = false;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }

            ResourceLocation id = stack.getItem().getRegistryName();
            if (!damagedKnife && id != null && id.getPath().endsWith("cutting_knife")) {
                damagedKnife = true;
                ItemStack damaged = stack.copy();
                if (damaged.isItemStackDamageable()) {
                    int nextDamage = damaged.getItemDamage() + 1;
                    if (nextDamage >= damaged.getMaxDamage()) {
                        damaged = ItemStack.EMPTY;
                    } else {
                        damaged.setItemDamage(nextDamage);
                    }
                }
                remaining.set(i, damaged);
            } else if (stack.getItem().hasContainerItem(stack)) {
                remaining.set(i, stack.getItem().getContainerItem(stack));
            }
        }
        return remaining;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return this.ingredients;
    }

    @Override
    public boolean isDynamic() {
        return true;
    }
}
