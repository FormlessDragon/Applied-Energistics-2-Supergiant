package ae2.recipes.mattercannon;

import ae2.core.AELog;
import ae2.recipes.AERecipeTypes;
import ae2.recipes.IAERecipeFactory;
import ae2.recipes.serializers.JsonRecipeUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.JsonUtils;
import net.minecraftforge.common.crafting.JsonContext;

public class MatterCannonAmmoSerializer implements IAERecipeFactory {
    @Override
    public void register(JsonObject json, JsonContext ctx) {
        float weight = JsonUtils.getFloat(json, "weight");
        if (!Float.isFinite(weight) || weight < 0) {
            throw new JsonSyntaxException("weight must be finite and non-negative");
        }

        Ingredient ammo = JsonRecipeUtils.readIngredient(json, "ammo", ctx);
        if (ammo == Ingredient.EMPTY || ammo.getMatchingStacks().length == 0) {
            AELog.warn("Skipping matter cannon ammo recipe with no matching ammo ingredient: %s", json);
            return;
        }

        AERecipeTypes.MATTER_CANNON_AMMO.register(new MatterCannonAmmo(ammo, weight));
    }
}
