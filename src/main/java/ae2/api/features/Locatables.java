/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
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

import ae2.api.networking.security.IActionHost;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A Registry for locatable items, works based on serial numbers.
 */
public final class Locatables {

    private static final Type<IActionHost> QUANTUM_NETWORK_BRIDGES = new Type<>();

    private Locatables() {
    }

    /**
     * @return The registry that can be used to locate quantum network bridges using their unique key.
     */
    public static Type<IActionHost> quantumNetworkBridges() {
        return QUANTUM_NETWORK_BRIDGES;
    }

    public static class Type<T> {
        private final Long2ObjectOpenHashMap<T> objects = new Long2ObjectOpenHashMap<>();

        /**
         * Gets the currently registered locatable object for a given key. This only works server-side, which is why a
         * server-side level must be passed to ensure that server-side objects don't accidentally leak to the
         * client-side in an embedded-server scenario.
         *
         * @param level A level to ensure this is called only on the server-side. Calls on the client-side always return
         *              null.
         * @param key   The unique ID of the locatable object.
         * @return The locatable object or null, if no object is registered for the given key, or if the given level was
         * a client-side level.
         */
        @Nullable
        public T get(World level, long key) {
            Objects.requireNonNull(level, "level");
            if (level.isRemote) {
                return null;
            } else {
                return objects.get(key);
            }
        }

        /**
         * Registers a locatable with a given unique key. This call will not fail if the key is already used. Instead,
         * the latest registered locatable will overwrite any previously registered ones.
         *
         * @param level     A level to ensure this is called only on the server-side. Calls on the client-side are
         *                  silently ignored.
         * @param key       The unique key to register under.
         * @param locatable The locatable object to register.
         */
        public void register(World level, long key, T locatable) {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(locatable, "locatable");

            if (!level.isRemote) {
                objects.put(key, locatable);
            }
        }

        /**
         * Unregisters any locatable that has the given key. Doesn't fail if no locatable with the given key is
         * registered.
         *
         * @param level A level to ensure this is called only on the server-side. Calls on the client-side are silently
         *              ignored.
         */
        public void unregister(World level, long key) {
            Objects.requireNonNull(level, "level");

            if (!level.isRemote) {
                objects.remove(key);
            }
        }
    }

}
