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

package appeng.client.gui.implementations;

import appeng.api.config.FormationPlaneMode;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.client.gui.style.GuiStyle;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.container.implementations.ContainerFormationPlane;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

public class GuiFormationPlane extends GuiUpgradeable<ContainerFormationPlane> {

    private final SettingToggleButton<FuzzyMode> fuzzyMode;
    private final SettingToggleButton<FormationPlaneMode> formationPlaneMode;
    private final SettingToggleButton<YesNo> placeMode;

    public GuiFormationPlane(ContainerFormationPlane container, InventoryPlayer playerInventory, ITextComponent title,
                             GuiStyle style) {
        super(container, playerInventory, title, style);

        this.formationPlaneMode = addToLeftToolbar(new ServerSettingToggleButton<>(Settings.FORMATION_PLANE_MODE,
            FormationPlaneMode.PASSIVE));
        this.placeMode = addToLeftToolbar(new ServerSettingToggleButton<>(Settings.PLACE_BLOCK, YesNo.YES));
        this.fuzzyMode = addToLeftToolbar(new ServerSettingToggleButton<>(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL));

        widgets.addOpenPriorityButton();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.fuzzyMode.set(container.getFuzzyMode());
        this.fuzzyMode.setVisibility(container.supportsFuzzyMode());
        this.formationPlaneMode.set(container.getFormationPlaneMode());
        this.placeMode.set(container.getPlaceMode());
    }
}

