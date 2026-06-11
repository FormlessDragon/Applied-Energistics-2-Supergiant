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

import ae2.api.config.Setting;
import ae2.client.gui.AEBaseGui;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.ConfigButtonPacket;
import net.minecraft.client.Minecraft;

public class ServerSettingToggleButton<T extends Enum<T>> extends SettingToggleButton<T> {

    public ServerSettingToggleButton(Setting<T> setting, T val) {
        super(setting, val, ServerSettingToggleButton::sendToServer);
    }

    private static <T extends Enum<T>> void sendToServer(SettingToggleButton<T> button, boolean backwards) {
        if (Minecraft.getMinecraft().currentScreen instanceof AEBaseGui<?> gui) {
            InitNetwork.sendToServer(new ConfigButtonPacket(gui.getContainer().windowId, button.getSetting(),
                backwards));
        }
    }
}

