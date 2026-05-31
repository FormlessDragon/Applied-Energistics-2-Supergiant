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

package ae2.client.gui.implementations;

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.NumberEntryType;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.GuiStyleManager;
import ae2.client.gui.widgets.NumberEntryWidget;
import ae2.container.implementations.ContainerPriority;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

import java.awt.Rectangle;
import java.util.OptionalInt;

public class GuiPriority extends AEBaseGui<ContainerPriority> {

    private static final String STYLE_PATH = "/screens/priority.json";

    private final NumberEntryWidget priority;

    public GuiPriority(ContainerPriority container, InventoryPlayer playerInventory, ITextComponent title) {
        this(container, playerInventory, title, GuiStyleManager.loadStyleDoc(STYLE_PATH));
    }

    private GuiPriority(ContainerPriority container, InventoryPlayer playerInventory, ITextComponent title, GuiStyle style) {
        super(container, playerInventory, style);
        var background = style.getBackground();
        if (background != null) {
            Rectangle bounds = background.getDestRect();
            this.xSize = bounds.width;
            this.ySize = bounds.height;
        } else {
            this.xSize = 176;
            this.ySize = 125;
        }

        AESubGui.addBackButton(container, "back", widgets);

        this.priority = widgets.addNumberEntryWidget("priority", NumberEntryType.UNITLESS);
        this.priority.setTextFieldStyle(style.getWidget("priorityInput"));
        this.priority.setMinValue(Integer.MIN_VALUE);
        this.priority.setLongValue(this.container.getPriorityValue());
        this.priority.setOnChange(this::savePriority);
        this.priority.setOnConfirm(() -> {
            savePriority();
            AESubGui.goBack();
        });
    }

    private void savePriority() {
        OptionalInt priority = this.priority.getIntValue();
        if (priority.isPresent()) {
            container.setPriority(priority.getAsInt());
        }
    }
}

