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

import ae2.client.gui.Icon;
import ae2.client.gui.widgets.AE2Button;
import ae2.core.localization.GuiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

public class TempAreaHeader extends AbstractHeader {
    public static final int SEND_BUTTON_X = 150;
    private static final int SEND_BUTTON_Y_OFFSET = 1;
    private static final int SEND_BUTTON_WIDTH = 28;
    private static final int SEND_BUTTON_HEIGHT = 16;
    private static final int MAX_NAME_WIDTH = SEND_BUTTON_X - CellTerminalLayout.HEADER_NAME_X - 4;
    private static final int SIZE = CellTerminalLayout.MINI_SLOT_SIZE;
    private BooleanSupplier hasCellSupplier;
    private CellSlotClickCallback cellSlotCallback;
    private Runnable onSendClick;

    public TempAreaHeader(int y, FontRenderer fontRenderer, RenderItem itemRender) {
        super(y, fontRenderer, itemRender);
        this.nameMaxWidth = MAX_NAME_WIDTH;
    }

    public void setHasCellSupplier(BooleanSupplier supplier) {
        this.hasCellSupplier = supplier;
    }

    public void setCellSlotCallback(CellSlotClickCallback callback) {
        this.cellSlotCallback = callback;
    }

    public void setOnSendClick(Runnable callback) {
        this.onSendClick = callback;
    }

    @Override
    protected int drawHeaderContent(int mouseX, int mouseY) {
        if (cardsDisplay != null) {
            cardsDisplay.draw(mouseX, mouseY);
        }
        boolean hasCell = hasCellSupplier != null && hasCellSupplier.getAsBoolean();
        if (hasCell) {
            drawSendButton(mouseX, mouseY);
        }
        return hasCell ? SEND_BUTTON_X : CellTerminalLayout.CONTENT_RIGHT_EDGE;
    }

    @Override
    protected int getHeaderHoverRightBound() {
        boolean hasCell = hasCellSupplier != null && hasCellSupplier.getAsBoolean();
        return hasCell ? SEND_BUTTON_X : CellTerminalLayout.CONTENT_RIGHT_EDGE;
    }

    @Override
    protected void drawIcon() {
        int slotX = CellTerminalLayout.GUI_INDENT;
        Icon.CELL_TERMINAL_MINI_SLOT.getBlitter().copy().dest(slotX, y, SIZE, SIZE).blit();
        ItemStack icon = iconSupplier != null ? iconSupplier.get() : ItemStack.EMPTY;
        if (!icon.isEmpty()) {
            AbstractWidget.renderItemStack(itemRender, icon, slotX, y);
        }
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
        checkCellSlotHover(mouseX, mouseY);
        drawTempName(mouseX, mouseY);
        if (drawConnector) {
            Gui.drawRect(TREE_LINE_X, y + CellTerminalLayout.HEADER_CONNECTOR_Y_OFFSET,
                TREE_LINE_X + 1, y + CellTerminalLayout.ROW_HEIGHT, CellTerminalLayout.COLOR_TREE_LINE);
        }
    }

    private void checkCellSlotHover(int mouseX, int mouseY) {
        int slotX = CellTerminalLayout.GUI_INDENT;
        if (isCellSlotUnderMouse(mouseX, mouseY)) {
            Gui.drawRect(slotX, y, slotX + SIZE, y + SIZE, CellTerminalLayout.COLOR_HOVER_HIGHLIGHT);
        }
    }

    private void drawTempName(int mouseX, int mouseY) {
        boolean hasCell = hasCellSupplier != null && hasCellSupplier.getAsBoolean();
        String name = hasCell
            ? (nameSupplier != null ? nameSupplier.get() : "")
            : GuiText.CellTerminalTempAreaDropCell.getLocal();
        if (name.isEmpty()) {
            return;
        }
        String displayName = trimTextToWidth(name, nameMaxWidth);
        boolean isSelected = selectedSupplier != null && selectedSupplier.getAsBoolean();
        boolean hasCustomName = hasCustomNameSupplier != null && hasCustomNameSupplier.getAsBoolean();
        int nameColor;
        if (isSelected) {
            nameColor = CellTerminalLayout.COLOR_NAME_SELECTED;
        } else if (!hasCell) {
            nameColor = CellTerminalLayout.COLOR_TEXT_PLACEHOLDER;
        } else if (hasCustomName) {
            nameColor = CellTerminalLayout.COLOR_CUSTOM_NAME;
        } else {
            nameColor = CellTerminalLayout.COLOR_TEXT_NORMAL;
        }
        fontRenderer.drawString(displayName, CellTerminalLayout.HEADER_NAME_X, y + 5, nameColor);
        if (!hasCell) {
            return;
        }
        if (isNameUnderMouse(mouseX, mouseY)) {
            nameHovered = true;
        }
    }

    @Override
    protected boolean isNameUnderMouse(int mouseX, int mouseY) {
        return mouseX >= CellTerminalLayout.HEADER_NAME_X && mouseX < CellTerminalLayout.HEADER_NAME_X + nameMaxWidth
            && mouseY >= y + 5 && mouseY < y + 14;
    }

    private void drawSendButton(int mouseX, int mouseY) {
        int btnY = y + SEND_BUTTON_Y_OFFSET;
        AE2Button button = new AE2Button(SEND_BUTTON_X, btnY, SEND_BUTTON_WIDTH, SEND_BUTTON_HEIGHT,
            GuiText.CellTerminalTempAreaSend.text(), null);
        button.drawButton(Minecraft.getMinecraft(), mouseX, mouseY, 0.0F);
    }

    private boolean isCellSlotUnderMouse(int mouseX, int mouseY) {
        int slotX = CellTerminalLayout.GUI_INDENT;
        return mouseX >= slotX && mouseX < slotX + SIZE && mouseY >= y && mouseY < y + SIZE;
    }

    private boolean isSendButtonUnderMouse(int mouseX, int mouseY) {
        boolean hasCell = hasCellSupplier != null && hasCellSupplier.getAsBoolean();
        int btnX = SEND_BUTTON_X;
        int btnY = y + SEND_BUTTON_Y_OFFSET;
        return hasCell && mouseX >= btnX && mouseX < btnX + SEND_BUTTON_WIDTH
            && mouseY >= btnY && mouseY < btnY + SEND_BUTTON_HEIGHT;
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible) {
            return false;
        }
        if (button == 0 && isSendButtonUnderMouse(mouseX, mouseY) && onSendClick != null) {
            onSendClick.run();
            return true;
        }
        if (isCellSlotUnderMouse(mouseX, mouseY) && cellSlotCallback != null) {
            cellSlotCallback.onCellSlotClicked(button);
            return true;
        }
        return super.handleClick(mouseX, mouseY, button);
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY)) {
            return Collections.emptyList();
        }
        if (isSendButtonUnderMouse(mouseX, mouseY)) {
            return Collections.singletonList(GuiText.CellTerminalTempAreaSendTooltip.getLocal());
        }
        return super.getTooltip(mouseX, mouseY);
    }

    @Override
    public ItemStack getHoveredItemStack(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY)) {
            return ItemStack.EMPTY;
        }
        if (isCellSlotUnderMouse(mouseX, mouseY)) {
            ItemStack icon = iconSupplier != null ? iconSupplier.get() : ItemStack.EMPTY;
            if (!icon.isEmpty()) {
                return icon;
            }
        }
        return ItemStack.EMPTY;
    }

    @FunctionalInterface
    public interface CellSlotClickCallback {
        void onCellSlotClicked(int mouseButton);
    }
}
