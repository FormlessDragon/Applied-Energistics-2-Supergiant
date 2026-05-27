package appeng.integration.modules.hei;

import appeng.recipes.quartzcutting.QuartzCuttingRecipe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@ParametersAreNonnullByDefault
class QuartzCuttingRecipeWrapper implements IRecipeWrapper {
    private final QuartzCuttingRecipe recipe;

    QuartzCuttingRecipeWrapper(QuartzCuttingRecipe recipe) {
        this.recipe = recipe;
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        List<List<ItemStack>> inputs = new ObjectArrayList<>();
        for (Ingredient ingredient : this.recipe.getIngredients()) {
            ItemStack[] stacks = ingredient.getMatchingStacks();
            inputs.add(stacks.length == 0 ? Collections.singletonList(ItemStack.EMPTY) : Arrays.asList(stacks));
        }
        ingredients.setInputLists(VanillaTypes.ITEM, inputs);
        ingredients.setOutput(VanillaTypes.ITEM, this.recipe.getRecipeOutput());
    }
}
