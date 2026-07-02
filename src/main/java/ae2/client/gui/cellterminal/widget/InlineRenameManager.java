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

import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.ConfirmableTextField;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;

import java.util.Objects;

/**
 * Manages the active Cell Terminal inline rename field.
 * <p>
 * Headers provide GUI-local bounds when right-clicked. This manager keeps the editing session stable across row
 * rebuilds and renders a standard AE2 text field at the current screen-space position.
 */
public final class InlineRenameManager {
    private static final InlineRenameManager INSTANCE = new InlineRenameManager();
    private static final int MAX_NAME_LENGTH = 50;
    private static final int TEXT_FIELD_HEIGHT = 12;
    private static final int MIN_FIELD_WIDTH = 24;

    private Renameable editingTarget;
    private ConfirmableTextField textField;
    private int guiLeft;
    private int guiTop;
    private int editingY;
    private int editingX = CellTerminalLayout.GUI_INDENT + 20;
    private int editingRightEdge = CellTerminalLayout.CONTENT_RIGHT_EDGE - 4;

    private InlineRenameManager() {
    }

    public static InlineRenameManager getInstance() {
        return INSTANCE;
    }

    private static boolean isSameTarget(Renameable a, Renameable b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return Objects.equals(a.getIdentityKey(), b.getIdentityKey());
    }

    public boolean isEditing() {
        return editingTarget != null && textField != null;
    }

    public void updateContext(GuiStyle style, FontRenderer fontRenderer, int guiLeft, int guiTop) {
        this.guiLeft = guiLeft;
        this.guiTop = guiTop;
        if (this.textField == null) {
            this.textField = new ConfirmableTextField(Objects.requireNonNull(style), fontRenderer, 0, 0,
                MIN_FIELD_WIDTH, TEXT_FIELD_HEIGHT);
            this.textField.setMaxStringLength(MAX_NAME_LENGTH);
            this.textField.setOnConfirm(this::confirmEditing);
            this.textField.setVisible(false);
        }
        moveFieldToCurrentBounds();
    }

    public void startEditing(Renameable target, int y, int x, int rightEdge) {
        if (target == null || !target.isRenameable()) {
            return;
        }
        if (isEditing() && isSameTarget(editingTarget, target)) {
            this.textField.setFocused(true);
            this.textField.selectAll();
            return;
        }
        if (isEditing()) {
            confirmEditing();
        }
        ensureFieldReady();
        this.editingTarget = target;
        this.editingY = y;
        this.editingX = x;
        this.editingRightEdge = rightEdge;
        this.textField.setText(target.getCurrentName() == null ? "" : target.getCurrentName());
        this.textField.setVisible(true);
        this.textField.setFocused(true);
        this.textField.selectAll();
        moveFieldToCurrentBounds();
    }

    public void confirmEditing() {
        if (!isEditing()) {
            return;
        }
        Renameable target = editingTarget;
        String newName = this.textField.getText().trim();
        clearState();
        target.commitName(newName);
    }

    public void cancelEditing() {
        clearState();
    }

    private void clearState() {
        editingTarget = null;
        if (this.textField != null) {
            this.textField.setFocused(false);
            this.textField.setVisible(false);
        }
    }

    public boolean handleKey(char typedChar, int keyCode) {
        if (!isEditing()) {
            return false;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            cancelEditing();
            return true;
        }
        return this.textField.textboxKeyTyped(typedChar, keyCode);
    }

    public boolean handleMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!isEditing()) {
            return false;
        }
        if (!this.textField.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        if (mouseButton == 1) {
            this.textField.setText("");
            this.textField.setFocused(true);
            return true;
        }
        this.textField.mouseClicked(mouseX, mouseY, mouseButton);
        return true;
    }

    public void handleClickOutside(int mouseX, int mouseY) {
        if (!isEditing()) {
            return;
        }
        int screenX = this.guiLeft + mouseX;
        int screenY = this.guiTop + mouseY;
        if (this.textField.isMouseOver(screenX, screenY)) {
            return;
        }
        confirmEditing();
    }

    public void tickKeyRepeat() {
        if (!isEditing()) {
            return;
        }
        this.textField.tickKeyRepeat();
    }

    public void drawRenameField() {
        if (!isEditing()) {
            return;
        }
        moveFieldToCurrentBounds();
        GlStateManager.pushMatrix();
        GlStateManager.translate(-this.guiLeft, -this.guiTop, 0.0F);
        try {
            this.textField.drawTextBox();
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private void ensureFieldReady() {
        if (this.textField == null) {
            throw new IllegalStateException("Cell Terminal rename field used before GUI context was initialized");
        }
    }

    private void moveFieldToCurrentBounds() {
        if (this.textField == null) {
            return;
        }
        int width = Math.max(MIN_FIELD_WIDTH, this.editingRightEdge - this.editingX);
        this.textField.move(this.guiLeft + this.editingX, this.guiTop + this.editingY);
        this.textField.resize(width, TEXT_FIELD_HEIGHT);
    }
}
