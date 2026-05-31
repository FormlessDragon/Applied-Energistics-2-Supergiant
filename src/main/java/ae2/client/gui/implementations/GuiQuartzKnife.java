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
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.ITextFieldGui;
import ae2.container.implementations.ContainerQuartzKnife;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

import java.util.Collection;

public class GuiQuartzKnife extends AEBaseGui<ContainerQuartzKnife> implements ITextFieldGui {
    private final AETextField name;

    public GuiQuartzKnife(ContainerQuartzKnife container, InventoryPlayer playerInventory, ITextComponent title,
                          GuiStyle style) {
        super(container, playerInventory, style);
        this.name = widgets.addTextField("name");
        this.name.setMaxStringLength(32);
        this.name.setResponder(container::setName);
    }

    @Override
    public void initGui() {
        super.initGui();
        setInitialFocus(this.name);
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY, partialTicks);
    }

    @Override
    public Collection<? extends GuiTextField> getTextFields() {
        return ObjectLists.singleton(this.name);
    }
}
