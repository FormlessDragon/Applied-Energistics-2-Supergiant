package ae2.recipes.game;

import ae2.recipes.IAERecipeFactory;
import ae2.recipes.serializers.JsonRecipeUtils;
import com.google.gson.JsonObject;
import net.minecraftforge.common.crafting.JsonContext;

public class StorageCellDisassemblyRecipeSerializer implements IAERecipeFactory {
    @Override
    public void register(JsonObject json, JsonContext ctx) {
        StorageCellDisassemblyRecipe.register(new StorageCellDisassemblyRecipe(
            JsonRecipeUtils.readItem(json, "cell"),
            JsonRecipeUtils.readItemStacks(json, "cell_disassembly_items", ctx)));
    }
}
