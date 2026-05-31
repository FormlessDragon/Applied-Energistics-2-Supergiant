package ae2.util;

import com.google.common.base.Preconditions;

import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.util.NonNullList;

public final class CraftingRecipeUtil {
    private CraftingRecipeUtil() {
    }

    public static NonNullList<Ingredient> ensure3by3CraftingMatrix(IRecipe recipe) {
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        NonNullList<Ingredient> expandedIngredients = NonNullList.withSize(9, Ingredient.EMPTY);

        Preconditions.checkArgument(ingredients.size() <= 9);

        if (recipe instanceof ShapedRecipes shapedRecipe) {
            int width = shapedRecipe.recipeWidth;
            int height = shapedRecipe.recipeHeight;
            Preconditions.checkArgument(width <= 3 && height <= 3);

            for (int h = 0; h < height; h++) {
                for (int w = 0; w < width; w++) {
                    int source = w + h * width;
                    int target = w + h * 3;
                    expandedIngredients.set(target, ingredients.get(source));
                }
            }
        } else {
            for (int i = 0; i < ingredients.size(); i++) {
                expandedIngredients.set(i, ingredients.get(i));
            }
        }

        return expandedIngredients;
    }
}
