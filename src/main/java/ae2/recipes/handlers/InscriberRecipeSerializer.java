package ae2.recipes.handlers;

import ae2.recipes.AERecipeTypes;
import ae2.recipes.IAERecipeFactory;
import ae2.recipes.serializers.JsonRecipeUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.JsonUtils;
import net.minecraftforge.common.crafting.JsonContext;

public class InscriberRecipeSerializer implements IAERecipeFactory {
    private static InscriberProcessType readMode(String mode) {
        return switch (mode) {
            case "inscribe" -> InscriberProcessType.INSCRIBE;
            case "press" -> InscriberProcessType.PRESS;
            default -> throw new JsonSyntaxException("Unknown inscriber mode: " + mode);
        };
    }

    @Override
    public void register(JsonObject json, JsonContext ctx) {
        JsonObject ingredients = JsonUtils.getJsonObject(json, "ingredients");
        Ingredient top = ingredients.has("top") ? JsonRecipeUtils.readIngredient(ingredients, "top", ctx) : Ingredient.EMPTY;
        Ingredient bottom = ingredients.has("bottom") ? JsonRecipeUtils.readIngredient(ingredients, "bottom", ctx)
            : Ingredient.EMPTY;
        Ingredient middle = JsonRecipeUtils.readIngredient(ingredients, "middle", ctx);
        String mode = JsonUtils.getString(json, "mode", "inscribe");

        AERecipeTypes.INSCRIBER.register(new InscriberRecipe(
            middle,
            JsonRecipeUtils.readItemStack(json, "result", ctx),
            top,
            bottom,
            readMode(mode)));
    }
}
