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

import ae2.client.gui.Icon;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ITextComponent;

import java.util.List;

/**
 * Displays a small icon that shows validation errors for some input control.
 */
public class ValidationIcon extends IconButton {

    private final List<ITextComponent> tooltip = new ObjectArrayList<>();

    public ValidationIcon() {
        super(() -> {
        });
        setDisableBackground(true);
        setDisableClickSound(true);
        setHalfSize(true);
    }

    public void setValid(boolean valid) {
        setVisibility(!valid);
        if (valid) {
            this.tooltip.clear();
        }
    }

    @Override
    public List<ITextComponent> getTooltipMessage() {
        return tooltip;
    }

    public void setTooltip(List<ITextComponent> lines) {
        this.tooltip.clear();
        this.tooltip.addAll(lines);
    }

    @Override
    protected Icon getIcon() {
        return Icon.INVALID;
    }

    @Override
    public boolean mousePressed(Minecraft minecraft, int mouseX, int mouseY) {
        return false;
    }
}


