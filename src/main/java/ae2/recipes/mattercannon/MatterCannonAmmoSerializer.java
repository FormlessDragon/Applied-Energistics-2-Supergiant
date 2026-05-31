package ae2.recipes.mattercannon;

import ae2.recipes.AERecipeTypes;
import ae2.recipes.IAERecipeFactory;
import ae2.recipes.serializers.JsonRecipeUtils;
import com.google.gson.JsonObject;
import net.minecraft.util.JsonUtils;
import net.minecraftforge.common.crafting.JsonContext;

public class MatterCannonAmmoSerializer implements IAERecipeFactory {
    @Override
    public void register(JsonObject json, JsonContext ctx) {
        AERecipeTypes.MATTER_CANNON_AMMO.register(new MatterCannonAmmo(
            JsonRecipeUtils.readIngredient(json, "ammo", ctx),
            JsonUtils.getFloat(json, "weight")));
    }
}
