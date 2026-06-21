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

import ae2.api.config.RedstoneMode;
import ae2.api.config.Settings;
import ae2.client.gui.NumberEntryType;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.NumberEntryWidget;
import ae2.client.gui.widgets.ServerSettingToggleButton;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.container.implementations.ContainerEnergyLevelEmitter;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

public class GuiEnergyLevelEmitter extends GuiUpgradeable<ContainerEnergyLevelEmitter> {

    private final SettingToggleButton<RedstoneMode> redstoneMode;
    private final NumberEntryWidget level;

    public GuiEnergyLevelEmitter(ContainerEnergyLevelEmitter container, InventoryPlayer playerInventory, ITextComponent title,
                                 GuiStyle style) {
        super(container, playerInventory, title, style);

        this.redstoneMode = addToLeftToolbar(
            new ServerSettingToggleButton<>(Settings.REDSTONE_EMITTER, RedstoneMode.LOW_SIGNAL));

        this.level = widgets.addNumberEntryWidget("level", NumberEntryType.ENERGY);
        this.level.setTextFieldStyle(style.getWidget("levelInput"));
        this.level.setLongValue(container.getReportingValue());
        this.level.setOnChange(this::saveReportingValue);
        this.level.setOnConfirm(this::saveReportingValue);
        this.level.setFocused(false);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        syncLevelFromContainer();

        this.redstoneMode.enabled = true;
        this.redstoneMode.set(container.getRedStoneMode());
    }

    private void syncLevelFromContainer() {
        long reportingValue = this.container.getReportingValue();
        var currentValue = this.level.getLongValue();
        if (!this.level.isFocused() && (currentValue.isEmpty() || currentValue.getAsLong() != reportingValue)) {
            this.level.setLongValue(reportingValue);
        }
    }

    private void saveReportingValue() {
        this.level.getLongValue().ifPresent(container::setReportingValue);
    }
}

