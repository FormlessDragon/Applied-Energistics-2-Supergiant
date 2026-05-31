package ae2.recipes;

import ae2.core.AppEng;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.util.JsonUtils;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.JsonContext;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AERecipeLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final ModContainer mod;
    private final JsonContext ctx;

    public AERecipeLoader() {
        this.mod = Loader.instance().getIndexedModList().get(AppEng.MOD_ID);
        this.ctx = new JsonContext(AppEng.MOD_ID);
    }

    public static void loadRecipes() {
        new AERecipeLoader().load();
    }

    public void load() {
        AERecipeTypes.clear();
        CraftingHelper.findFiles(this.mod, "assets/" + AppEng.MOD_ID + "/recipes", path -> true, this::process, true,
            true);
    }

    private boolean process(Path root, Path file) {
        String relative = root.relativize(file).toString();
        String extension = FilenameUtils.getExtension(file.toString());
        if (relative.startsWith("_") || !("json".equals(extension) || "recipe".equals(extension))) {
            return true;
        }

        BufferedReader reader = null;
        try {
            reader = Files.newBufferedReader(file);
            JsonObject json = JsonUtils.fromJson(GSON, reader, JsonObject.class);
            if (json == null || !json.has("type")) {
                return true;
            }

            AERecipeSerializers.register(json, this.ctx);
        } catch (JsonParseException e) {
            FMLLog.log.error("Parsing error loading AE2 custom recipe {}", relative, e);
            return false;
        } catch (IOException e) {
            FMLLog.log.error("Couldn't read AE2 custom recipe {}", relative, e);
            return false;
        } finally {
            IOUtils.closeQuietly(reader);
        }

        return true;
    }
}
