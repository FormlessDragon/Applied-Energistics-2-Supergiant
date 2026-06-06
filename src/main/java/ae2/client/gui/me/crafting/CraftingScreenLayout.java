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

package ae2.client.gui.me.crafting;

import ae2.api.config.TerminalStyle;
import ae2.core.AEConfig;

final class CraftingScreenLayout {

    static final int WIDTH = 238;
    static final int TABLE_TOP = 19;
    static final int TABLE_ROW_HEIGHT = 23;
    static final int MIN_ROWS = 2;

    private CraftingScreenLayout() {
    }

    static int getRows(int screenHeight, int fixedHeader, int fixedFooter) {
        int availableHeight = screenHeight - 2 * AEConfig.instance().getTerminalMargin();
        int fullRows = (availableHeight - fixedHeader - fixedFooter) / TABLE_ROW_HEIGHT;
        fullRows = Math.max(1, fullRows);

        int minRows = Math.min(MIN_ROWS, fullRows);
        TerminalStyle style = AEConfig.instance().getTerminalStyle();
        if (style == TerminalStyle.SMALL) {
            return minRows;
        }

        int extraRows = Math.max(0, fullRows - minRows);
        return switch (style) {
            case MEDIUM -> minRows + (extraRows + 2) / 3;
            case TALL -> minRows + (extraRows * 2 + 2) / 3;
            case FULL -> fullRows;
            default -> minRows;
        };
    }

    static int getHeight(int rows, int fixedHeader, int fixedFooter) {
        return fixedHeader + rows * TABLE_ROW_HEIGHT + fixedFooter;
    }

    static int getScrollbarHeight(int rows) {
        return Math.max(1, rows * TABLE_ROW_HEIGHT - 1);
    }
}
