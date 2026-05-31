/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 TeamAppliedEnergistics
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

package ae2.api.features;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A registry for items that can be linked to a specific network using for example the security station's user
 * interface.
 * <p/>
 * This can be used by items like wireless terminals to encode the network security key in their NBT. This security key
 * can then be used to locate the grid for that security key later, when the item wants to interact with the grid.
 */
public final class GridLinkables {

    private static final Reference2ObjectMap<Item, IGridLinkableHandler> registry = new Reference2ObjectOpenHashMap<>();

    private GridLinkables() {
    }

    /**
     * Register a handler to link or unlink stacks of a given item with a network.
     *
     * @param item    The type of item to register a handler for.
     * @param handler The handler that handles linking and unlinking for the item stacks.
     */
    public synchronized static void register(Item item, IGridLinkableHandler handler) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(handler, "handler");
        Preconditions.checkState(!registry.containsKey(item), "Handler for %s already registered", item);
        registry.put(item, handler);
    }

    /**
     * Gets the registered handler for a given item.
     */
    @Nullable
    public static synchronized IGridLinkableHandler get(Item item) {
        Objects.requireNonNull(item, "item");
        return registry.get(item);
    }

}
