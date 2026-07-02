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

import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.List;

public interface IWidget {
    void draw(int mouseX, int mouseY);

    boolean handleClick(int mouseX, int mouseY, int button);

    default boolean handleKey(char typedChar, int keyCode) {
        return false;
    }

    boolean isHovered(int mouseX, int mouseY);

    default List<String> getTooltip(int mouseX, int mouseY) {
        return Collections.emptyList();
    }

    default ItemStack getHoveredItemStack(int mouseX, int mouseY) {
        return ItemStack.EMPTY;
    }

    int getX();

    int getY();

    int getWidth();

    int getHeight();

    default boolean isVisible() {
        return true;
    }
}
