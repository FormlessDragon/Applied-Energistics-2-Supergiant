package ae2.integration.modules.hei;

import ae2.recipes.handlers.InscriberRecipe;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.item.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class InscriberRecipeWrapper implements IRecipeWrapper {
    private final InscriberRecipe recipe;

    InscriberRecipeWrapper(InscriberRecipe recipe) {
        this.recipe = recipe;
    }

    private static List<ItemStack> getStacks(net.minecraft.item.crafting.Ingredient ingredient) {
        if (ingredient == null || ingredient == net.minecraft.item.crafting.Ingredient.EMPTY) {
            return Collections.singletonList(ItemStack.EMPTY);
        }
        ItemStack[] stacks = ingredient.getMatchingStacks();
        return stacks.length == 0 ? Collections.singletonList(ItemStack.EMPTY) : Arrays.asList(stacks);
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        ingredients.setInputLists(VanillaTypes.ITEM, Arrays.asList(
            getStacks(this.recipe.getTopOptional()),
            getStacks(this.recipe.getMiddleInput()),
            getStacks(this.recipe.getBottomOptional())));
        ingredients.setOutput(VanillaTypes.ITEM, this.recipe.getResultItem());
    }
}
