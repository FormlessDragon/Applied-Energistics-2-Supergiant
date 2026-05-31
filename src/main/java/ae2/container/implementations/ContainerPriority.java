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

package ae2.container.implementations;

import ae2.container.AEBaseContainer;
import ae2.container.ISubGui;
import ae2.core.gui.locator.GuiHostLocator;
import ae2.helpers.IPriorityHost;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerPriority extends AEBaseContainer implements ISubGui {

    private static final String ACTION_SET_PRIORITY = "setPriority";

    private final IPriorityHost host;

    private int priorityValue;

    public ContainerPriority(InventoryPlayer ip, IPriorityHost host) {
        super(ip, host);
        this.host = host;
        this.priorityValue = host.getPriority();

        registerClientAction(ACTION_SET_PRIORITY, Integer.class, this::setPriority);
    }

    public void setPriority(int newValue) {
        if (newValue != priorityValue) {
            if (isClientSide()) {
                this.priorityValue = newValue;
                sendClientAction(ACTION_SET_PRIORITY, newValue);
            } else {
                this.host.setPriority(newValue);
                this.priorityValue = newValue;
            }
        }
    }

    public void setInitialPriority(int priorityValue) {
        this.priorityValue = priorityValue;
    }

    public int getPriorityValue() {
        return priorityValue;
    }

    @Override
    public GuiHostLocator getLocator() {
        return super.getLocator();
    }

    @Override
    public IPriorityHost getHost() {
        return this.host;
    }
}
