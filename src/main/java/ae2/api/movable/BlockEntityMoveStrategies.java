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

package ae2.api.movable;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.tileentity.TileEntity;

import java.util.Objects;

/**
 *
 * <p/>
 * To blacklist blocks or tile entities from being moved in and out of spatial storage, use the
 * registered spatial move strategies for the affected block entities.
 */
public final class BlockEntityMoveStrategies {

    private static final IBlockEntityMoveStrategy DEFAULT_STRATEGY = new DefaultBlockEntityMoveStrategy() {
        @Override
        public boolean canHandle(Class<? extends TileEntity> type) {
            return true;
        }
    };
    private static final ObjectList<IBlockEntityMoveStrategy> strategies = new ObjectArrayList<>();
    private static final Reference2ObjectMap<Class<? extends TileEntity>, IBlockEntityMoveStrategy> valid = new Reference2ObjectOpenHashMap<>();

    /**
     * Adds a custom strategy for moving certain tile entities.
     *
     * @param strategy The strategy to add.
     */
    public synchronized static void add(IBlockEntityMoveStrategy strategy) {
        Objects.requireNonNull(strategy, "handler");
        strategies.add(strategy);
    }

    /**
     * Retrieves the strategy for moving the given tile entity to a different location.
     *
     * @return The strategy for moving the given tile entity. If no custom strategy was {@link #add registered}, the
     * {@link #getDefault() default strategy} will be returned.
     */
    public synchronized static IBlockEntityMoveStrategy get(TileEntity blockEntity) {
        Objects.requireNonNull(blockEntity, "blockEntity");

        var type = blockEntity.getClass();
        // Prefer a cached handler if possible
        var result = valid.get(type);
        if (result == null) {
            // Give custom strategies a chance
            for (var strategy : strategies) {
                if (strategy.canHandle(type)) {
                    result = strategy;
                    break;
                }
            }

            // Fall back to the default handler
            if (result == null) {
                result = DEFAULT_STRATEGY;
            }
            valid.put(type, result);
        }
        return result;
    }

    /**
     * @return The default handler for moving tile entities.
     */
    public static IBlockEntityMoveStrategy getDefault() {
        return DEFAULT_STRATEGY;
    }

}
