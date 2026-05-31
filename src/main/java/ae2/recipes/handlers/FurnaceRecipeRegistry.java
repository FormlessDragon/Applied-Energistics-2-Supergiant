package ae2.recipes.handlers;

import ae2.core.AppEng;
import ae2.recipes.serializers.JsonRecipeUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.JsonUtils;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.JsonContext;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FurnaceRecipeRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private FurnaceRecipeRegistry() {
    }

    public static void register() {
        ModContainer mod = Loader.instance().getIndexedModList().get(AppEng.MOD_ID);
        JsonContext ctx = new JsonContext(AppEng.MOD_ID);
        CraftingHelper.findFiles(mod, "assets/" + AppEng.MOD_ID + "/furnace_recipes", ignored -> true,
            (root, file) -> process(ctx, root, file), true, true);
    }

    private static boolean process(JsonContext ctx, Path root, Path file) {
        String relative = root.relativize(file).toString();
        String extension = FilenameUtils.getExtension(file.toString());
        if (relative.startsWith("_") || !"json".equals(extension)) {
            return true;
        }

        BufferedReader reader = null;
        try {
            reader = Files.newBufferedReader(file);
            JsonObject json = JsonUtils.fromJson(GSON, reader, JsonObject.class);
            if (json == null || !isFurnaceRecipe(json)) {
                return true;
            }

            registerRecipe(json, ctx);
        } catch (JsonParseException e) {
            FMLLog.log.error("Parsing error loading AE2 furnace recipe {}", relative, e);
            return false;
        } catch (IOException e) {
            FMLLog.log.error("Couldn't read AE2 furnace recipe {}", relative, e);
            return false;
        } finally {
            IOUtils.closeQuietly(reader);
        }

        return true;
    }

    private static boolean isFurnaceRecipe(JsonObject json) {
        if (!json.has("type")) {
            return false;
        }

        String type = JsonUtils.getString(json, "type");
        return "minecraft:smelting".equals(type) || "minecraft:blasting".equals(type);
    }

    private static void registerRecipe(JsonObject json, JsonContext ctx) {
        Ingredient ingredient = JsonRecipeUtils.readIngredient(json, "ingredient", ctx);
        ItemStack output = JsonRecipeUtils.readItemStack(json, "result", ctx);
        float experience = JsonUtils.getFloat(json, "experience", 0.0F);

        for (ItemStack input : ingredient.getMatchingStacks()) {
            if (!input.isEmpty()) {
                GameRegistry.addSmelting(input, output.copy(), experience);
            }
        }
    }
}
