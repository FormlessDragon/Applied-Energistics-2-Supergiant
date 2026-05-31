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

import ae2.client.Point;
import ae2.client.gui.ICompositeWidget;
import ae2.client.gui.style.Blitter;

import java.awt.Rectangle;

public class BackgroundPanel implements ICompositeWidget {
    private final Blitter background;

    private int x;
    private int y;

    public BackgroundPanel(Blitter background) {
        this.background = background;
    }

    @Override
    public void setPosition(Point position) {
        x = position.x();
        y = position.y();
    }

    @Override
    public void setSize(int width, int height) {
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(x, y, background.getSrcWidth(), background.getSrcHeight());
    }

    @Override
    public boolean hitTest(Point mousePos) {
        return false;
    }

    @Override
    public void drawBackgroundLayer(Rectangle bounds, Point mouse) {
        background.dest(bounds.x + x, bounds.y + y).blit();
    }
}
