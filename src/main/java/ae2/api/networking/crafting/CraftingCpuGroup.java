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

package ae2.api.networking.crafting;

import net.minecraft.util.ResourceLocation;

import java.util.Objects;

/**
 * Marks a crafting CPU as belonging to a group in the crafting status CPU list.
 * <p/>
 * CPUs sharing a {@link #groupId()} are kept contiguous in the list regardless of the sort mode the player selected,
 * and are ordered among themselves by {@link #memberOrdinal()}. The group as a whole takes the list position of
 * whichever member sorts first under the active sort mode, so grouping never overrides the player's sorting choice
 * for the list as a whole.
 * <p/>
 * This is intended for machines that expose several CPUs backed by one physical multi-block, so those CPUs stay
 * visually together instead of being scattered across the list.
 *
 * @param groupId       identifies the group. Must be unique per group instance, not per machine type, so that two
 *                      separate multi-blocks of the same kind form two separate groups.
 * @param memberOrdinal orders members within the group. Members with equal ordinals keep their relative order from
 *                      the active sort mode.
 */
public record CraftingCpuGroup(ResourceLocation groupId, int memberOrdinal) {

    public CraftingCpuGroup {
        Objects.requireNonNull(groupId, "groupId");
    }
}
