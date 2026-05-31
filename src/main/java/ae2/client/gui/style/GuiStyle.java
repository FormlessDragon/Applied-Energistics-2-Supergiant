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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.awt.Rectangle;
import java.lang.reflect.Type;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public class GuiStyle {

    public static final Gson GSON = new GsonBuilder()
        .disableHtmlEscaping()
        .registerTypeHierarchyAdapter(ITextComponent.class, new TextComponentSerializer())
        .registerTypeAdapter(Blitter.class, BlitterDeserializer.INSTANCE)
        .registerTypeAdapter(Rectangle.class, Rectangle2dDeserializer.INSTANCE)
        .registerTypeAdapter(Color.class, ColorDeserializer.INSTANCE)
        .create();

    private final Map<String, SlotPosition> slots = new Object2ObjectOpenHashMap<>();

    private final Map<String, Text> text = new Object2ObjectOpenHashMap<>();

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final Map<PaletteColor, Color> palette = new EnumMap<>(PaletteColor.class);

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final Map<String, Blitter> images = new Object2ObjectOpenHashMap<>();
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final Map<String, WidgetStyle> widgets = new Object2ObjectOpenHashMap<>();
    private final Map<String, TooltipArea> tooltips = new Object2ObjectOpenHashMap<>();
    @Nullable
    @SuppressWarnings("unused")
    private Blitter background;
    @Nullable
    @SuppressWarnings("unused")
    private GeneratedBackground generatedBackground;
    @Nullable
    @SuppressWarnings("unused")
    private TerminalStyle terminalStyle;

    public Color getColor(PaletteColor color) {
        return Objects.requireNonNull(palette.get(color), "Screen is missing required palette color: " + color);
    }

    public Map<String, SlotPosition> getSlots() {
        return slots;
    }

    public Map<String, Text> getText() {
        return text;
    }

    public Map<String, TooltipArea> getTooltips() {
        return tooltips;
    }

    @Nullable
    public Blitter getBackground() {
        return background != null ? background.copy() : null;
    }

    @Nullable
    public GeneratedBackground getGeneratedBackground() {
        return generatedBackground;
    }

    public WidgetStyle getWidget(String id) {
        WidgetStyle widget = widgets.get(id);
        if (widget == null) {
            throw new IllegalStateException("Screen is missing required widget: " + id);
        }
        return widget;
    }

    public Blitter getImage(String id) {
        Blitter blitter = images.get(id);
        if (blitter == null) {
            throw new IllegalStateException("Screen is missing required image: " + id);
        }
        return blitter;
    }

    @Nullable
    public TerminalStyle getTerminalStyle() {
        return terminalStyle;
    }

    public void validate() {
        for (PaletteColor value : PaletteColor.values()) {
            if (!palette.containsKey(value)) {
                throw new RuntimeException("Palette is missing color " + value);
            }
        }

        if (terminalStyle != null) {
            terminalStyle.validate();
        }
    }

    private static class TextComponentSerializer
        implements JsonSerializer<ITextComponent>, JsonDeserializer<ITextComponent> {
        @Override
        public ITextComponent deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            return ITextComponent.Serializer.jsonToComponent(json.toString());
        }

        @Override
        public JsonElement serialize(ITextComponent src, Type typeOfSrc, JsonSerializationContext context) {
            return JsonParser.parseString(ITextComponent.Serializer.componentToJson(src));
        }
    }
}


