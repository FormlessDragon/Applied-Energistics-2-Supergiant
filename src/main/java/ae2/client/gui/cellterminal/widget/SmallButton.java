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

import java.util.Collections;
import java.util.List;

public class SmallButton extends AbstractWidget {
    private static final int SIZE = 8;
    private final Runnable onClick;
    protected ButtonType type;

    public SmallButton(int x, int y, ButtonType type, Runnable onClick) {
        super(x, y, SIZE, SIZE);
        this.type = type;
        this.onClick = onClick;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        boolean hovered = isHovered(mouseX, mouseY);
        this.type.getIcon(hovered).getBlitter().copy().dest(this.x, this.y, SIZE, SIZE).blit();
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible || button != 0 || !isHovered(mouseX, mouseY)) {
            return false;
        }
        onClick.run();
        return true;
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY)) {
            return Collections.emptyList();
        }
        return Collections.singletonList(this.type.getTooltip());
    }

    public ButtonType getType() {
        return this.type;
    }

    public void setType(ButtonType type) {
        this.type = type;
    }
}
