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

import ae2.api.config.FuzzyMode;
import ae2.api.config.RedstoneMode;
import ae2.api.config.SchedulingMode;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.util.KeyTypeSelectionHost;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.KeyTypeSelectionButton;
import ae2.client.gui.widgets.ServerSettingToggleButton;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.container.implementations.ContainerIOBus;
import ae2.core.definitions.AEItems;
import ae2.core.localization.GuiText;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

public class GuiIOBus extends GuiUpgradeable<ContainerIOBus> {

    private final SettingToggleButton<RedstoneMode> redstoneMode;
    private final SettingToggleButton<FuzzyMode> fuzzyMode;
    private final SettingToggleButton<YesNo> craftMode;
    private final SettingToggleButton<SchedulingMode> schedulingMode;

    public GuiIOBus(ContainerIOBus container, InventoryPlayer playerInventory, ITextComponent title, GuiStyle style) {
        super(container, playerInventory, title, style);

        if (container.getHost() instanceof KeyTypeSelectionHost) {
            addToLeftToolbar(KeyTypeSelectionButton.create(this, container.getHost(), GuiText.ConfigureImportedTypes.text()));
        }

        this.redstoneMode = addToLeftToolbar(
            new ServerSettingToggleButton<>(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE));
        this.fuzzyMode = addToLeftToolbar(new ServerSettingToggleButton<>(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL));

        if (container.getHost().getConfigManager().hasSetting(Settings.CRAFT_ONLY)) {
            this.craftMode = addToLeftToolbar(new ServerSettingToggleButton<>(Settings.CRAFT_ONLY, YesNo.NO));
        } else {
            this.craftMode = null;
        }

        if (container.getHost().getConfigManager().hasSetting(Settings.SCHEDULING_MODE)) {
            this.schedulingMode = addToLeftToolbar(
                new ServerSettingToggleButton<>(Settings.SCHEDULING_MODE, SchedulingMode.DEFAULT));
        } else {
            this.schedulingMode = null;
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.redstoneMode.set(container.getRedStoneMode());
        this.redstoneMode.setVisibility(container.hasUpgrade(AEItems.REDSTONE_CARD.item()));

        this.fuzzyMode.set(container.getFuzzyMode());
        this.fuzzyMode.setVisibility(container.hasUpgrade(AEItems.FUZZY_CARD.item()));

        if (this.craftMode != null) {
            this.craftMode.set(container.getCraftingMode());
            this.craftMode.setVisibility(container.hasUpgrade(AEItems.CRAFTING_CARD.item()));
        }

        if (this.schedulingMode != null) {
            this.schedulingMode.set(container.getSchedulingMode());
        }
    }
}

