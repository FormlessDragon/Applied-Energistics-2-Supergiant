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
import ae2.core.localization.ButtonToolTips;

public enum ButtonType {
    DO_PARTITION(Icon.CELL_TERMINAL_BTN_DO_PARTITION, ButtonToolTips.CellTerminalDoPartition),
    CLEAR_PARTITION(Icon.CELL_TERMINAL_BTN_CLEAR_PARTITION, ButtonToolTips.CellTerminalClearPartition),
    READ_ONLY(Icon.CELL_TERMINAL_BTN_READ_ONLY, ButtonToolTips.CellTerminalReadOnly),
    WRITE_ONLY(Icon.CELL_TERMINAL_BTN_WRITE_ONLY, ButtonToolTips.CellTerminalWriteOnly),
    READ_WRITE(Icon.CELL_TERMINAL_BTN_READ_WRITE, ButtonToolTips.CellTerminalReadWrite);

    private final Icon icon;
    private final ButtonToolTips tooltip;

    ButtonType(Icon icon, ButtonToolTips tooltip) {
        this.icon = icon;
        this.tooltip = tooltip;
    }

    public Icon getIcon(boolean ignoredHovered) {
        return this.icon;
    }

    public String getTooltip() {
        return this.tooltip.getLocal();
    }
}
