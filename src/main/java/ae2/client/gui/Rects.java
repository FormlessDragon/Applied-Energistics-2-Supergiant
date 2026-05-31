/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
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

package ae2.client.gui;

import java.awt.Rectangle;

/**
 * Utility class for dealing with immutable {@link Rectangle}.
 */
public final class Rects {

    public static final Rectangle ZERO = new Rectangle();

    private Rects() {
    }

    public static Rectangle expand(Rectangle rect, int amount) {
        return new Rectangle(
            rect.x - amount,
            rect.y - amount,
            rect.width + 2 * amount,
            rect.height + 2 * amount);
    }

    public static Rectangle move(Rectangle rect, int x, int y) {
        return new Rectangle(
            rect.x + x,
            rect.y + y,
            rect.width,
            rect.height);
    }
}

