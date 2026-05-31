package ae2.recipes.handlers;

import ae2.recipes.AERecipeTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import org.jspecify.annotations.Nullable;

public class ChargerRecipe {
    private final Ingredient ingredient;
    private final ItemStack result;

    public ChargerRecipe(Ingredient ingredient, ItemStack result) {
        this.ingredient = ingredient;
        this.result = result.copy();
    }

    public static @Nullable ChargerRecipe findRecipe(ItemStack input) {
        for (var recipe : AERecipeTypes.CHARGER.getRecipes()) {
            if (recipe.matches(input)) {
                return recipe;
            }
        }
        return null;
    }

    public Ingredient getIngredient() {
        return this.ingredient;
    }

    public ItemStack getResultItem() {
        return this.result.copy();
    }

    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && this.ingredient.apply(stack);
    }
}
