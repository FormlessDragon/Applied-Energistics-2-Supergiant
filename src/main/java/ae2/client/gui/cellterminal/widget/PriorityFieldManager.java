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
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.ConfirmableTextField;
import ae2.core.localization.GuiText;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PriorityFieldManager {
    public static final int FIELD_WIDTH = 28;
    public static final int RIGHT_MARGIN = 21;
    private static final PriorityFieldManager INSTANCE = new PriorityFieldManager();
    private static final int FIELD_HEIGHT = 6;
    private final Map<String, InlinePriorityField> fields = new Object2ObjectOpenHashMap<>();
    private InlinePriorityField focusedField = null;

    private PriorityFieldManager() {
    }

    public static PriorityFieldManager getInstance() {
        return INSTANCE;
    }

    public void registerField(Prioritizable target, int y, int guiLeft, int guiTop, FontRenderer fontRenderer,
                              GuiStyle guiStyle) {
        InlinePriorityField field = fields.get(target.getIdentityKey());
        if (field == null) {
            field = new InlinePriorityField(target, fontRenderer, guiStyle);
            fields.put(target.getIdentityKey(), field);
        } else {
            field.updateTarget(target);
            field.updateStyle(guiStyle);
        }
        int fieldX = guiLeft + CellTerminalLayout.CONTENT_RIGHT_EDGE - FIELD_WIDTH - RIGHT_MARGIN;
        int fieldY = guiTop + y + 1;
        field.updatePosition(fieldX, fieldY);
    }

    public void drawFieldsRelative(int guiLeft, int guiTop) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(-guiLeft, -guiTop, 0);
        for (InlinePriorityField field : fields.values()) {
            if (field.isVisible()) {
                field.draw();
            }
        }
        GlStateManager.popMatrix();
    }

    public void resetVisibility() {
        for (InlinePriorityField field : fields.values()) {
            field.hide();
        }
    }

    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        for (InlinePriorityField field : fields.values()) {
            if (!field.isVisible()) {
                continue;
            }
            if (field.isMouseOver(mouseX, mouseY)) {
                if (focusedField != null && focusedField != field) {
                    focusedField.onFocusLost();
                }
                focusedField = field;
                field.mouseClicked(mouseX, mouseY, mouseButton);
                return true;
            }
        }
        if (focusedField != null) {
            focusedField.onFocusLost();
            focusedField = null;
        }
        return false;
    }

    public boolean handleKeyTyped(char typedChar, int keyCode) {
        if (focusedField == null) {
            return false;
        }
        boolean consumed = focusedField.keyTyped(typedChar, keyCode);
        if (!focusedField.isFocused()) {
            focusedField = null;
        }
        return consumed;
    }

    public List<String> getTooltip(int mouseX, int mouseY) {
        for (InlinePriorityField field : fields.values()) {
            if (field.isVisible() && field.isMouseOver(mouseX, mouseY)) {
                return Collections.singletonList(GuiText.CellTerminalPriorityTooltip.getLocal());
            }
        }
        return Collections.emptyList();
    }

    public void unfocusAll() {
        if (focusedField != null) {
            focusedField.onFocusLost();
            focusedField = null;
        }
    }

    public static final class InlinePriorityField {
        private static final Blitter TEXT_FIELD_BACKGROUND = Blitter.texture("guis/text_field.png", 128, 128);
        private static final int TEXT_FIELD_TEXTURE_HEIGHT = 12;
        private static final float TEXT_SCALE = 0.65f;
        private final ConfirmableTextField textField;
        private final FontRenderer fontRenderer;
        private Prioritizable target;
        private boolean visible = false;
        private int lastKnownPriority;

        InlinePriorityField(Prioritizable target, FontRenderer fontRenderer, GuiStyle guiStyle) {
            this.target = target;
            this.fontRenderer = fontRenderer;
            this.lastKnownPriority = target.getPriority();
            this.textField = new ConfirmableTextField(Objects.requireNonNull(guiStyle, "guiStyle"),
                fontRenderer, 0, 0, FIELD_WIDTH, FIELD_HEIGHT);
            this.textField.setMaxStringLength(8);
            this.textField.setKeyFilter(InlinePriorityField::isAllowedKey);
            this.textField.setOnConfirm(() -> {
                submitPriority();
                textField.setFocused(false);
            });
            this.textField.setText(String.valueOf(target.getPriority()));
        }

        private static boolean isAllowedKey(char typedChar, int keyCode) {
            return Character.isDigit(typedChar) || typedChar == '-'
                || keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE
                || keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT
                || keyCode == Keyboard.KEY_HOME || keyCode == Keyboard.KEY_END;
        }

        void updateTarget(Prioritizable newTarget) {
            this.target = newTarget;
        }

        void updateStyle(GuiStyle guiStyle) {
            Objects.requireNonNull(guiStyle, "guiStyle");
        }

        void updatePosition(int x, int y) {
            this.textField.move(x, y);
            this.textField.resize(FIELD_WIDTH, FIELD_HEIGHT);
            this.visible = true;
            if (target.getPriority() != lastKnownPriority && !textField.isFocused()) {
                lastKnownPriority = target.getPriority();
                textField.setText(String.valueOf(lastKnownPriority));
            }
        }

        void draw() {
            Rectangle bounds = textField.getTooltipArea();
            int yOffset = textField.isFocused() ? 24 : 0;
            TEXT_FIELD_BACKGROUND.copy().src(0, yOffset, 1, TEXT_FIELD_TEXTURE_HEIGHT)
                                 .dest(bounds.x, bounds.y, 1, FIELD_HEIGHT).blit();
            TEXT_FIELD_BACKGROUND.copy().src(1, yOffset, 126, TEXT_FIELD_TEXTURE_HEIGHT)
                                 .dest(bounds.x + 1, bounds.y, FIELD_WIDTH - 2, FIELD_HEIGHT).blit();
            TEXT_FIELD_BACKGROUND.copy().src(127, yOffset, 1, TEXT_FIELD_TEXTURE_HEIGHT)
                                 .dest(bounds.x + FIELD_WIDTH - 1, bounds.y, 1, FIELD_HEIGHT).blit();

            String text = textField.getText();
            if (!text.isEmpty()) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(bounds.x + 2, bounds.y + 1, 0);
                GlStateManager.scale(TEXT_SCALE, TEXT_SCALE, 1.0f);
                fontRenderer.drawString(text, 0, 0, 0xE0E0E0);
                GlStateManager.popMatrix();
            }
            if (textField.isFocused()) {
                int cursorPos = textField.getCursorPosition();
                String beforeCursor = text.substring(0, Math.min(cursorPos, text.length()));
                int cursorX = (int) (fontRenderer.getStringWidth(beforeCursor) * TEXT_SCALE);
                Gui.drawRect(bounds.x + 2 + cursorX, bounds.y + 1,
                    bounds.x + 3 + cursorX, bounds.y + FIELD_HEIGHT - 1, 0xFFD0D0D0);
            }
        }

        boolean isVisible() {
            return visible;
        }

        void hide() {
            this.visible = false;
        }

        boolean isMouseOver(int mouseX, int mouseY) {
            return textField.isMouseOver(mouseX, mouseY);
        }

        void mouseClicked(int mouseX, int mouseY, int mouseButton) {
            textField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        boolean keyTyped(char typedChar, int keyCode) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                textField.setText(String.valueOf(target.getPriority()));
                textField.setFocused(false);
                return true;
            }
            return textField.textboxKeyTyped(typedChar, keyCode);
        }

        boolean isFocused() {
            return textField.isFocused();
        }

        void onFocusLost() {
            if (textField.isFocused()) {
                submitPriority();
                textField.setFocused(false);
            }
        }

        private void submitPriority() {
            try {
                int newPriority = Integer.parseInt(textField.getText().trim());
                if (newPriority != target.getPriority()) {
                    target.commitPriority(newPriority);
                    lastKnownPriority = newPriority;
                }
            } catch (NumberFormatException e) {
                textField.setText(String.valueOf(target.getPriority()));
            }
        }
    }
}
