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

import ae2.client.gui.Icon;
import ae2.client.gui.style.PaletteColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Dropdown button and popup that shows the most recent search terms of an {@link AETextField}.
 * <p>
 * The button occupies the left edge of the text field's assigned area, which keeps it from overlapping neighbouring
 * widgets. The popup is rendered by the {@link ae2.client.gui.WidgetContainer} after all other layers so that it stays
 * on top of item listings and scroll bars.
 */
@SideOnly(Side.CLIENT)
public class SearchHistoryDropdown {
    public static final int RESERVED_WIDTH = 10;

    private static final int BUTTON_WIDTH = 9;
    private static final int BUTTON_GAP = RESERVED_WIDTH - BUTTON_WIDTH;
    private static final int ROW_HEIGHT = 11;
    private static final int ROW_PADDING = 1;

    private static final int POPUP_BACKGROUND = 0xAA000000;
    private static final int ROW_HOVER = 0x30FFFFFF;
    private static final int ROW_SELECTED = 0x60FFFFFF;

    private final AETextField field;
    private final List<String> rows = new ArrayList<>();

    private boolean enabled;
    private boolean open;
    private boolean openedByButton;
    private boolean applying;
    private boolean commitPending;
    private boolean buttonHovered;
    private int selected = -1;
    private String lastText;
    @Nullable
    private Rectangle lastGuiBounds;

    public SearchHistoryDropdown(AETextField field) {
        this.field = Objects.requireNonNull(field, "field");
        this.lastText = field.getText();
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * @return Whether the mouse was over the dropdown button during the last frame.
     */
    public boolean isButtonHovered() {
        return this.buttonHovered;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }

        this.enabled = enabled;
        if (!enabled) {
            onDisabled();
        }
    }

    /**
     * Opens the suggestion list when the player edited the search field. The popup is also opened while the typed
     * term does not match a stored term, so the player can always pick a recent search.
     */
    public void onTextEdited() {
        if (!this.enabled) {
            return;
        }

        String text = this.field.getText();
        if (this.applying) {
            this.lastText = text;
            return;
        }

        if (text.equals(this.lastText)) {
            return;
        }
        this.lastText = text;

        if (!this.field.isFocused() || !this.field.isEnabled() || !this.field.getVisible()) {
            return;
        }

        this.commitPending = !text.trim().isEmpty();
        showSuggestions(text);
    }

    public void onFocusLost() {
        if (!this.enabled) {
            return;
        }

        commitPendingEntry();
        close();
    }

    public void onHidden() {
        onFocusLost();
    }

    public void onDisabled() {
        commitPendingEntry();
        close();
    }

    /**
     * Stores the current term if the player edited it but did not confirm it yet.
     */
    public void commitPendingEntry() {
        if (!this.commitPending) {
            return;
        }

        this.commitPending = false;
        SearchHistoryStore.record(this.field.getText());
    }

    public boolean keyTyped(int keyCode) {
        // The list may have been opened through the button, which does not focus the text field.
        if (!this.enabled || !this.open || !this.field.getVisible()) {
            return false;
        }

        switch (keyCode) {
            case Keyboard.KEY_UP:
                return moveSelection(-1);
            case Keyboard.KEY_DOWN:
                return moveSelection(1);
            case Keyboard.KEY_W:
                return moveSelectionWithLetter(-1);
            case Keyboard.KEY_S:
                return moveSelectionWithLetter(1);
            case Keyboard.KEY_TAB:
                if (this.rows.isEmpty()) {
                    return false;
                }
                apply(this.selected >= 0 ? this.selected : 0);
                return true;
            case Keyboard.KEY_ESCAPE:
                commitPendingEntry();
                close();
                return true;
            case Keyboard.KEY_RETURN:
            case Keyboard.KEY_NUMPADENTER:
                commitPendingEntry();
                close();
                return true;
            default:
                return false;
        }
    }

    public boolean mouseDown(int mouseX, int mouseY, int button) {
        if (!this.enabled || !this.field.getVisible() || !this.field.isEnabled()) {
            return false;
        }

        Rectangle buttonBounds = getButtonBounds();
        if (button == 0 && buttonBounds.contains(mouseX, mouseY)) {
            if (this.open) {
                close();
            } else {
                openFromButton();
            }
            return true;
        }

        if (!this.open) {
            return false;
        }

        Rectangle popup = getPopupBounds(this.lastGuiBounds);
        if (popup.contains(mouseX, mouseY)) {
            if (button == 0) {
                int index = getRowIndexAt(popup, mouseX, mouseY);
                if (index >= 0) {
                    apply(index);
                    return true;
                }
            }
            close();
            return true;
        }

        close();
        return false;
    }

    public void draw(Rectangle guiBounds, int mouseX, int mouseY) {
        if (!this.enabled || !this.field.getVisible()) {
            this.buttonHovered = false;
            return;
        }

        this.lastGuiBounds = guiBounds;
        this.buttonHovered = getButtonBounds().contains(mouseX, mouseY);

        GlStateManager.pushMatrix();
        try {
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            drawButton(mouseX, mouseY);
            if (this.open && !this.rows.isEmpty()) {
                drawPopup(guiBounds, mouseX, mouseY);
            }
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.popMatrix();
        }
    }

    private boolean moveSelectionWithLetter(int delta) {
        // W and S are regular characters while the player types; they only navigate lists that were opened explicitly.
        return this.openedByButton && moveSelection(delta);
    }

    private boolean moveSelection(int delta) {
        int size = this.rows.size();
        if (size == 0) {
            return false;
        }

        if (this.selected < 0) {
            this.selected = delta > 0 ? 0 : size - 1;
        } else {
            this.selected = Math.floorMod(this.selected + delta, size);
        }
        return true;
    }

    private void apply(int index) {
        if (index < 0 || index >= this.rows.size()) {
            return;
        }

        String value = this.rows.get(index);
        this.applying = true;
        try {
            this.field.setTextFromClient(value);
            this.field.setCursorPositionEnd();
        } finally {
            this.applying = false;
        }
        this.lastText = this.field.getText();
        this.commitPending = false;
        close();
        // Selecting a term through the button leaves the field unfocused, so take focus to allow immediate editing.
        this.field.setFocused(true);
        SearchHistoryStore.record(value);
    }

    private void showSuggestions(String text) {
        if (text.trim().isEmpty()) {
            close();
            return;
        }

        List<String> matches = SearchHistoryStore.filter(text);
        boolean hasMatches = !matches.isEmpty();
        List<String> entries = hasMatches ? matches : SearchHistoryStore.getEntries();
        if (entries.isEmpty()) {
            close();
            return;
        }

        open(entries, false, hasMatches ? 0 : -1);
    }

    private void openFromButton() {
        List<String> entries = SearchHistoryStore.getEntries();
        if (entries.isEmpty()) {
            close();
            return;
        }

        open(entries, true, -1);
    }

    private void open(List<String> entries, boolean openedByButton, int selected) {
        this.rows.clear();
        this.rows.addAll(entries);
        this.open = true;
        this.openedByButton = openedByButton;
        this.selected = selected;
    }

    private void close() {
        this.open = false;
        this.openedByButton = false;
        this.selected = -1;
        this.rows.clear();
    }

    private Rectangle getButtonBounds() {
        Rectangle bounds = this.field.getVisualBounds();
        return new Rectangle(bounds.x - BUTTON_GAP - BUTTON_WIDTH, bounds.y, BUTTON_WIDTH, bounds.height);
    }

    private Rectangle getPopupBounds(@Nullable Rectangle guiBounds) {
        Rectangle fieldBounds = this.field.getVisualBounds();
        Rectangle button = getButtonBounds();
        int width = button.width + BUTTON_GAP + fieldBounds.width;
        int height = this.rows.size() * ROW_HEIGHT + 2 * ROW_PADDING;
        int y = fieldBounds.y + fieldBounds.height + 1;

        if (guiBounds != null && y + height > guiBounds.y + guiBounds.height - 1) {
            int above = fieldBounds.y - height - 1;
            if (above >= guiBounds.y) {
                y = above;
            }
        }

        return new Rectangle(button.x, y, width, height);
    }

    private int getRowIndexAt(Rectangle popup, int mouseX, int mouseY) {
        if (mouseX < popup.x || mouseX >= popup.x + popup.width) {
            return -1;
        }

        int top = popup.y + ROW_PADDING;
        for (int i = 0; i < this.rows.size(); i++) {
            int rowY = top + i * ROW_HEIGHT;
            if (mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                return i;
            }
        }

        return -1;
    }

    private void drawButton(int mouseX, int mouseY) {
        Rectangle bounds = getButtonBounds();
        boolean highlighted = this.field.isEnabled() && (this.open || bounds.contains(mouseX, mouseY));

        AE2Button.renderButtonBackground(bounds.x, bounds.y, bounds.width, bounds.height, this.field.isEnabled(),
            highlighted);
        SmallSquareButtonRenderer.drawIcon(bounds.x, bounds.y, bounds.width, bounds.height, Icon.ARROW_DOWN, 0);
    }

    private void drawPopup(Rectangle guiBounds, int mouseX, int mouseY) {
        Rectangle popup = getPopupBounds(guiBounds);
        Gui.drawRect(popup.x, popup.y, popup.x + popup.width, popup.y + popup.height, POPUP_BACKGROUND);

        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        int textColor = this.field.getStyle().getColor(PaletteColor.TEXTFIELD_TEXT).toARGB();

        for (int i = 0; i < this.rows.size(); i++) {
            int rowY = popup.y + ROW_PADDING + i * ROW_HEIGHT;
            boolean hovered = mouseX >= popup.x + 1 && mouseX < popup.x + popup.width - 1
                && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

            if (i == this.selected) {
                Gui.drawRect(popup.x + 1, rowY, popup.x + popup.width - 1, rowY + ROW_HEIGHT, ROW_SELECTED);
            } else if (hovered) {
                Gui.drawRect(popup.x + 1, rowY, popup.x + popup.width - 1, rowY + ROW_HEIGHT, ROW_HOVER);
            }

            String text = fontRenderer.trimStringToWidth(this.rows.get(i), popup.width - 8);
            fontRenderer.drawString(text, popup.x + 4, rowY + 2, textColor);
        }
    }
}
