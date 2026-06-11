package ae2.integration.modules.hei;

import ae2.recipes.handlers.ChargerRecipe;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;

import java.util.Arrays;
import java.util.Collections;

class ChargerRecipeWrapper implements IRecipeWrapper {
    private final ChargerRecipe recipe;

    ChargerRecipeWrapper(ChargerRecipe recipe) {
        this.recipe = recipe;
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        Ingredient ingredient = this.recipe.getIngredient();
        ItemStack[] matchingStacks = ingredient == null ? null : ingredient.getMatchingStacks();
        ingredients.setInputLists(VanillaTypes.ITEM,
            Collections.singletonList(matchingStacks == null || matchingStacks.length == 0
                ? Collections.singletonList(ItemStack.EMPTY)
                : Arrays.asList(matchingStacks)));
        ingredients.setOutput(VanillaTypes.ITEM, this.recipe.getResultItem());
    }
}
