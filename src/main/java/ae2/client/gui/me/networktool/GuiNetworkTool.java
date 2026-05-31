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

package ae2.client.gui.me.networktool;

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.style.GuiStyle;
import ae2.container.implementations.ContainerNetworkTool;
import ae2.core.localization.GuiText;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

public class GuiNetworkTool extends AEBaseGui<ContainerNetworkTool> {
    @Nullable
    private final ITextComponent title;

    public GuiNetworkTool(ContainerNetworkTool container, InventoryPlayer playerInventory, @Nullable ITextComponent title,
                          GuiStyle style) {
        super(container, playerInventory, style);
        this.title = title;
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        if (container.getGuiTitle() == null) {
            setTextContent(TEXT_ID_DIALOG_TITLE, title != null ? title : GuiText.NetworkTool.text());
        }
    }
}
