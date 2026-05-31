package ae2.recipes.transform;

import ae2.recipes.AERecipeTypes;
import ae2.recipes.IAERecipeFactory;
import ae2.recipes.serializers.JsonRecipeUtils;
import com.google.gson.JsonObject;
import net.minecraftforge.common.crafting.JsonContext;

import java.util.List;

public class TransformRecipeSerializer implements IAERecipeFactory {
    @Override
    public void register(JsonObject json, JsonContext ctx) {
        List<net.minecraft.item.crafting.Ingredient> ingredients = JsonRecipeUtils.readIngredients(json, "ingredients",
            ctx);
        JsonObject circumstance = json.has("circumstance") ? json.getAsJsonObject("circumstance") : null;
        AERecipeTypes.TRANSFORM.register(new TransformRecipe(
            ingredients,
            JsonRecipeUtils.readItemStack(json, "result", ctx),
            TransformCircumstance.fromJson(circumstance)));
    }
}
