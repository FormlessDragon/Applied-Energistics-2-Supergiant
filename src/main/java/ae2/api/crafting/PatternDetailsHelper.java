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

package ae2.api.crafting;

import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.core.definitions.AEItems;
import ae2.crafting.pattern.AECraftingPattern;
import ae2.crafting.pattern.AEPatternDecoder;
import ae2.crafting.pattern.AEProcessingPattern;
import com.google.common.base.Function;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PatternDetailsHelper {
    public static final long MAX_PROCESSING_PATTERN_AMOUNT = Integer.MAX_VALUE;

    private static final List<IPatternDetailsDecoder> DECODERS = new CopyOnWriteArrayList<>();

    static {
        // Register support for our own stacks.
        registerDecoder(AEPatternDecoder.INSTANCE);
    }

    public static void registerDecoder(IPatternDetailsDecoder decoder) {
        Objects.requireNonNull(decoder);
        DECODERS.add(decoder);
    }

    /**
     * Creates a new encoded pattern item based on the given decoder. Your mod must register this item and use it, when
     * it encodes its patterns. You do not need to register {@linkplain #registerDecoder an additional decoder} for the
     * returned item.
     */
    public static <T extends IPatternDetails> EncodedPatternItemBuilder<T> encodedPatternItemBuilder(
        EncodedPatternDecoder<T> decoder) {
        return new EncodedPatternItemBuilder<>(decoder);
    }

    /**
     * Convenience method for decoders that do not need access to the level to decode a pattern.
     *
     * @see #encodedPatternItemBuilder(EncodedPatternDecoder)
     */
    public static <T extends IPatternDetails> EncodedPatternItemBuilder<T> encodedPatternItemBuilder(
        Function<AEItemKey, T> decoder) {
        return new EncodedPatternItemBuilder<>((what, level) -> decoder.apply(what));
    }

    public static boolean isEncodedPattern(ItemStack stack) {
        for (var decoder : DECODERS) {
            if (decoder.isEncodedPattern(stack)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static IPatternDetails decodePattern(AEItemKey what, World level) {
        for (var decoder : DECODERS) {
            var decoded = decoder.decodePattern(what, level);
            if (decoded != null) {
                return decoded;
            }
        }
        return null;
    }

    @Nullable
    public static IPatternDetails decodePattern(ItemStack stack, World level) {
        for (var decoder : DECODERS) {
            var decoded = decoder.decodePattern(stack, level);
            if (decoded != null) {
                return decoded;
            }
        }
        return null;
    }

    /**
     * Encodes a processing pattern which represents the ability to convert the given inputs into the given outputs
     * using some process external to the ME system.
     *
     * @param sparseOutputs The first element is considered the primary output and must be present
     * @return A new encoded pattern.
     * @throws IllegalArgumentException If either in or out contain only empty ItemStacks, or no primary output
     */
    public static ItemStack encodeProcessingPattern(List<GenericStack> sparseInputs, List<GenericStack> sparseOutputs) {
        var stack = AEItems.PROCESSING_PATTERN.stack();
        AEProcessingPattern.encode(stack, sparseInputs, sparseOutputs);
        return stack;
    }

    /**
     * Encodes a crafting pattern which represents a Vanilla crafting recipe.
     *
     * @param recipe                The Vanilla crafting recipe to be encoded.
     * @param in                    The items in the crafting grid, which are used to determine what items are supplied
     *                              from the ME system to craft using this pattern.
     * @param out                   What is to be expected as the result of this crafting operation by the ME system.
     * @param allowSubstitutes      Controls whether the ME system will allow the use of equivalent items to craft this
     *                              recipe.
     * @param allowFluidSubstitutes Controls whether the ME system will allow the use of equivalent fluids.
     * @throws IllegalArgumentException If either in or out contain only empty ItemStacks.
     */
    public static ItemStack encodeCraftingPattern(IRecipe recipe, ItemStack[] in,
                                                  ItemStack out, boolean allowSubstitutes, boolean allowFluidSubstitutes) {
        var stack = AEItems.CRAFTING_PATTERN.stack();
        AECraftingPattern.encode(stack, recipe, in, out, allowSubstitutes,
            allowFluidSubstitutes);
        return stack;
    }

}
