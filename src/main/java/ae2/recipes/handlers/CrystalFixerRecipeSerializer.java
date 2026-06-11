package ae2.recipes.handlers;

import ae2.recipes.AERecipeTypes;
import ae2.recipes.IAERecipeFactory;
import ae2.recipes.serializers.JsonRecipeUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.JsonUtils;
import net.minecraftforge.common.crafting.JsonContext;

public class CrystalFixerRecipeSerializer implements IAERecipeFactory {
    @Override
    public void register(JsonObject json, JsonContext ctx) {
        JsonObject fuel = JsonUtils.getJsonObject(json, "fuel");
        Ingredient fuelIngredient = JsonRecipeUtils.readIngredient(fuel, "ingredient", ctx);
        int fuelAmount = JsonUtils.getInt(fuel, "amount", 1);
        if (fuelAmount <= 0) {
            throw new JsonSyntaxException("fuel amount must be positive");
        }
        int chance = JsonUtils.getInt(json, "chance", CrystalFixerRecipe.FULL_CHANCE);
        if (chance < 0 || chance > CrystalFixerRecipe.FULL_CHANCE) {
            throw new JsonSyntaxException("chance must be between 0 and " + CrystalFixerRecipe.FULL_CHANCE);
        }
        AERecipeTypes.CRYSTAL_FIXER.register(new CrystalFixerRecipe(
            JsonRecipeUtils.readBlock(json, "input"),
            JsonRecipeUtils.readBlock(json, "output"),
            fuelIngredient,
            fuelAmount,
            chance));
    }
}
