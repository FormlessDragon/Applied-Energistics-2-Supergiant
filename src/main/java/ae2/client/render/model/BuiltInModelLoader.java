package ae2.client.render.model;

import ae2.core.AppEng;
import com.google.common.collect.ImmutableMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;

import javax.annotation.Nonnull;
import java.util.Map;

@SuppressWarnings("deprecation")
public class BuiltInModelLoader implements ICustomModelLoader {

    private final Map<String, IModel> builtInModels;

    public BuiltInModelLoader(Map<String, IModel> builtInModels) {
        this.builtInModels = ImmutableMap.copyOf(builtInModels);
    }

    private static String normalizePath(String path) {
        if (path.startsWith("block/builtin/")) {
            return "block/" + path.substring("block/builtin/".length());
        }
        if (path.startsWith("item/builtin/")) {
            return "item/" + path.substring("item/builtin/".length());
        }
        if (path.startsWith("part/builtin/")) {
            return "part/" + path.substring("part/builtin/".length());
        }
        if (path.startsWith("crafting/")) {
            return "block/" + path;
        }
        if (path.startsWith("builtin/")) {
            return path.substring("builtin/".length());
        }
        if (path.startsWith("models/")) {
            return path.substring("models/".length());
        }
        return path;
    }

    @Override
    public boolean accepts(ResourceLocation modelLocation) {
        if (!AppEng.MOD_ID.equals(modelLocation.getNamespace())) {
            return false;
        }

        String path = modelLocation.getPath();
        String normalizedPath = normalizePath(path);
        return this.builtInModels.containsKey(path) || this.builtInModels.containsKey(normalizedPath);
    }

    @Override
    public IModel loadModel(ResourceLocation modelLocation) {
        String path = modelLocation.getPath();
        String normalizedPath = normalizePath(path);
        IModel model = this.builtInModels.get(path);
        return model != null ? model : this.builtInModels.get(normalizedPath);
    }

    @Override
    public void onResourceManagerReload(@Nonnull IResourceManager resourceManager) {
        for (IModel model : this.builtInModels.values()) {
            if (model instanceof IResourceManagerReloadListener) {
                ((IResourceManagerReloadListener) model).onResourceManagerReload(resourceManager);
            }
        }
    }
}
