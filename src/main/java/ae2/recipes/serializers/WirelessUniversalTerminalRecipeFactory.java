package ae2.recipes.serializers;

import ae2.core.definitions.AEItems;
import ae2.recipes.game.WirelessUniversalTerminalRecipe;
import com.google.gson.JsonObject;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.common.crafting.IRecipeFactory;
import net.minecraftforge.common.crafting.JsonContext;

@SuppressWarnings("unused")
public class WirelessUniversalTerminalRecipeFactory implements IRecipeFactory {
    @Override
    public IRecipe parse(JsonContext context, JsonObject json) {
        return new WirelessUniversalTerminalRecipe(AEItems.WIRELESS_UNIVERSAL_TERMINAL.item());
    }
}
