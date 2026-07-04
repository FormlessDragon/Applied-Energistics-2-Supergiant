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

package ae2.client.gui.widgets;

import ae2.client.gui.style.Blitter;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.PaletteColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.text.ITextComponent;
import org.jspecify.annotations.NonNull;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;

public class AECheckbox extends GuiButton implements ITooltip {
    public static final int SIZE = 14;

    private static final Blitter BLITTER = Blitter.texture("guis/checkbox.png", 64, 64);

    private static final Blitter UNCHECKED = BLITTER.copy().src(0, 28, 22, 12);
    private static final Blitter UNCHECKED_FOCUS = BLITTER.copy().src(22, 28, 22, 12);
    private static final Blitter CHECKED = BLITTER.copy().src(0, 40, 22, 12);
    private static final Blitter CHECKED_FOCUS = BLITTER.copy().src(22, 40, 22, 12);
    private static final Blitter RADIO_UNCHECKED = BLITTER.copy().src(2 * SIZE, 0, SIZE, SIZE);
    private static final Blitter RADIO_UNCHECKED_FOCUS = BLITTER.copy().src(3 * SIZE, 0, SIZE, SIZE);
    private static final Blitter RADIO_CHECKED = BLITTER.copy().src(2 * SIZE, SIZE, SIZE, SIZE);
    private static final Blitter RADIO_CHECKED_FOCUS = BLITTER.copy().src(3 * SIZE, SIZE, SIZE, SIZE);

    private final GuiStyle style;
    private boolean selected;
    private Runnable changeListener;
    private boolean radio;
    private boolean focused;
    private List<ITextComponent> tooltipMessage = Collections.emptyList();

    public AECheckbox(int x, int y, int width, int height, GuiStyle style, ITextComponent component) {
        super(0, x, y, width, height, component.getFormattedText());
        this.style = style;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        boolean pressed = this.enabled && this.visible
            && mouseX >= this.x
            && mouseY >= this.y
            && mouseX < this.x + this.width
            && mouseY < this.y + this.height;
        super.mouseReleased(mouseX, mouseY);
        if (pressed) {
            this.selected = !this.selected;
            if (this.changeListener != null) {
                this.changeListener.run();
            }
        }
    }

    @Override
    public boolean mousePressed(Minecraft minecraft, int mouseX, int mouseY) {
        boolean pressed = super.mousePressed(minecraft, mouseX, mouseY);
        this.focused = pressed;
        return pressed;
    }

    public boolean isRadio() {
        return this.radio;
    }

    public void setRadio(boolean radio) {
        this.radio = radio;
    }

    public boolean isSelected() {
        return this.selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public void setChangeListener(Runnable listener) {
        this.changeListener = listener;
    }

    public boolean isFocused() {
        return this.focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    @Override
    public @NonNull List<ITextComponent> getTooltipMessage() {
        return this.tooltipMessage;
    }

    public void setTooltipMessage(List<ITextComponent> tooltipMessage) {
        this.tooltipMessage = List.copyOf(tooltipMessage);
    }

    @Override
    public Rectangle getTooltipArea() {
        return new Rectangle(this.x, this.y, this.width, this.height);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return this.visible && !this.tooltipMessage.isEmpty();
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }

        this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
        if (!this.hovered) {
            this.setFocused(false);
        }

        Blitter icon = getIcon();
        float opacity = this.enabled ? 1.0F : 0.5F;
        int textColor = this.enabled ? this.style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB()
            : this.style.getColor(PaletteColor.MUTED_TEXT_COLOR).toARGB();

        icon.dest(this.x, this.y).opacity(opacity).blit();

        FontRenderer font = minecraft.fontRenderer;
        List<String> lines = font.listFormattedStringToWidth(this.displayString, this.width - 22);
        int lineY = this.y + (lines.size() <= 1 ? 4 : 1);
        int textX = this.x + (this.radio ? 16 : 26);
        for (String line : lines) {
            font.drawString(line, textX, lineY, textColor);
            lineY += font.FONT_HEIGHT;
        }
    }

    private Blitter getIcon() {
        if (this.radio) {
            if (this.hovered) {
                return this.selected ? RADIO_CHECKED_FOCUS.copy() : RADIO_UNCHECKED_FOCUS.copy();
            }
            return this.selected ? RADIO_CHECKED.copy() : RADIO_UNCHECKED.copy();
        }

        if (this.hovered && !this.isFocused()) {
            return this.selected ? CHECKED_FOCUS.copy() : UNCHECKED_FOCUS.copy();
        }

        return this.selected ? CHECKED.copy() : UNCHECKED.copy();
    }
}

