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
import ae2.client.gui.style.GuiStyleManager;
import ae2.container.implementations.ContainerMEChest;
import net.minecraft.entity.player.InventoryPlayer;

public class GuiMEChest extends AEBaseGui<ContainerMEChest> {
    public GuiMEChest(ContainerMEChest container, InventoryPlayer playerInventory) {
        this(container, playerInventory, GuiStyleManager.loadStyleDoc("/screens/me_chest.json"));
    }

    private GuiMEChest(ContainerMEChest container, InventoryPlayer playerInventory, GuiStyle style) {
        super(container, playerInventory, style);
        widgets.addOpenPriorityButton();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        var title = this.container.getGuiTitle();
        if (title != null && !title.getFormattedText().isEmpty()) {
            setTextContent(TEXT_ID_DIALOG_TITLE, title);
        }
    }
}

