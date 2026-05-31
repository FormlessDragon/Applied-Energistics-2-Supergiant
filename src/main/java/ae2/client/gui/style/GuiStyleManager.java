/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.client.gui.style;

import ae2.core.AppEng;
import com.google.common.base.Preconditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("deprecation")
public final class GuiStyleManager {

    public static final String PROP_INCLUDES = "includes";
    private static final Map<String, GuiStyle> styleCache = new Object2ObjectOpenHashMap<>();
    private static IResourceManager resourceManager;

    private GuiStyleManager() {
    }

    private static String getBasePath(String path) {
        int lastSep = path.lastIndexOf('/');
        if (lastSep == -1) {
            return "";
        } else {
            return path.substring(0, lastSep + 1);
        }
    }

    public static GuiStyle loadStyleDoc(String path) {
        GuiStyle style;

        try {
            style = loadStyleDocInternal(path);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Failed to find Screen JSON file: " + path + ": " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read Screen JSON file: " + path, e);
        }

        style.validate();
        return style;
    }

    private static JsonObject loadMergedJsonTree(String path, Set<String> loadedFiles, Set<String> resourcePacks)
        throws IOException {
        Preconditions.checkArgument(path.startsWith("/"), "Path needs to start with slash");

        if (path.contains("..")) {
            path = URI.create(path).normalize().toString();
        }

        if (!loadedFiles.add(path)) {
            throw new IllegalStateException("Recursive style includes: " + loadedFiles);
        }

        if (resourceManager == null) {
            throw new IllegalStateException("ResourceManager was not set. Was initialize called?");
        }

        String basePath = getBasePath(path);

        JsonObject document;
        var resourceId = AppEng.makeId(path.substring(1));
        var resource = resourceManager.getResource(resourceId);
        resourcePacks.add(resource.getResourcePackName());
        try (var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            document = GuiStyle.GSON.fromJson(reader, JsonObject.class);
        }

        if (document.has(PROP_INCLUDES)) {
            String[] includes = GuiStyle.GSON.fromJson(document.get(PROP_INCLUDES), String[].class);

            List<JsonObject> layers = new ObjectArrayList<>();
            for (String include : includes) {
                layers.add(loadMergedJsonTree(basePath + include, loadedFiles, resourcePacks));
            }
            layers.add(document);
            document = combineLayers(layers);
        }

        return document;
    }

    private static JsonObject combineLayers(List<JsonObject> layers) {
        JsonObject result = new JsonObject();

        for (JsonObject layer : layers) {
            for (Map.Entry<String, JsonElement> entry : layer.entrySet()) {
                result.add(entry.getKey(), entry.getValue());
            }
        }

        mergeObjectKeys("slots", layers, result);
        mergeObjectKeys("text", layers, result);
        mergeObjectKeys("palette", layers, result);
        mergeObjectKeys("images", layers, result);
        mergeObjectKeys("terminalStyle", layers, result);
        mergeObjectKeys("widgets", layers, result);
        mergeObjectKeys("tooltips", layers, result);

        return result;
    }

    private static void mergeObjectKeys(String propertyName, List<JsonObject> layers, JsonObject target)
        throws JsonParseException {
        JsonObject mergedObject = null;
        for (JsonObject layer : layers) {
            JsonElement layerEl = layer.get(propertyName);
            if (layerEl != null) {
                if (!layerEl.isJsonObject()) {
                    throw new JsonParseException("Expected " + propertyName + " to be an object, but was: " + layerEl);
                }
                JsonObject layerObj = layerEl.getAsJsonObject();

                if (mergedObject == null) {
                    mergedObject = new JsonObject();
                }
                for (Map.Entry<String, JsonElement> entry : layerObj.entrySet()) {
                    mergedObject.add(entry.getKey(), entry.getValue());
                }
            }
        }

        if (mergedObject != null) {
            target.add(propertyName, mergedObject);
        }
    }

    private static GuiStyle loadStyleDocInternal(String path) throws IOException {
        GuiStyle style = styleCache.get(path);
        if (style != null) {
            return style;
        }

        Set<String> resourcePacks = new ObjectOpenHashSet<>();
        try {
            JsonObject document = loadMergedJsonTree(path, new ObjectOpenHashSet<>(), resourcePacks);
            style = GuiStyle.GSON.fromJson(document, GuiStyle.class);
            style.validate();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new JsonParseException("Failed to load style from " + path + " (packs: " + resourcePacks + ")", e);
        }

        styleCache.put(path, style);
        return style;
    }

    public static void initialize(IResourceManager resourceManager) {
        if (resourceManager instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) resourceManager).registerReloadListener(new ReloadListener());
        }
        setResourceManager(resourceManager);
    }

    private static void setResourceManager(IResourceManager resourceManager) {
        GuiStyleManager.resourceManager = resourceManager;
        GuiStyleManager.styleCache.clear();
    }

    private static class ReloadListener implements IResourceManagerReloadListener {
        @Override
        public void onResourceManagerReload(IResourceManager resourceManager) {
            setResourceManager(resourceManager);
        }
    }
}
