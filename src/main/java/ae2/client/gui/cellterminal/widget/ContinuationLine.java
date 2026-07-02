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

public class ContinuationLine extends SlotsLine {
    private boolean drawHorizontalBranch = true;

    public ContinuationLine(int y, int slotsPerRow, int slotsXOffset, SlotMode mode, int startIndex,
                            FontRenderer fontRenderer) {
        super(y, slotsPerRow, slotsXOffset, mode, startIndex, fontRenderer);
        this.isFirstRow = false;
    }

    public void setDrawHorizontalBranch(boolean drawHorizontalBranch) {
        this.drawHorizontalBranch = drawHorizontalBranch;
    }

    public boolean drawsHorizontalBranch() {
        return this.drawHorizontalBranch;
    }

    public void setExtendTreeLineToBottom(boolean extendTreeLineToBottom) {
        super.setExtendTreeLineToBottom(extendTreeLineToBottom);
    }

    @Override
    protected void drawTreeLines(int mouseX, int mouseY) {
        if (!drawTreeLine) {
            return;
        }
        if (!drawHorizontalBranch) {
            int verticalEndY = extendTreeLineToBottom ? y + CellTerminalLayout.ROW_HEIGHT : y + 9;
            if (lineAboveCutY < verticalEndY) {
                Gui.drawRect(TREE_LINE_X, lineAboveCutY, TREE_LINE_X + 1, verticalEndY,
                    CellTerminalLayout.COLOR_TREE_LINE);
            }
            return;
        }
        super.drawTreeLines(mouseX, mouseY);
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        if (drawTopSeparator) {
            Gui.drawRect(CellTerminalLayout.GUI_INDENT, y - 1, CellTerminalLayout.CONTENT_RIGHT_EDGE, y,
                CellTerminalLayout.COLOR_SEPARATOR);
        }
        boolean isSelected = selectedSupplier != null && selectedSupplier.getAsBoolean();
        if (isSelected) {
            Gui.drawRect(CellTerminalLayout.GUI_INDENT, y, CellTerminalLayout.CONTENT_RIGHT_EDGE,
                y + CellTerminalLayout.ROW_HEIGHT, CellTerminalLayout.COLOR_SELECTION);
        }
        drawTreeLines(mouseX, mouseY);
        hoveredSlotIndex = -1;
        hoveredStack = null;
        partitionTargets.clear();
        if (mode == SlotMode.CONTENT) {
            drawContentSlots(mouseX, mouseY);
        } else {
            drawPartitionSlots(mouseX, mouseY);
        }
    }
}
