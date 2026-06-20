package ae2.recipes.game;

import ae2.core.definitions.AEBlocks;
import ae2.recipes.AERecipeTypes;
import ae2.recipes.IAERecipeFactory;
import ae2.recipes.serializers.JsonRecipeUtils;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraftforge.common.crafting.JsonContext;

public class CraftingUnitTransformRecipeSerializer implements IAERecipeFactory {
    @Override
    public void register(JsonObject json, JsonContext ctx) {
        Block baseBlock = json.has("base_block")
            ? JsonRecipeUtils.readBlock(json, "base_block")
            : AEBlocks.CRAFTING_UNIT.block();
        AERecipeTypes.CRAFTING_UNIT_TRANSFORM.register(new CraftingUnitTransformRecipe(
            baseBlock,
            JsonRecipeUtils.readBlock(json, "upgraded_block"),
            JsonRecipeUtils.readItem(json, "upgrade_item")));
    }
}
