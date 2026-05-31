package ae2.recipes;

import com.google.gson.JsonObject;

import net.minecraftforge.common.crafting.JsonContext;

public interface IAERecipeFactory {
    void register(JsonObject json, JsonContext ctx);
}
