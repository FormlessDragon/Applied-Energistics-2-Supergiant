package ae2.recipes.serializers;

import ae2.recipes.game.RemoveItemUpgradeRecipe;
import com.google.gson.JsonObject;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.common.crafting.IRecipeFactory;
import net.minecraftforge.common.crafting.JsonContext;

public class RemoveItemUpgradeRecipeFactory implements IRecipeFactory {
    @Override
    public IRecipe parse(JsonContext context, JsonObject json) {
        return new RemoveItemUpgradeRecipe();
    }
}
