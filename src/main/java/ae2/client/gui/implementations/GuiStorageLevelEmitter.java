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

import ae2.api.config.FuzzyMode;
import ae2.api.config.RedstoneMode;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.client.gui.NumberEntryType;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.NumberEntryWidget;
import ae2.client.gui.widgets.ServerSettingToggleButton;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.container.implementations.ContainerStorageLevelEmitter;
import ae2.core.definitions.AEItems;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

public class GuiStorageLevelEmitter extends GuiUpgradeable<ContainerStorageLevelEmitter> {

    private final SettingToggleButton<YesNo> craftingMode;
    private final SettingToggleButton<RedstoneMode> redstoneMode;
    private final SettingToggleButton<FuzzyMode> fuzzyMode;
    private final NumberEntryWidget level;

    public GuiStorageLevelEmitter(ContainerStorageLevelEmitter container, InventoryPlayer playerInventory, ITextComponent title,
                                  GuiStyle style) {
        super(container, playerInventory, title, style);

        this.redstoneMode = addToLeftToolbar(
            new ServerSettingToggleButton<>(Settings.REDSTONE_EMITTER, RedstoneMode.LOW_SIGNAL));
        this.craftingMode = addToLeftToolbar(
            new ServerSettingToggleButton<>(Settings.CRAFT_VIA_REDSTONE, YesNo.NO));
        this.fuzzyMode = addToLeftToolbar(
            new ServerSettingToggleButton<>(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL));

        this.level = widgets.addNumberEntryWidget("level", NumberEntryType.of(container.getConfiguredFilter()));
        this.level.setTextFieldStyle(style.getWidget("levelInput"));
        this.level.setLongValue(this.container.getCurrentValue());
        this.level.setOnChange(this::saveReportingValue);
        this.level.setOnConfirm(this::saveReportingValue);
        this.level.setFocused(false);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.level.setType(NumberEntryType.of(container.getConfiguredFilter()));
        syncLevelFromContainer();

        this.fuzzyMode.set(container.getFuzzyMode());
        this.fuzzyMode.setVisibility(container.supportsFuzzySearch());

        boolean notCraftingMode = !container.hasUpgrade(AEItems.CRAFTING_CARD.item());
        this.level.setActive(notCraftingMode);

        this.redstoneMode.enabled = notCraftingMode;
        this.redstoneMode.set(container.getRedStoneMode());
        this.redstoneMode.setVisibility(notCraftingMode);

        this.craftingMode.set(this.container.getCraftingMode());
        this.craftingMode.setVisibility(!notCraftingMode);
    }

    private void syncLevelFromContainer() {
        long currentValue = this.container.getCurrentValue();
        var levelValue = this.level.getLongValue();
        if (!this.level.isFocused() && (levelValue.isEmpty() || levelValue.getAsLong() != currentValue)) {
            this.level.setLongValue(currentValue);
        }
    }

    private void saveReportingValue() {
        this.level.getLongValue().ifPresent(container::setValue);
    }
}

