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

import ae2.api.upgrades.Upgrades;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.ToolboxPanel;
import ae2.client.gui.widgets.UpgradesPanel;
import ae2.container.SlotSemantics;
import ae2.container.implementations.UpgradeableContainer;
import ae2.core.localization.GuiText;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

import java.util.List;

public class GuiUpgradeable<T extends UpgradeableContainer<?>> extends AEBaseGui<T> {

    public GuiUpgradeable(T container, InventoryPlayer playerInventory, GuiStyle style) {
        this(container, playerInventory, null, style);
    }

    public GuiUpgradeable(T container, InventoryPlayer playerInventory, @SuppressWarnings("unused") ITextComponent title,
                          GuiStyle style) {
        super(container, playerInventory, style);

        this.widgets.add("upgrades", UpgradesPanel.create(
            this.widgets,
            container.getSlots(SlotSemantics.UPGRADE),
            this::getCompatibleUpgrades));
        if (container.getToolbox().isPresent()) {
            this.widgets.add("toolbox", new ToolboxPanel(style, container.getToolbox().getName()));
        }
    }

    private List<ITextComponent> getCompatibleUpgrades() {
        var upgradeLines = Upgrades.getTooltipLinesForInventory(container.getUpgrades());
        var list = new ObjectArrayList<ITextComponent>(upgradeLines.size() + 1);
        list.add(GuiText.CompatibleUpgrades.text());
        list.addAll(upgradeLines);
        return list;
    }
}
