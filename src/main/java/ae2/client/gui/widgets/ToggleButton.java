/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.util.text.ITextComponent;

import java.util.List;

public class ToggleButton extends IconButton {

    private final Listener listener;
    private final Icon iconOn;
    private final Icon iconOff;

    private List<ITextComponent> tooltipOn = ObjectLists.emptyList();
    private List<ITextComponent> tooltipOff = ObjectLists.emptyList();

    private boolean state;

    public ToggleButton(Icon on, Icon off, ITextComponent displayName, ITextComponent displayHint, Listener listener) {
        this(on, off, listener);
        setTooltipOn(List.of(displayName, displayHint));
        setTooltipOff(List.of(displayName, displayHint));
    }

    public ToggleButton(Icon on, Icon off, Listener listener) {
        super(null);
        this.iconOn = on;
        this.iconOff = off;
        this.listener = listener;
    }

    public void setTooltipOn(List<ITextComponent> lines) {
        this.tooltipOn = lines;
    }

    public void setTooltipOff(List<ITextComponent> lines) {
        this.tooltipOff = lines;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        boolean releasedInside = this.enabled && this.visible
            && mouseX >= this.x
            && mouseY >= this.y
            && mouseX < this.x + this.width
            && mouseY < this.y + this.height;
        super.mouseReleased(mouseX, mouseY);
        if (releasedInside) {
            this.listener.onChange(!this.state);
        }
    }

    public void setState(boolean isOn) {
        this.state = isOn;
    }

    @Override
    protected Icon getIcon() {
        return this.state ? this.iconOn : this.iconOff;
    }

    @Override
    public List<ITextComponent> getTooltipMessage() {
        return this.state ? this.tooltipOn : this.tooltipOff;
    }

    @FunctionalInterface
    public interface Listener {
        void onChange(boolean state);
    }
}

