package ae2.recipes.serializers;

import ae2.recipes.quartzcutting.QuartzCuttingRecipe;
import com.google.gson.JsonObject;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.crafting.IRecipeFactory;
import net.minecraftforge.common.crafting.JsonContext;

import java.util.List;

public class QuartzCuttingRecipeFactory implements IRecipeFactory {
    @Override
    public IRecipe parse(JsonContext context, JsonObject json) {
        List<Ingredient> ingredients = JsonRecipeUtils.readIngredients(json, "ingredients", context);
        return new QuartzCuttingRecipe(
            JsonRecipeUtils.readItemStack(json, "result", context),
            NonNullList.from(Ingredient.EMPTY, ingredients.toArray(new Ingredient[0])));
    }
}
