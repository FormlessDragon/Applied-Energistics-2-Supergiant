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

package ae2.client.gui.implementations;

import ae2.client.gui.Icon;
import ae2.client.gui.WidgetContainer;
import ae2.client.gui.widgets.TabButton;
import ae2.container.ISubGui;
import ae2.core.network.InitNetwork;
import ae2.core.network.ServerboundPacket;
import ae2.core.network.serverbound.SwitchGuisPacket;
import ae2.text.TextComponentItemStack;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

public final class AESubGui {
    private AESubGui() {
    }

    public static void addBackButton(ISubGui subGui, String id, WidgetContainer widgets) {
        addBackButton(subGui, id, widgets, null);
    }

    public static void addBackButton(ISubGui subGui, String id, WidgetContainer widgets,
                                     @Nullable ITextComponent label) {
        if (label == null) {
            label = TextComponentItemStack.of(subGui.getHost().getMainContainerIcon());
        }
        TabButton button = new TabButton(Icon.BACK, label, AESubGui::goBack);
        widgets.add(id, button);
    }

    public static void goBack() {
        ServerboundPacket message = SwitchGuisPacket.returnToParentGui();
        InitNetwork.sendToServer(message);
    }
}
