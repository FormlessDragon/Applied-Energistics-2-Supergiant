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

public class CellSlotsLine extends SlotsLine {
    private final RenderItem itemRender;
    private Supplier<ItemStack> cellItemSupplier;
    private BooleanSupplier cellFilledSupplier;
    private CardsDisplay cardsDisplay;
    private CellSlotClickCallback cellSlotCallback;
    private boolean cellSlotHovered;

    public CellSlotsLine(int y, int slotsPerRow, int slotsXOffset, SlotMode mode, int startIndex,
                         FontRenderer fontRenderer, RenderItem itemRender) {
        super(y, slotsPerRow, slotsXOffset, mode, startIndex, fontRenderer);
        this.itemRender = itemRender;
    }

    public void setCellItemSupplier(Supplier<ItemStack> supplier) {
        this.cellItemSupplier = supplier;
    }

    public void setCellFilledSupplier(BooleanSupplier supplier) {
        this.cellFilledSupplier = supplier;
    }

    public void setCardsDisplay(CardsDisplay cards) {
        this.cardsDisplay = cards;
    }

    public void setCellSlotCallback(CellSlotClickCallback callback) {
        this.cellSlotCallback = callback;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        boolean isSelected = selectedSupplier != null && selectedSupplier.getAsBoolean();
        if (isSelected) {
            Gui.drawRect(CellTerminalLayout.GUI_INDENT, y, CellTerminalLayout.CONTENT_RIGHT_EDGE,
                y + CellTerminalLayout.ROW_HEIGHT, CellTerminalLayout.COLOR_SELECTION);
        }
        drawTreeLines(mouseX, mouseY);
        cellSlotHovered = false;
        drawCellSlot(mouseX, mouseY);
        if (cardsDisplay != null) {
            cardsDisplay.draw(mouseX, mouseY);
        }
        boolean cellFilled = cellFilledSupplier != null && cellFilledSupplier.getAsBoolean();
        if (!cellFilled) {
            return;
        }
        hoveredSlotIndex = -1;
        hoveredStack = null;
        partitionTargets.clear();
        if (mode == SlotMode.CONTENT) {
            drawContentSlots(mouseX, mouseY);
        } else {
            drawPartitionSlots(mouseX, mouseY);
        }
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (treeButton != null && treeButton.isVisible() && treeButton.isHovered(mouseX, mouseY)) {
            return treeButton.handleClick(mouseX, mouseY, button);
        }
        if (cardsDisplay != null && cardsDisplay.isHovered(mouseX, mouseY)) {
            return cardsDisplay.handleClick(mouseX, mouseY, button);
        }
        if (cellSlotHovered && cellSlotCallback != null && button == 0) {
            cellSlotCallback.onCellSlotClicked(button);
            return true;
        }
        if (hoveredSlotIndex >= 0 && slotClickCallback != null && button == 0) {
            slotClickCallback.onSlotClicked(hoveredSlotIndex, button);
            return true;
        }
        return false;
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY)) {
            return Collections.emptyList();
        }
        if (treeButton != null && treeButton.isHovered(mouseX, mouseY)) {
            return treeButton.getTooltip(mouseX, mouseY);
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
        if (cellSlotHovered) {
            ItemStack cellItem = cellItemSupplier != null ? cellItemSupplier.get() : ItemStack.EMPTY;
            if (!cellItem.isEmpty()) {
                return cellItem;
            }
        }
        return hoveredStack != null ? hoveredStack.what().wrapForDisplayOrFilter() : ItemStack.EMPTY;
    }

    private void drawCellSlot(int mouseX, int mouseY) {
        int cellX = CellTerminalLayout.CELL_INDENT;
        drawSlotBackground(cellX, y, false);
        ItemStack cellItem = cellItemSupplier != null ? cellItemSupplier.get() : ItemStack.EMPTY;
        if (!cellItem.isEmpty()) {
            AbstractWidget.renderItemStack(itemRender, cellItem, cellX, y);
        }
        if (mouseX >= cellX && mouseX < cellX + CellTerminalLayout.MINI_SLOT_SIZE
            && mouseY >= y && mouseY < y + CellTerminalLayout.MINI_SLOT_SIZE) {
            drawSlotHoverHighlight(cellX, y);
            cellSlotHovered = true;
        }
    }

    @FunctionalInterface
    public interface CellSlotClickCallback {
        void onCellSlotClicked(int mouseButton);
    }
}
