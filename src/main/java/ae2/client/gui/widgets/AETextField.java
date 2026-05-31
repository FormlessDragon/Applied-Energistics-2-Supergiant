/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

import ae2.client.Point;
import ae2.client.gui.style.Blitter;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.PaletteColor;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A modified version of the Minecraft text field. You can initialize it over the full element span. The mouse click
 * area is increased to the full element subtracted with the defined padding.
 * <p>
 * The rendering does pay attention to the size of the '_' caret.
 */
public class AETextField extends GuiTextField implements IResizableWidget, ITooltip {
    private static final Blitter BLITTER = Blitter.texture("guis/text_field.png", 128, 128);

    private static final int PADDING = 2;

    private final FontRenderer fontRenderer;
    private final int fontPad;
    private final GuiStyle style;
    private int selectionColor;
    private boolean enabled = true;
    private List<ITextComponent> tooltipMessage = ObjectLists.emptyList();
    @Nullable
    private Consumer<String> responder;

    /**
     * Displayed with a muted text color when the text box is unfocused and has no content.
     */
    @Nullable
    private ITextComponent placeholder;

    /**
     * Uses the values to instantiate a padded version of a text field. Pays attention to the '_' caret.
     *
     * @param fontRenderer renderer for the strings
     * @param xPos         absolute left position
     * @param yPos         absolute top position
     * @param width        absolute width
     * @param height       absolute height
     */
    public AETextField(GuiStyle style, FontRenderer fontRenderer, int xPos, int yPos, int width, int height) {
        super(0, fontRenderer, xPos + PADDING, yPos + PADDING,
            width - 2 * PADDING - fontRenderer.getStringWidth("_"), height - 2 * PADDING);

        this.fontRenderer = fontRenderer;
        this.style = style;
        this.fontPad = fontRenderer.getStringWidth("_");
        this.setEnableBackgroundDrawing(false);
        this.setSelectionColor(style.getColor(PaletteColor.TEXTFIELD_SELECTION).toARGB());
        this.setTextColor(style.getColor(PaletteColor.TEXTFIELD_TEXT).toARGB());
        this.setDisabledTextColour(style.getColor(PaletteColor.TEXTFIELD_TEXT).toARGB());
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        return getVisualBounds().contains(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (this.isMouseOver(mouseX, mouseY)) {
            mouseX = MathHelper.clamp(mouseX, this.x, this.x + this.width - 1);
            mouseY = MathHelper.clamp(mouseY, this.y, this.y + this.height - 1);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean textboxKeyTyped(char typedChar, int keyCode) {
        if (super.textboxKeyTyped(typedChar, keyCode)) {
            if (this.responder != null) {
                this.responder.accept(this.getText());
            }
            return true;
        }

        return this.isFocused() && this.canConsumeInput() && keyCode != Keyboard.KEY_TAB && keyCode != Keyboard.KEY_ESCAPE;
    }

    public void setResponder(@Nullable Consumer<String> responder) {
        this.responder = responder;
    }

    public void setTextFromClient(String text) {
        String oldValue = this.getText();
        super.setText(text);
        if (!oldValue.equals(this.getText()) && this.responder != null) {
            this.responder.accept(this.getText());
        }
    }

    protected boolean canConsumeInput() {
        return this.enabled && this.getVisible();
    }

    public void move(Point pos) {
        this.move(pos.x(), pos.y());
    }

    @Override
    public void move(int x, int y) {
        this.x = x + PADDING;
        this.y = y + PADDING;
    }

    @Override
    public void resize(int width, int height) {
        this.width = width - 2 * PADDING - this.fontPad;
        this.height = height - 2 * PADDING;
    }

    public void selectAll() {
        this.setCursorPositionZero();
        this.setSelectionPos(this.getMaxStringLength());
    }

    public int getSelectionColor() {
        return this.selectionColor;
    }

    public void setSelectionColor(int color) {
        this.selectionColor = color;
    }

    @Override
    public void drawTextBox() {
        if (!this.getVisible()) {
            return;
        }

        GlStateManager.pushMatrix();
        try {
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GlStateManager.colorMask(true, true, true, true);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            int yOffset = 0;
            if (!this.enabled) {
                yOffset = 12;
            } else if (this.isFocused()) {
                yOffset = 24;
            }

            Rectangle bounds = getVisualBounds();

            BLITTER.copy().src(0, yOffset, 1, 12)
                   .dest(bounds.x, bounds.y)
                   .blit();
            int backgroundWidth = Math.min(126, bounds.width - 2);

            BLITTER.copy().src(1, yOffset, backgroundWidth, 12)
                   .dest(bounds.x + 1, bounds.y)
                   .blit();

            BLITTER.copy().src(127, yOffset, 1, 12)
                   .dest(bounds.x + bounds.width - 1, bounds.y)
                   .blit();

            super.drawTextBox();

            if (this.placeholder != null && !this.isFocused() && this.getText().isEmpty()) {
                this.fontRenderer.drawString(this.placeholder.getFormattedText(), this.x, this.y,
                    this.style.getColor(PaletteColor.TEXTFIELD_PLACEHOLDER).toARGB());
            }
        } finally {
            GlStateManager.colorMask(true, true, true, true);
            GlStateManager.enableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    @Override
    public Rectangle getTooltipArea() {
        return new Rectangle(
            this.x - PADDING,
            this.y - PADDING,
            this.width + 2 * PADDING + this.fontPad,
            this.height + 2 * PADDING);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return this.getVisible();
    }

    @Override
    public List<ITextComponent> getTooltipMessage() {
        return this.tooltipMessage;
    }

    public void setTooltipMessage(List<ITextComponent> tooltipMessage) {
        this.tooltipMessage = Objects.requireNonNull(tooltipMessage);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        this.enabled = enabled;
    }

    private Rectangle getVisualBounds() {
        int left = this.x - PADDING;
        int top = this.y - PADDING;
        return new Rectangle(
            left,
            top,
            this.width + 2 * PADDING + this.fontPad,
            this.height + 2 * PADDING);
    }

    @Nullable
    public ITextComponent getPlaceholder() {
        return this.placeholder;
    }

    public void setPlaceholder(@Nullable ITextComponent placeholder) {
        this.placeholder = placeholder;
    }
}
