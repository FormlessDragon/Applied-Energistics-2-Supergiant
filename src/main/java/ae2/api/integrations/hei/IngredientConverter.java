package ae2.api.integrations.hei;

import ae2.api.stacks.GenericStack;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.IIngredientType;
import org.jetbrains.annotations.Nullable;

public interface IngredientConverter<T> {
    IIngredientType<T> getIngredientType();

    @Nullable
    T getIngredientFromStack(GenericStack stack);

    @Nullable
    GenericStack getStackFromIngredient(T ingredient);

    default IGuiIngredientGroup<T> getIngredientGroup(IRecipeLayout recipeLayout) {
        return recipeLayout.getIngredientsGroup(getIngredientType());
    }
}
