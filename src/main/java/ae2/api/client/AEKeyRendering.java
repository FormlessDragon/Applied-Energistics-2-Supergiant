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

package ae2.api.client;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

@SideOnly(Side.CLIENT)
public class AEKeyRendering {
    private static volatile Reference2ObjectMap<AEKeyType, AEKeyRenderHandler<?>> renderers = new Reference2ObjectOpenHashMap<>();

    public static synchronized <T extends AEKey> void register(AEKeyType channel,
                                                               Class<T> keyClass,
                                                               AEKeyRenderHandler<T> handler) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(keyClass, "keyClass");
        Preconditions.checkArgument(channel.getKeyClass() == keyClass, "%s != %s",
            channel.getKeyClass(), keyClass);

        var renderersCopy = new Reference2ObjectOpenHashMap<>(renderers);
        if (renderersCopy.put(channel, handler) != null) {
            throw new IllegalArgumentException("Duplicate registration of render handler for channel " + channel);
        }
        renderers = renderersCopy;
    }

    @Nullable
    public static AEKeyRenderHandler<?> get(AEKeyType channel) {
        return renderers.get(channel);
    }

    public static AEKeyRenderHandler<?> getOrThrow(AEKeyType channel) {
        var renderHandler = get(channel);

        if (renderHandler == null) {
            throw new IllegalArgumentException("Missing render handler for channel " + channel);
        }

        return renderHandler;
    }

    @SuppressWarnings("rawtypes")
    private static AEKeyRenderHandler getUnchecked(AEKey stack) {
        Objects.requireNonNull(stack, "stack");
        return getOrThrow(stack.getType());
    }

    @SuppressWarnings("unchecked")
    public static void drawInGui(Minecraft minecraft, int x, int y, AEKey what) {
        Objects.requireNonNull(minecraft, "minecraft");
        getUnchecked(what).drawInGui(minecraft, x, y, what);
    }

    @SuppressWarnings({"unchecked", "unused"})
    public static void drawOnBlockFace(AEKey what, float scale, int combinedLightIn, World level) {
        Objects.requireNonNull(level, "level");
        getUnchecked(what).drawOnBlockFace(what, scale, combinedLightIn, level);
    }

    @SuppressWarnings("unchecked")
    public static ITextComponent getDisplayName(AEKey stack) {
        return getUnchecked(stack).getDisplayName(stack);
    }

    @SuppressWarnings("unchecked")
    public static List<ITextComponent> getTooltip(AEKey stack) {
        return new ObjectArrayList<ITextComponent>(getUnchecked(stack).getTooltip(stack));
    }
}
