/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 TeamAppliedEnergistics
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package ae2.api.client;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public final class StorageCellModels {

    private static final ResourceLocation MODEL_CELL_DEFAULT = new ResourceLocation("ae2", "block/drive/drive_cell");

    private static final Reference2ObjectMap<Item, ResourceLocation> registry = new Reference2ObjectOpenHashMap<>();

    private StorageCellModels() {
    }

    public static synchronized void registerModel(Item item, ResourceLocation model) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(model, "model");
        Preconditions.checkArgument(!registry.containsKey(item), "Cannot register an item twice.");

        registry.put(item, model);
    }

    @Nullable
    public static synchronized ResourceLocation model(Item item) {
        Objects.requireNonNull(item, "item");

        return registry.get(item);
    }

    public static synchronized Map<Item, ResourceLocation> models() {
        return new Reference2ObjectOpenHashMap<>(registry);
    }

    public static ResourceLocation getDefaultModel() {
        return MODEL_CELL_DEFAULT;
    }
}
