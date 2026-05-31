package ae2.integration.modules.hei;

import ae2.recipes.game.StorageCellUpgradeRecipe;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.item.ItemStack;

import java.util.List;

class StorageCellUpgradeRecipeWrapper implements IRecipeWrapper {
    private final StorageCellUpgradeRecipe recipe;

    StorageCellUpgradeRecipeWrapper(StorageCellUpgradeRecipe recipe) {
        this.recipe = recipe;
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        ingredients.setInputs(VanillaTypes.ITEM, List.of(
            new ItemStack(this.recipe.getInputCell()),
            new ItemStack(this.recipe.getInputComponent())));
        ingredients.setOutput(VanillaTypes.ITEM, new ItemStack(this.recipe.getResultCell()));
    }
}
