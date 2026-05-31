package ae2.recipes.game;

import ae2.recipes.AERecipeTypes;
import ae2.recipes.IAERecipeFactory;
import ae2.recipes.serializers.JsonRecipeUtils;
import com.google.gson.JsonObject;
import net.minecraftforge.common.crafting.JsonContext;

public class CraftingUnitTransformRecipeSerializer implements IAERecipeFactory {
    @Override
    public void register(JsonObject json, JsonContext ctx) {
        AERecipeTypes.CRAFTING_UNIT_TRANSFORM.register(new CraftingUnitTransformRecipe(
            JsonRecipeUtils.readBlock(json, "upgraded_block"),
            JsonRecipeUtils.readItem(json, "upgrade_item")));
    }
}
