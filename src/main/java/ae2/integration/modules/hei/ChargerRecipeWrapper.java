package ae2.integration.modules.hei;

import ae2.recipes.handlers.ChargerRecipe;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;

import java.util.Arrays;
import java.util.Collections;

class ChargerRecipeWrapper implements IRecipeWrapper {
    private final ChargerRecipe recipe;

    ChargerRecipeWrapper(ChargerRecipe recipe) {
        this.recipe = recipe;
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        ingredients.setInputLists(VanillaTypes.ITEM,
            Collections.singletonList(Arrays.asList(this.recipe.getIngredient().getMatchingStacks())));
        ingredients.setOutput(VanillaTypes.ITEM, this.recipe.getResultItem());
    }
}
