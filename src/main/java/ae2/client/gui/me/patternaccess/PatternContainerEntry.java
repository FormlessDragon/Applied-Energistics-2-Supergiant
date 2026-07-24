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
import ae2.container.me.patternaccess.ContainerPatternAccessTerm;
import ae2.util.inv.AppEngInternalInventory;

import java.util.Locale;
import java.util.Objects;

/**
 * This class is used on the client-side to represent a pattern provider and its inventory as it is shown in the
 * {@link GuiPatternAccessTerm}'s table for {@link ContainerPatternAccessTerm}.
 */
public class PatternContainerEntry implements Comparable<PatternContainerEntry> {
    public static final int MAX_INVENTORY_SIZE = 4096;

    private final PatternContainerGroup group;
    private final String searchName;
    private final String providerLabel;
    private final String providerSearchText;
    private final long serverId;
    private final AppEngInternalInventory inventory;
    private final long order;
    private final boolean acceptsProcessingPatterns;
    private final boolean editableTerminalName;
    private final boolean terminalVisibilityModifiable;

    public PatternContainerEntry(long serverId, int slots, long order, boolean acceptsProcessingPatterns,
                                 boolean editableTerminalName, boolean terminalVisibilityModifiable,
                                 PatternContainerGroup group) {
        this(serverId, slots, order, acceptsProcessingPatterns, editableTerminalName, terminalVisibilityModifiable,
            group, defaultProviderLabel(group, slots), defaultProviderSearchText(group, slots));
    }

    public PatternContainerEntry(long serverId, int slots, long order, boolean acceptsProcessingPatterns,
                                 boolean editableTerminalName, boolean terminalVisibilityModifiable,
                                 PatternContainerGroup group, String providerLabel, String providerSearchText) {
        this.inventory = new AppEngInternalInventory(Math.clamp(slots, 0, MAX_INVENTORY_SIZE));
        this.group = group;
        this.searchName = group.name().getFormattedText().toLowerCase(Locale.ROOT);
        this.providerLabel = Objects.requireNonNull(providerLabel, "providerLabel");
        this.providerSearchText = Objects.requireNonNull(providerSearchText, "providerSearchText")
            .toLowerCase(Locale.ROOT);
        this.serverId = serverId;
        this.order = order;
        this.acceptsProcessingPatterns = acceptsProcessingPatterns;
        this.editableTerminalName = editableTerminalName;
        this.terminalVisibilityModifiable = terminalVisibilityModifiable;
    }

    private static String defaultProviderLabel(PatternContainerGroup group, int slots) {
        return group.name().getFormattedText() + " (" + Math.clamp(slots, 0, MAX_INVENTORY_SIZE) + ")";
    }

    private static String defaultProviderSearchText(PatternContainerGroup group, int slots) {
        String providerName = group.name().getFormattedText();
        return defaultProviderLabel(group, slots) + "\n" + providerName;
    }

    public PatternContainerGroup getGroup() {
        return group;
    }

    public String getSearchName() {
        return searchName;
    }

    public String getProviderLabel() {
        return this.providerLabel;
    }

    public String getProviderSearchText() {
        return this.providerSearchText;
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

    public boolean acceptsProcessingPatterns() {
        return this.acceptsProcessingPatterns;
    }

    public boolean canEditTerminalName() {
        return this.editableTerminalName;
    }

    public boolean canModifyTerminalVisibility() {
        return this.terminalVisibilityModifiable;
    }
}
