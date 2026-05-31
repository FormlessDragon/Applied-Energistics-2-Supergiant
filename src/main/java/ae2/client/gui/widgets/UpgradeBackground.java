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

package ae2.client.gui.widgets;

import ae2.client.gui.style.Blitter;

final class UpgradeBackground {
    private static final String TEXTURE = "guis/icons.png";
    private static final int TEXTURE_SIZE = 128;

    private static final UpgradeBackground FIXED = new UpgradeBackground(
        23,
        Blitter.texture(TEXTURE, TEXTURE_SIZE, TEXTURE_SIZE).src(77, 62, 23, 23),
        Blitter.texture(TEXTURE, TEXTURE_SIZE, TEXTURE_SIZE).src(77, 85, 23, 18),
        Blitter.texture(TEXTURE, TEXTURE_SIZE, TEXTURE_SIZE).src(77, 103, 23, 25));

    private static final UpgradeBackground SCROLLING = new UpgradeBackground(
        33,
        Blitter.texture(TEXTURE, TEXTURE_SIZE, TEXTURE_SIZE).src(48, 62, 33, 23),
        Blitter.texture(TEXTURE, TEXTURE_SIZE, TEXTURE_SIZE).src(48, 85, 33, 18),
        Blitter.texture(TEXTURE, TEXTURE_SIZE, TEXTURE_SIZE).src(48, 103, 33, 25));

    private final int width;
    private final Blitter top;
    private final Blitter middle;
    private final Blitter bottom;

    private UpgradeBackground(int width, Blitter top, Blitter middle, Blitter bottom) {
        this.width = width;
        this.top = top;
        this.middle = middle;
        this.bottom = bottom;
    }

    public static UpgradeBackground get(boolean scrolling) {
        return scrolling ? SCROLLING : FIXED;
    }

    public int width() {
        return this.width;
    }

    public Blitter top() {
        return this.top;
    }

    public Blitter middle() {
        return this.middle;
    }

    public Blitter bottom() {
        return this.bottom;
    }
}
