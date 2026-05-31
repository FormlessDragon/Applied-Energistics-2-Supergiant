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

package ae2.client.gui.me.crafting;

import ae2.client.gui.implementations.AESubGui;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.CPUSelectionList;
import ae2.client.gui.widgets.Scrollbar;
import ae2.container.implementations.ContainerCraftingStatus;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

public class GuiCraftingStatus extends GuiCraftingCPU<ContainerCraftingStatus> {

    public GuiCraftingStatus(ContainerCraftingStatus container, InventoryPlayer playerInventory,
                             ITextComponent title, GuiStyle style) {
        super(container, playerInventory, title, style);

        AESubGui.addBackButton(container, "back", widgets);

        var scrollbar = widgets.addScrollBar("selectCpuScrollbar", Scrollbar.BIG);
        widgets.add("selectCpuList", new CPUSelectionList(container, scrollbar, style));
    }

    @Override
    protected ITextComponent getGuiDisplayName(ITextComponent in) {
        return in;
    }
}

