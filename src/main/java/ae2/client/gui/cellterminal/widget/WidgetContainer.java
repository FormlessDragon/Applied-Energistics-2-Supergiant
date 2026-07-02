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

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.List;

public class WidgetContainer extends AbstractWidget {
    protected final List<IWidget> children = new ObjectArrayList<>();

    public WidgetContainer(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public void addChild(IWidget child) {
        children.add(child);
    }

    public void removeChild(IWidget child) {
        children.remove(child);
    }

    public void clearChildren() {
        children.clear();
    }

    public List<IWidget> getChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        for (IWidget child : children) {
            if (child.isVisible()) {
                child.draw(mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible) {
            return false;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            IWidget child = children.get(i);
            if (child.isVisible() && child.isHovered(mouseX, mouseY)) {
                if (child.handleClick(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean handleKey(char typedChar, int keyCode) {
        if (!visible) {
            return false;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            IWidget child = children.get(i);
            if (child.isVisible() && child.handleKey(typedChar, keyCode)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        if (!visible) {
            return Collections.emptyList();
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            IWidget child = children.get(i);
            if (!child.isVisible() || !child.isHovered(mouseX, mouseY)) {
                continue;
            }
            List<String> tooltip = child.getTooltip(mouseX, mouseY);
            if (!tooltip.isEmpty()) {
                return tooltip;
            }
        }
        return Collections.emptyList();
    }

    @Override
    public ItemStack getHoveredItemStack(int mouseX, int mouseY) {
        if (!visible) {
            return ItemStack.EMPTY;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            IWidget child = children.get(i);
            if (!child.isVisible() || !child.isHovered(mouseX, mouseY)) {
                continue;
            }
            ItemStack stack = child.getHoveredItemStack(mouseX, mouseY);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
