package ae2.recipes.serializers;

import ae2.recipes.game.StorageCellUpgradeRecipe;
import com.google.gson.JsonObject;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.common.crafting.IRecipeFactory;
import net.minecraftforge.common.crafting.JsonContext;

@SuppressWarnings("unused")
public class StorageCellUpgradeRecipeFactory implements IRecipeFactory {
    @Override
    public IRecipe parse(JsonContext context, JsonObject json) {
        return new StorageCellUpgradeRecipe(
            JsonRecipeUtils.readItem(json, "input_cell"),
            JsonRecipeUtils.readItem(json, "input_component"),
            JsonRecipeUtils.readItem(json, "result_cell"),
            JsonRecipeUtils.readItem(json, "result_component"));
    }
}
