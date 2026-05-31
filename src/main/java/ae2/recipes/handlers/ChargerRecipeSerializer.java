package ae2.recipes.handlers;

import ae2.recipes.AERecipeTypes;
import ae2.recipes.IAERecipeFactory;
import ae2.recipes.serializers.JsonRecipeUtils;
import com.google.gson.JsonObject;
import net.minecraftforge.common.crafting.JsonContext;

public class ChargerRecipeSerializer implements IAERecipeFactory {
    @Override
    public void register(JsonObject json, JsonContext ctx) {
        AERecipeTypes.CHARGER.register(new ChargerRecipe(
            JsonRecipeUtils.readIngredient(json, "ingredient", ctx),
            JsonRecipeUtils.readItemStack(json, "result", ctx)));
    }
}
