package ae2.recipes.handlers;

import ae2.recipes.AERecipeTypes;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;

public record CrystalFixerRecipe(Block input, Block output, Ingredient fuel, int fuelAmount, int chance) {
    public static final int FULL_CHANCE = 10000;

    public static CrystalFixerRecipe findRecipe(Block input, ItemStack fuel) {
        for (var recipe : AERecipeTypes.CRYSTAL_FIXER.getRecipes()) {
            if (recipe.matches(input, fuel)) {
                return recipe;
            }
        }
        return null;
    }

    public boolean matches(Block input, ItemStack fuelStack) {
        return this.input == input && !fuelStack.isEmpty() && fuelStack.getCount() >= this.fuelAmount
            && this.fuel.apply(fuelStack);
    }

    public double getChancePercent() {
        return (double) this.chance / FULL_CHANCE;
    }
}
