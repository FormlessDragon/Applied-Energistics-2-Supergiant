/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026, TeamAppliedEnergistics, All rights reserved.
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

package ae2.client.gui.cellterminal.widget;

import ae2.client.gui.style.Blitter;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.ConfirmableTextField;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CellTerminalSearchOverlay {
    private static final Blitter TEXT_FIELD_BACKGROUND = Blitter.texture("guis/text_field.png", 128, 128);
    private static final int EXTRA_WIDTH = 128;
    private static final int MIN_WIDTH = 220;
    private static final int MAX_WIDTH = 320;
    private static final int TEXT_PADDING = 5;
    private static final int VISIBLE_LINES = 5;
    private static final int LINE_HEIGHT = 11;
    private static final int SCREEN_MARGIN = 4;
    private static final int TEXT_FIELD_TOP_HEIGHT = 3;
    private static final int TEXT_FIELD_MIDDLE_HEIGHT = 8;
    private static final int TEXT_FIELD_BOTTOM_HEIGHT = 1;
    private static final int PLACEHOLDER_COLOR = 0x606060;
    private static final int CURSOR_COLOR = 0xFF202020;
    private static final int SELECTION_COLOR = 0x803399FF;

    private final ConfirmableTextField field;
    private final AETextField sourceField;
    private final FontRenderer fontRenderer;
    private boolean visible;
    private Rectangle guiBounds = new Rectangle();
    private Rectangle panelBounds = new Rectangle();
    private Rectangle textBounds = new Rectangle();

    public CellTerminalSearchOverlay(FontRenderer fontRenderer, AETextField sourceField) {
        this.fontRenderer = Objects.requireNonNull(fontRenderer, "fontRenderer");
        this.sourceField = Objects.requireNonNull(sourceField, "sourceField");
        this.field = new ConfirmableTextField(
            Objects.requireNonNull(sourceField.getStyle(), "sourceField.style"),
            this.fontRenderer,
            0, 0, 0, 0);
        this.field.setMaxStringLength(sourceField.getMaxStringLength());
        this.field.setEnableBackgroundDrawing(false);
        this.field.setVisible(false);
        this.field.setCanLoseFocus(false);
        this.field.setResponder(this::syncToSource);
        this.field.setOnConfirm(this::close);
    }

    private static void drawTextFieldBackground(Rectangle bounds, boolean focused, boolean enabled) {
        int yOffset = 0;
        if (!enabled) {
            yOffset = 12;
        } else if (focused) {
            yOffset = 24;
        }
        int topHeight = Math.min(TEXT_FIELD_TOP_HEIGHT, bounds.height);
        drawTextFieldBackgroundSlice(bounds.x, bounds.y, bounds.width, yOffset, topHeight);

        int bottomHeight = bounds.height > topHeight
            ? Math.min(TEXT_FIELD_BOTTOM_HEIGHT, bounds.height - topHeight)
            : 0;
        int middleTop = bounds.y + topHeight;
        int middleBottom = bounds.y + bounds.height - bottomHeight;
        int remainingMiddleHeight = Math.max(0, middleBottom - middleTop);
        int backgroundY = middleTop;
        while (remainingMiddleHeight > 0) {
            int backgroundHeight = Math.min(TEXT_FIELD_MIDDLE_HEIGHT, remainingMiddleHeight);
            drawTextFieldBackgroundSlice(bounds.x, backgroundY, bounds.width, yOffset + TEXT_FIELD_TOP_HEIGHT,
                backgroundHeight);
            backgroundY += backgroundHeight;
            remainingMiddleHeight -= backgroundHeight;
        }

        if (bottomHeight > 0) {
            drawTextFieldBackgroundSlice(bounds.x, bounds.y + bounds.height - bottomHeight, bounds.width,
                yOffset + TEXT_FIELD_TOP_HEIGHT + TEXT_FIELD_MIDDLE_HEIGHT, bottomHeight);
        }
    }

    private static void drawTextFieldBackgroundSlice(int x, int y, int width, int srcY, int height) {
        TEXT_FIELD_BACKGROUND.copy().src(0, srcY, 1, height)
                             .dest(x, y, 1, height).blit();
        int remainingWidth = width - 2;
        int backgroundX = x + 1;
        while (remainingWidth > 0) {
            int backgroundWidth = Math.min(126, remainingWidth);
            TEXT_FIELD_BACKGROUND.copy().src(1, srcY, backgroundWidth, height)
                                 .dest(backgroundX, y, backgroundWidth, height).blit();
            backgroundX += backgroundWidth;
            remainingWidth -= backgroundWidth;
        }
        TEXT_FIELD_BACKGROUND.copy().src(127, srcY, 1, height)
                             .dest(x + width - 1, y, 1, height).blit();
    }

    public void open() {
        if (this.visible) {
            reposition();
            this.field.setFocused(true);
            this.field.selectAll();
            return;
        }
        syncFromSource();
        reposition();
        this.visible = true;
        this.field.setVisible(true);
        this.field.setFocused(true);
        this.field.setCanLoseFocus(false);
        this.field.selectAll();
    }

    public void close() {
        if (!this.visible) {
            return;
        }
        this.visible = false;
        this.field.setFocused(false);
        this.field.setVisible(false);
        this.field.setCanLoseFocus(true);
        this.sourceField.setFocused(false);
    }

    public boolean isVisible() {
        return this.visible;
    }

    public boolean contains(int mouseX, int mouseY) {
        return this.visible && this.panelBounds.contains(mouseX, mouseY);
    }

    public ConfirmableTextField getField() {
        return this.field;
    }

    public void setGuiBounds(Rectangle guiBounds) {
        Objects.requireNonNull(guiBounds, "guiBounds");
        if (guiBounds.width <= 0 || guiBounds.height <= 0) {
            throw new IllegalArgumentException("GUI bounds must have positive size: " + guiBounds);
        }
        this.guiBounds = new Rectangle(guiBounds);
        if (this.visible) {
            reposition();
        }
    }

    public Rectangle getAssistAnchorArea() {
        return new Rectangle(this.panelBounds);
    }

    public void draw() {
        if (!this.visible) {
            return;
        }
        reposition();
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

            drawTextFieldBackground(this.panelBounds, this.field.isFocused(), this.field.isEnabled());
            drawMultilineText();
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

    public void tickKeyRepeat() {
        if (this.visible) {
            this.field.tickKeyRepeat();
        }
    }

    public boolean handleMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!this.visible) {
            return false;
        }
        if (this.panelBounds.contains(mouseX, mouseY)) {
            if (mouseButton == 1) {
                this.field.setTextFromClient("");
                this.field.setCursorPosition(0);
                this.field.setSelectionPos(0);
                this.field.setFocused(true);
                return true;
            }
            this.field.setFocused(true);
            int cursor = cursorPositionAt(mouseX, mouseY);
            this.field.setCursorPosition(cursor);
            this.field.setSelectionPos(cursor);
            return true;
        }
        close();
        return true;
    }

    public boolean handleKeyTyped(char typedChar, int keyCode, CellTerminalSearchAssist searchAssist) {
        if (!this.visible) {
            return false;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            return searchAssist.handleTab(this.field, Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
        }
        if (this.field.textboxKeyTyped(typedChar, keyCode)) {
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            close();
            return true;
        }
        return false;
    }

    private void reposition() {
        Rectangle sourceBounds = this.sourceField.getTooltipArea();
        if (this.guiBounds.width <= 0 || this.guiBounds.height <= 0) {
            throw new IllegalStateException("Cell Terminal search overlay requires GUI bounds before positioning");
        }

        int maxPanelWidth = Math.clamp(this.guiBounds.width - SCREEN_MARGIN * 2, 1, MAX_WIDTH);
        int panelWidth = Math.clamp(sourceBounds.width + EXTRA_WIDTH, Math.min(MIN_WIDTH, maxPanelWidth),
            maxPanelWidth);
        int panelHeight = TEXT_PADDING * 2 + VISIBLE_LINES * LINE_HEIGHT;
        int left = sourceBounds.x + sourceBounds.width - panelWidth;
        int minLeft = this.guiBounds.x + SCREEN_MARGIN;
        int maxLeft = Math.max(minLeft, this.guiBounds.x + this.guiBounds.width - panelWidth - SCREEN_MARGIN);
        left = Math.clamp(left, minLeft, maxLeft);
        int minTop = this.guiBounds.y + SCREEN_MARGIN;
        int maxTop = Math.max(minTop, this.guiBounds.y + this.guiBounds.height - panelHeight - SCREEN_MARGIN);
        int top = Math.clamp(sourceBounds.y, minTop, maxTop);

        this.panelBounds = new Rectangle(left, top, panelWidth, panelHeight);
        this.textBounds = new Rectangle(
            left + TEXT_PADDING,
            top + TEXT_PADDING,
            panelWidth - TEXT_PADDING * 2,
            panelHeight - TEXT_PADDING * 2);
        this.field.move(left, top);
        this.field.resize(panelWidth, panelHeight);
    }

    private void syncFromSource() {
        this.field.setPlaceholder(this.sourceField.getPlaceholder());
        this.field.setTooltipMessage(CellTerminalSearchAssist.shortTooltip());
        String text = this.sourceField.getText();
        if (!Objects.equals(this.field.getText(), text)) {
            this.field.setText(text);
        }
    }

    private void syncToSource(String text) {
        this.sourceField.setTextFromClient(text);
    }

    private void drawMultilineText() {
        boolean focused = this.field.isFocused();
        String actualText = this.field.getText();
        boolean placeholder = actualText.isEmpty() && !focused;
        String text = placeholder
            ? Objects.requireNonNullElse(this.sourceField.getPlaceholder(), "")
            : actualText;
        List<WrappedLine> lines = wrapLines(text);
        int color = placeholder
            ? PLACEHOLDER_COLOR
            : this.field.getCurrentTextColor();
        drawSelection(lines);
        int textY = this.textBounds.y;
        int maxLines = Math.max(1, this.textBounds.height / LINE_HEIGHT);
        for (int index = 0; index < Math.min(maxLines, lines.size()); index++) {
            this.fontRenderer.drawString(lines.get(index).text(), this.textBounds.x, textY, color);
            textY += LINE_HEIGHT;
        }
        if (focused) {
            drawCursor(lines);
        }
    }

    private void drawCursor(List<WrappedLine> lines) {
        int cursor = this.field.getCursorPosition();
        CursorLocation location = cursorLocation(lines, cursor);
        if (((System.currentTimeMillis() / 500L) & 1L) == 0L) {
            int x = location.x();
            int y = location.y();
            Gui.drawRect(x, y, x + 1, y + this.fontRenderer.FONT_HEIGHT, CURSOR_COLOR);
        }
    }

    private void drawSelection(List<WrappedLine> lines) {
        int cursor = this.field.getCursorPosition();
        int selection = this.field.getSelectionEnd();
        if (cursor == selection) {
            return;
        }
        int start = Math.min(cursor, selection);
        int end = Math.max(cursor, selection);
        for (int index = 0; index < lines.size(); index++) {
            WrappedLine line = lines.get(index);
            int lineStart = Math.max(start, line.start());
            int lineEnd = Math.min(end, line.end());
            if (lineStart >= lineEnd) {
                continue;
            }
            int x1 = this.textBounds.x + this.fontRenderer.getStringWidth(
                this.field.getText().substring(line.start(), lineStart));
            int x2 = this.textBounds.x + this.fontRenderer.getStringWidth(
                this.field.getText().substring(line.start(), lineEnd));
            int y = this.textBounds.y + index * LINE_HEIGHT;
            Gui.drawRect(x1, y, x2, y + this.fontRenderer.FONT_HEIGHT, SELECTION_COLOR);
        }
    }

    private CursorLocation cursorLocation(List<WrappedLine> lines, int cursor) {
        for (int index = 0; index < lines.size(); index++) {
            WrappedLine line = lines.get(index);
            if (cursor >= line.start() && cursor <= line.end()) {
                String textBeforeCursor = this.field.getText().substring(line.start(), cursor);
                return new CursorLocation(
                    this.textBounds.x + this.fontRenderer.getStringWidth(textBeforeCursor),
                    this.textBounds.y + index * LINE_HEIGHT);
            }
        }
        int lastLineIndex = Math.max(0, lines.size() - 1);
        WrappedLine lastLine = lines.get(lastLineIndex);
        return new CursorLocation(
            this.textBounds.x + this.fontRenderer.getStringWidth(lastLine.text()),
            this.textBounds.y + lastLineIndex * LINE_HEIGHT);
    }

    private int cursorPositionAt(int mouseX, int mouseY) {
        List<WrappedLine> lines = wrapLines(this.field.getText());
        int lineIndex = Math.clamp((mouseY - this.textBounds.y) / LINE_HEIGHT, 0, Math.max(0, lines.size() - 1));
        WrappedLine line = lines.get(lineIndex);
        int relativeX = Math.max(0, mouseX - this.textBounds.x);
        int bestCursor = line.start();
        int bestDistance = Math.abs(relativeX);
        for (int cursor = line.start(); cursor <= line.end(); cursor++) {
            int x = this.fontRenderer.getStringWidth(this.field.getText().substring(line.start(), cursor));
            int distance = Math.abs(relativeX - x);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestCursor = cursor;
            }
        }
        return bestCursor;
    }

    private List<WrappedLine> wrapLines(String text) {
        if (text.isEmpty()) {
            return List.of(new WrappedLine(0, 0, ""));
        }
        List<WrappedLine> lines = new ArrayList<>();
        int start = 0;
        int maxWidth = Math.max(1, this.textBounds.width);
        while (start < text.length()) {
            int end = start + 1;
            int lastFitting = end;
            while (end <= text.length()) {
                String part = text.substring(start, end);
                if (this.fontRenderer.getStringWidth(part) > maxWidth) {
                    break;
                }
                lastFitting = end;
                end++;
            }
            if (lastFitting == start) {
                lastFitting = Math.min(text.length(), start + 1);
            }
            lines.add(new WrappedLine(start, lastFitting, text.substring(start, lastFitting)));
            start = lastFitting;
        }
        return lines;
    }

    private record WrappedLine(int start, int end, String text) {
    }

    private record CursorLocation(int x, int y) {
    }
}
