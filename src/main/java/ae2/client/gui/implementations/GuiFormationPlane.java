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

import ae2.api.config.ActionItems;
import ae2.api.config.FormationPlaneMode;
import ae2.api.config.FuzzyMode;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.ActionButton;
import ae2.client.gui.widgets.ServerSettingToggleButton;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.container.GuiIds;
import ae2.container.implementations.ContainerFormationPlane;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.SwitchGuisPacket;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

public class GuiFormationPlane extends GuiUpgradeable<ContainerFormationPlane> {

    private final SettingToggleButton<FuzzyMode> fuzzyMode;
    private final SettingToggleButton<FormationPlaneMode> formationPlaneMode;
    private final SettingToggleButton<YesNo> placeMode;
    private final ActionButton workIntervalButton;

    public GuiFormationPlane(ContainerFormationPlane container, InventoryPlayer playerInventory, ITextComponent title,
                             GuiStyle style) {
        super(container, playerInventory, title, style);

        this.formationPlaneMode = addToLeftToolbar(new ServerSettingToggleButton<>(Settings.FORMATION_PLANE_MODE,
            FormationPlaneMode.PASSIVE));
        this.placeMode = addToLeftToolbar(new ServerSettingToggleButton<>(Settings.PLACE_BLOCK, YesNo.YES));
        this.fuzzyMode = addToLeftToolbar(new ServerSettingToggleButton<>(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL));
        this.workIntervalButton = addToLeftToolbar(new ActionButton(ActionItems.WORK_INTERVAL,
            this::openWorkInterval));

        widgets.addOpenPriorityButton();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.fuzzyMode.set(container.getFuzzyMode());
        this.fuzzyMode.setVisibility(container.supportsFuzzyMode());
        FormationPlaneMode formationMode = container.getFormationPlaneMode();
        if (formationMode != null) {
            this.formationPlaneMode.set(formationMode);
        } else {
            formationMode = FormationPlaneMode.PASSIVE;
        }
        this.placeMode.set(container.getPlaceMode());
        this.workIntervalButton.setVisibility(formationMode == FormationPlaneMode.ACTIVE);
    }

    private void openWorkInterval() {
        InitNetwork.sendToServer(SwitchGuisPacket.openSubGui(GuiIds.GuiKey.WORK_INTERVAL));
    }
}

