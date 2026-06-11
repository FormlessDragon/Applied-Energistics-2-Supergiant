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

package ae2.client.gui.me.patternaccess;

import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.container.implementations.ContainerPatternAccessTerm;
import ae2.util.inv.AppEngInternalInventory;

import java.util.Locale;

/**
 * This class is used on the client-side to represent a pattern provider and its inventory as it is shown in the
 * {@link GuiPatternAccessTerm}'s table for {@link ContainerPatternAccessTerm}.
 */
public class PatternContainerEntry implements Comparable<PatternContainerEntry> {
    public static final int MAX_INVENTORY_SIZE = 4096;

    private final PatternContainerGroup group;
    private final String searchName;
    private final long serverId;
    private final AppEngInternalInventory inventory;
    private final long order;
    private final boolean editableTerminalName;

    public PatternContainerEntry(long serverId, int slots, long order, boolean editableTerminalName,
                                 PatternContainerGroup group) {
        this.inventory = new AppEngInternalInventory(Math.clamp(slots, 0, MAX_INVENTORY_SIZE));
        this.group = group;
        this.searchName = group.name().getFormattedText().toLowerCase(Locale.ROOT);
        this.serverId = serverId;
        this.order = order;
        this.editableTerminalName = editableTerminalName;
    }

    public PatternContainerGroup getGroup() {
        return group;
    }

    public String getSearchName() {
        return searchName;
    }

    @Override
    public int compareTo(PatternContainerEntry o) {
        return Long.compare(this.order, o.order);
    }

    public long getServerId() {
        return this.serverId;
    }

    public AppEngInternalInventory getInventory() {
        return inventory;
    }

    public boolean canEditTerminalName() {
        return this.editableTerminalName;
    }
}
