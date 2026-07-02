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
import ae2.core.localization.GuiText;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public class TerminalLine extends AbstractLine {
    public static final int HOVER_NONE = 0;
    public static final int HOVER_INVENTORY = 1;
    public static final int HOVER_PARTITION = 2;
    public static final int HOVER_EJECT = 3;

    private static final int SIZE = CellTerminalLayout.TAB1_BUTTON_SIZE;
    private static final int CELL_NAME_MAX_PIXEL_WIDTH =
        CellTerminalLayout.BUTTON_EJECT_X - (CellTerminalLayout.CELL_INDENT + CellTerminalLayout.CELL_NAME_X_OFFSET) - 4;
    private final FontRenderer fontRenderer;
    private final RenderItem itemRender;
    private Supplier<ItemStack> cellItemSupplier;
    private Supplier<String> cellNameSupplier;
    private BooleanSupplier hasCustomNameSupplier;
    private DoubleSupplier byteUsageSupplier;
    private CardsDisplay cardsDisplay;
    private TerminalLineCallback callback;
    private int hoveredButton = HOVER_NONE;

    public TerminalLine(int y, FontRenderer fontRenderer, RenderItem itemRender) {
        super(0, y, CellTerminalLayout.CONTENT_RIGHT_EDGE);
        this.fontRenderer = fontRenderer;
        this.itemRender = itemRender;
    }

    public void setCellItemSupplier(Supplier<ItemStack> supplier) {
        this.cellItemSupplier = supplier;
    }

    public void setCellNameSupplier(Supplier<String> supplier) {
        this.cellNameSupplier = supplier;
    }

    public void setHasCustomNameSupplier(BooleanSupplier supplier) {
        this.hasCustomNameSupplier = supplier;
    }

    public void setByteUsageSupplier(DoubleSupplier supplier) {
        this.byteUsageSupplier = supplier;
    }

    public void setCardsDisplay(CardsDisplay cards) {
        this.cardsDisplay = cards;
    }

    public void setCallback(TerminalLineCallback callback) {
        this.callback = callback;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        hoveredButton = HOVER_NONE;
        drawTreeLines(mouseX, mouseY);
        if (cardsDisplay != null) {
            cardsDisplay.draw(mouseX, mouseY);
        }
        ItemStack cellItem = cellItemSupplier != null ? cellItemSupplier.get() : ItemStack.EMPTY;
        if (!cellItem.isEmpty()) {
            AbstractWidget.renderItemStack(itemRender, cellItem, CellTerminalLayout.CELL_INDENT, y);
        }
        drawCellName();
        drawUsageBar();
        drawActionButtons(mouseX, mouseY);
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible) {
            return false;
        }
        if (button == 0 && cardsDisplay != null && cardsDisplay.isHovered(mouseX, mouseY)) {
            return cardsDisplay.handleClick(mouseX, mouseY, button);
        }
        if (button == 0 && callback != null && hoveredButton != HOVER_NONE) {
            callback.onAction(hoveredButton);
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
        if (isButtonHovered(mouseX, mouseY, CellTerminalLayout.BUTTON_EJECT_X)) {
            return Collections.singletonList(GuiText.CellTerminalActionEject.getLocal());
        }
        if (isButtonHovered(mouseX, mouseY, CellTerminalLayout.BUTTON_INVENTORY_X)) {
            return Collections.singletonList(GuiText.CellTerminalActionInventory.getLocal());
        }
        if (isButtonHovered(mouseX, mouseY, CellTerminalLayout.BUTTON_PARTITION_X)) {
            return Collections.singletonList(GuiText.CellTerminalActionPartition.getLocal());
        }
        return Collections.emptyList();
    }

    @Override
    public ItemStack getHoveredItemStack(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY)) {
            return ItemStack.EMPTY;
        }
        int cellX = CellTerminalLayout.CELL_INDENT;
        if (mouseX >= cellX && mouseX < cellX + CellTerminalLayout.MINI_SLOT_SIZE
            && mouseY >= y && mouseY < y + CellTerminalLayout.MINI_SLOT_SIZE) {
            ItemStack cellItem = cellItemSupplier != null ? cellItemSupplier.get() : ItemStack.EMPTY;
            if (!cellItem.isEmpty()) {
                return cellItem;
            }
        }
        return ItemStack.EMPTY;
    }

    private void drawCellName() {
        String name = cellNameSupplier != null ? cellNameSupplier.get() : "";
        if (name.isEmpty()) {
            return;
        }
        name = AbstractWidget.trimTextToWidth(fontRenderer, name, CELL_NAME_MAX_PIXEL_WIDTH);
        boolean hasCustomName = hasCustomNameSupplier != null && hasCustomNameSupplier.getAsBoolean();
        int nameColor = hasCustomName ? CellTerminalLayout.COLOR_CUSTOM_NAME : CellTerminalLayout.COLOR_TEXT_NORMAL;
        int nameX = CellTerminalLayout.CELL_INDENT + CellTerminalLayout.CELL_NAME_X_OFFSET;
        fontRenderer.drawString(name, nameX, y + 1, nameColor);
    }

    private void drawUsageBar() {
        float usage = byteUsageSupplier != null ? (float) byteUsageSupplier.getAsDouble() : 0f;
        int barX = CellTerminalLayout.CELL_INDENT + CellTerminalLayout.CELL_NAME_X_OFFSET;
        int barY = y + 10;
        Gui.drawRect(barX, barY, barX + CellTerminalLayout.USAGE_BAR_WIDTH, barY + CellTerminalLayout.USAGE_BAR_HEIGHT,
            CellTerminalLayout.COLOR_USAGE_BAR_BACKGROUND);
        int filledWidth = (int) (CellTerminalLayout.USAGE_BAR_WIDTH * usage);
        if (filledWidth > 0) {
            Gui.drawRect(barX, barY, barX + filledWidth, barY + CellTerminalLayout.USAGE_BAR_HEIGHT,
                getUsageColor(usage));
        }
    }

    private void drawActionButtons(int mouseX, int mouseY) {
        boolean ejectHovered = isButtonHovered(mouseX, mouseY, CellTerminalLayout.BUTTON_EJECT_X);
        boolean invHovered = isButtonHovered(mouseX, mouseY, CellTerminalLayout.BUTTON_INVENTORY_X);
        boolean partHovered = isButtonHovered(mouseX, mouseY, CellTerminalLayout.BUTTON_PARTITION_X);
        drawIcon(CellTerminalLayout.BUTTON_EJECT_X, ejectHovered ? Icon.CELL_TERMINAL_ACT_EJECT_HOVER
            : Icon.CELL_TERMINAL_ACT_EJECT);
        drawIcon(CellTerminalLayout.BUTTON_INVENTORY_X, Icon.CELL_TERMINAL_ACT_INVENTORY);
        drawIcon(CellTerminalLayout.BUTTON_PARTITION_X, Icon.CELL_TERMINAL_ACT_PARTITION);
        if (ejectHovered) {
            hoveredButton = HOVER_EJECT;
        } else if (invHovered) {
            hoveredButton = HOVER_INVENTORY;
        } else if (partHovered) {
            hoveredButton = HOVER_PARTITION;
        }
    }

    private void drawIcon(int drawX, Icon icon) {
        icon.getBlitter().copy().dest(drawX, y + 1, SIZE, SIZE).blit();
    }

    private boolean isButtonHovered(int mouseX, int mouseY, int buttonX) {
        return mouseX >= buttonX && mouseX < buttonX + SIZE && mouseY >= y + 1 && mouseY < y + 1 + SIZE;
    }

    private int getUsageColor(float percent) {
        if (percent > 0.9f) {
            return CellTerminalLayout.COLOR_USAGE_HIGH;
        }
        if (percent > 0.75f) {
            return CellTerminalLayout.COLOR_USAGE_MEDIUM;
        }
        return CellTerminalLayout.COLOR_USAGE_LOW;
    }

    @FunctionalInterface
    public interface TerminalLineCallback {
        void onAction(int hoverType);
    }
}
