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

public final class CellTerminalLayout {
    public static final int GUI_INDENT = 21;
    public static final int CELL_INDENT = GUI_INDENT + 12;
    public static final int CONTENT_RIGHT_EDGE = 184;
    public static final int CONTENT_START_Y = 19;
    public static final int ROW_HEIGHT = 18;
    public static final int MINI_SLOT_SIZE = 16;
    public static final int SMALL_BUTTON_SIZE = 8;
    public static final int TAB1_BUTTON_SIZE = 12;
    public static final int CELL_SLOTS_PER_ROW = 8;
    public static final int STORAGE_BUS_SLOTS_PER_ROW = 9;
    public static final int MAX_STORAGE_BUS_PARTITION_SLOTS = 63;
    public static final int BUTTON_EJECT_X = 141;
    public static final int BUTTON_INVENTORY_X = 154;
    public static final int BUTTON_PARTITION_X = 167;
    public static final int BUTTON_IO_MODE_X = 120;
    public static final int CARDS_X = 3;
    public static final int CELL_NAME_X_OFFSET = 18;
    public static final int USAGE_BAR_WIDTH = BUTTON_EJECT_X - CELL_INDENT - CELL_NAME_X_OFFSET - 4;
    public static final int USAGE_BAR_HEIGHT = 4;
    public static final int HEADER_NAME_X = GUI_INDENT + 20;
    public static final int HEADER_NAME_MAX_WIDTH = BUTTON_IO_MODE_X - HEADER_NAME_X - 4;
    public static final int HEADER_LOCATION_MAX_WIDTH = CONTENT_RIGHT_EDGE - HEADER_NAME_X;
    public static final int EXPAND_ICON_X = 167;
    public static final int HEADER_CONNECTOR_Y_OFFSET = ROW_HEIGHT - 3;
    public static final int COLOR_HOVER_HIGHLIGHT = 0x80FFFFFF;
    public static final int COLOR_STORAGE_HEADER_HOVER = 0x30FFFFFF;
    public static final int COLOR_SEPARATOR = 0xFF606060;
    public static final int COLOR_TREE_LINE = 0xFF808080;
    public static final int COLOR_PARTITION_INDICATOR = 0xFF00C853;
    public static final int COLOR_SELECTION = 0x405599DD;
    public static final int COLOR_NAME_SELECTED = 0x204080;
    public static final int COLOR_CUSTOM_NAME = 0xFF2E7D32;
    public static final int COLOR_USAGE_BAR_BACKGROUND = 0xFF555555;
    public static final int COLOR_USAGE_LOW = 0xFF33FF33;
    public static final int COLOR_USAGE_MEDIUM = 0xFFFFAA00;
    public static final int COLOR_USAGE_HIGH = 0xFFFF3333;
    public static final int COLOR_TEXT_NORMAL = 0x404040;
    public static final int COLOR_TEXT_SECONDARY = 0x808080;
    public static final int COLOR_TEXT_PLACEHOLDER = 0x606060;
    public static final int COLOR_MAIN_NETWORK = 0xFF005A66;
    public static final long DOUBLE_CLICK_TIME_MS = 400;

    private CellTerminalLayout() {
    }
}
