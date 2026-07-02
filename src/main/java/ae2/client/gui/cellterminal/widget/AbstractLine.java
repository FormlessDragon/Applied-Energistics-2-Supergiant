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

import net.minecraft.client.gui.Gui;

import java.util.Collections;
import java.util.List;

public abstract class AbstractLine extends AbstractWidget {
    protected static final int TREE_LINE_X = CellTerminalLayout.GUI_INDENT + 7;
    protected static final int TREE_BRANCH_WIDTH = 10;

    protected boolean drawTreeLine = true;
    protected int lineAboveCutY = CellTerminalLayout.CONTENT_START_Y;
    protected boolean extendTreeLineToBottom;
    protected boolean isFirstRow = true;
    protected SmallButton treeButton;
    protected int treeButtonXOffset = -5;

    protected AbstractLine(int x, int y, int width) {
        super(x, y, width, CellTerminalLayout.ROW_HEIGHT);
    }

    public void setTreeLineParams(boolean drawTreeLine, int lineAboveCutY) {
        this.drawTreeLine = drawTreeLine;
        this.lineAboveCutY = lineAboveCutY;
    }

    public void setExtendTreeLineToBottom(boolean extendTreeLineToBottom) {
        this.extendTreeLineToBottom = extendTreeLineToBottom;
    }

    public int getTreeLineCutY() {
        if (treeButton != null) {
            return y + 5 + CellTerminalLayout.SMALL_BUTTON_SIZE;
        }
        return y + 9;
    }

    public void setTreeButtonXOffset(int offset) {
        this.treeButtonXOffset = offset;
    }

    public SmallButton getTreeButton() {
        return treeButton;
    }

    public void setTreeButton(SmallButton button) {
        this.treeButton = button;
    }

    protected void drawTreeLines(int mouseX, int mouseY) {
        if (!drawTreeLine) {
            return;
        }
        int branchY = y + 8;
        int verticalEndY = (treeButton != null) ? y + 5 : branchY;
        if (lineAboveCutY < verticalEndY) {
            Gui.drawRect(TREE_LINE_X, lineAboveCutY, TREE_LINE_X + 1, verticalEndY, CellTerminalLayout.COLOR_TREE_LINE);
        }
        Gui.drawRect(TREE_LINE_X, branchY, TREE_LINE_X + TREE_BRANCH_WIDTH, branchY + 1,
            CellTerminalLayout.COLOR_TREE_LINE);
        if (extendTreeLineToBottom) {
            int bottomStartY = getTreeLineCutY();
            int bottomEndY = y + CellTerminalLayout.ROW_HEIGHT;
            if (bottomStartY < bottomEndY) {
                Gui.drawRect(TREE_LINE_X, bottomStartY, TREE_LINE_X + 1, bottomEndY,
                    CellTerminalLayout.COLOR_TREE_LINE);
            }
        }
        if (treeButton != null) {
            int buttonX = TREE_LINE_X + treeButtonXOffset;
            int buttonY = y + 5;
            treeButton.setPosition(buttonX, buttonY);
            treeButton.draw(mouseX, mouseY);
        }
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible) {
            return false;
        }
        if (treeButton != null && treeButton.isVisible() && treeButton.isHovered(mouseX, mouseY)) {
            return treeButton.handleClick(mouseX, mouseY, button);
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
        return Collections.emptyList();
    }
}
