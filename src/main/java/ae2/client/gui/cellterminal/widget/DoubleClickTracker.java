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

public final class DoubleClickTracker {
    private static long lastClickTargetId = -1;
    private static long lastClickTime = 0;

    private DoubleClickTracker() {
    }

    public static boolean isDoubleClick(long targetId) {
        long currentTime = System.currentTimeMillis();
        if (lastClickTargetId == targetId
            && currentTime - lastClickTime < CellTerminalLayout.DOUBLE_CLICK_TIME_MS) {
            lastClickTargetId = -1;
            lastClickTime = 0;
            return true;
        }
        lastClickTargetId = targetId;
        lastClickTime = currentTime;
        return false;
    }

    public static void reset() {
        lastClickTargetId = -1;
        lastClickTime = 0;
    }
}
