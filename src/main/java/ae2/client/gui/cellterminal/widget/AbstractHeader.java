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

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public abstract class AbstractHeader extends AbstractWidget {
    protected static final int TREE_LINE_X = CellTerminalLayout.GUI_INDENT + 7;

    protected final FontRenderer fontRenderer;
    protected final RenderItem itemRender;
    protected Supplier<ItemStack> iconSupplier;
    protected Supplier<String> nameSupplier;
    protected BooleanSupplier hasCustomNameSupplier;
    protected boolean drawConnector = false;
    protected int nameMaxWidth = CellTerminalLayout.HEADER_NAME_MAX_WIDTH;
    protected Renameable renameable;
    protected int renameFieldX;
    protected int renameFieldYOffset;
    protected int renameFieldRightEdge;
    protected Runnable onNameDoubleClick;
    protected long doubleClickTargetId = -1;
    protected Runnable onHeaderClick;
    protected BooleanSupplier selectedSupplier;
    protected CardsDisplay cardsDisplay;
    protected boolean nameHovered = false;
    protected boolean headerHovered = false;
    protected boolean drawTopSeparator = true;
    protected boolean suppressTopSeparator = false;

    protected AbstractHeader(int y, FontRenderer fontRenderer, RenderItem itemRender) {
        super(0, y, CellTerminalLayout.CONTENT_RIGHT_EDGE, CellTerminalLayout.ROW_HEIGHT);
        this.fontRenderer = fontRenderer;
        this.itemRender = itemRender;
    }

    public void setIconSupplier(Supplier<ItemStack> supplier) {
        this.iconSupplier = supplier;
    }

    public void setNameSupplier(Supplier<String> supplier) {
        this.nameSupplier = supplier;
    }

    public void setHasCustomNameSupplier(BooleanSupplier supplier) {
        this.hasCustomNameSupplier = supplier;
    }

    public void setDrawConnector(boolean drawConnector) {
        this.drawConnector = drawConnector;
    }

    public void setRenameInfo(Renameable target, int fieldX, int yOffset, int fieldRightEdge) {
        this.renameable = target;
        this.renameFieldX = fieldX;
        this.renameFieldYOffset = yOffset;
        this.renameFieldRightEdge = fieldRightEdge;
    }

    public void setOnNameDoubleClick(Runnable callback, long targetId) {
        this.onNameDoubleClick = callback;
        this.doubleClickTargetId = targetId;
    }

    public void setOnHeaderClick(Runnable callback) {
        this.onHeaderClick = callback;
    }

    public void setSelectedSupplier(BooleanSupplier supplier) {
        this.selectedSupplier = supplier;
    }

    public void setCardsDisplay(CardsDisplay cards) {
        this.cardsDisplay = cards;
    }

    public void setDrawTopSeparator(boolean drawTopSeparator) {
        this.drawTopSeparator = drawTopSeparator;
    }

    public void setSuppressTopSeparator(boolean suppressTopSeparator) {
        this.suppressTopSeparator = suppressTopSeparator;
    }

    @Override
    public boolean isHovered(int mouseX, int mouseY) {
        if (super.isHovered(mouseX, mouseY)) {
            return true;
        }
        return cardsDisplay != null && cardsDisplay.isHovered(mouseX, mouseY);
    }

    public int getConnectorY() {
        return y + CellTerminalLayout.HEADER_CONNECTOR_Y_OFFSET;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        nameHovered = false;
        headerHovered = false;
        if (drawTopSeparator && !suppressTopSeparator) {
            Gui.drawRect(CellTerminalLayout.GUI_INDENT, y - 1, CellTerminalLayout.CONTENT_RIGHT_EDGE, y,
                CellTerminalLayout.COLOR_SEPARATOR);
        }
        boolean isSelected = selectedSupplier != null && selectedSupplier.getAsBoolean();
        if (isSelected) {
            Gui.drawRect(CellTerminalLayout.GUI_INDENT, y, CellTerminalLayout.CONTENT_RIGHT_EDGE,
                y + CellTerminalLayout.ROW_HEIGHT, CellTerminalLayout.COLOR_SELECTION);
        }
        int hoverRightBound = drawHeaderContent(mouseX, mouseY);
        headerHovered = mouseX >= CellTerminalLayout.GUI_INDENT && mouseX < hoverRightBound
            && mouseY >= y && mouseY < y + CellTerminalLayout.ROW_HEIGHT;
        if (headerHovered) {
            Gui.drawRect(CellTerminalLayout.GUI_INDENT, y, hoverRightBound, y + CellTerminalLayout.ROW_HEIGHT,
                CellTerminalLayout.COLOR_STORAGE_HEADER_HOVER);
        }
        drawIcon();
        drawName(mouseX, mouseY);
        if (cardsDisplay != null) {
            cardsDisplay.draw(mouseX, mouseY);
        }
        if (drawConnector) {
            Gui.drawRect(TREE_LINE_X, y + CellTerminalLayout.HEADER_CONNECTOR_Y_OFFSET,
                TREE_LINE_X + 1, y + CellTerminalLayout.ROW_HEIGHT, CellTerminalLayout.COLOR_TREE_LINE);
        }
    }

    protected abstract int drawHeaderContent(int mouseX, int mouseY);

    protected int getHeaderHoverRightBound() {
        return CellTerminalLayout.EXPAND_ICON_X;
    }

    protected boolean isHeaderUnderMouse(int mouseX, int mouseY) {
        return mouseX >= CellTerminalLayout.GUI_INDENT && mouseX < getHeaderHoverRightBound()
            && mouseY >= y && mouseY < y + CellTerminalLayout.ROW_HEIGHT;
    }

    protected boolean isNameUnderMouse(int mouseX, int mouseY) {
        return mouseX >= CellTerminalLayout.HEADER_NAME_X && mouseX < CellTerminalLayout.HEADER_NAME_X + nameMaxWidth
            && mouseY >= y + 1 && mouseY < y + 10;
    }

    protected void drawIcon() {
        ItemStack icon = iconSupplier != null ? iconSupplier.get() : ItemStack.EMPTY;
        if (!icon.isEmpty()) {
            AbstractWidget.renderItemStack(itemRender, icon, CellTerminalLayout.GUI_INDENT, y);
        }
    }

    protected void drawName(int mouseX, int mouseY) {
        String name = nameSupplier != null ? nameSupplier.get() : "";
        if (name.isEmpty()) {
            return;
        }
        String displayName = AbstractWidget.trimTextToWidth(fontRenderer, name, nameMaxWidth);
        boolean isSelected = selectedSupplier != null && selectedSupplier.getAsBoolean();
        int nameColor;
        if (isSelected) {
            nameColor = CellTerminalLayout.COLOR_NAME_SELECTED;
        } else if (hasCustomNameSupplier != null && hasCustomNameSupplier.getAsBoolean()) {
            nameColor = CellTerminalLayout.COLOR_CUSTOM_NAME;
        } else {
            nameColor = CellTerminalLayout.COLOR_TEXT_NORMAL;
        }
        fontRenderer.drawString(displayName, CellTerminalLayout.HEADER_NAME_X, y + 1, nameColor);
        if (isNameUnderMouse(mouseX, mouseY)) {
            nameHovered = true;
        }
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible) {
            return false;
        }
        if (button == 1 && isNameUnderMouse(mouseX, mouseY) && renameable != null && renameable.isRenameable()) {
            InlineRenameManager.getInstance().startEditing(
                renameable, y + renameFieldYOffset, renameFieldX, renameFieldRightEdge);
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (cardsDisplay != null && cardsDisplay.isHovered(mouseX, mouseY)) {
            return cardsDisplay.handleClick(mouseX, mouseY, button);
        }
        boolean headerUnderMouse = isHeaderUnderMouse(mouseX, mouseY);
        if (headerUnderMouse && onNameDoubleClick != null && doubleClickTargetId != -1) {
            if (DoubleClickTracker.isDoubleClick(doubleClickTargetId)) {
                onNameDoubleClick.run();
                return true;
            }
        }
        if (headerUnderMouse && onHeaderClick != null) {
            onHeaderClick.run();
            return true;
        }
        return false;
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY)) {
            return Collections.emptyList();
        }
        if (cardsDisplay != null && cardsDisplay.isHovered(mouseX, mouseY)) {
            return cardsDisplay.getTooltip(mouseX, mouseY);
        }
        return Collections.emptyList();
    }

    @Override
    public ItemStack getHoveredItemStack(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY)) {
            return ItemStack.EMPTY;
        }
        int iconX = CellTerminalLayout.GUI_INDENT;
        if (mouseX >= iconX && mouseX < iconX + CellTerminalLayout.MINI_SLOT_SIZE
            && mouseY >= y && mouseY < y + CellTerminalLayout.MINI_SLOT_SIZE) {
            ItemStack icon = iconSupplier != null ? iconSupplier.get() : ItemStack.EMPTY;
            if (!icon.isEmpty()) {
                return icon;
            }
        }
        return ItemStack.EMPTY;
    }

    protected String trimTextToWidth(String text, int maxWidth) {
        return AbstractWidget.trimTextToWidth(fontRenderer, text, maxWidth);
    }
}
