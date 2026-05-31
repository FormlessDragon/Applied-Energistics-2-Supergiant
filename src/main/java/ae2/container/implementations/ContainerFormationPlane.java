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

import ae2.api.config.FormationPlaneMode;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.stacks.AEKey;
import ae2.api.util.IConfigManager;
import ae2.container.guisync.GuiSync;
import ae2.core.definitions.AEItems;
import ae2.parts.automation.FormationPlanePart;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerFormationPlane extends UpgradeableContainer<FormationPlanePart> {

    @GuiSync(7)
    public YesNo placeMode = YesNo.YES;
    @GuiSync(8)
    public FormationPlaneMode formationPlaneMode;

    public ContainerFormationPlane(InventoryPlayer ip, FormationPlanePart host) {
        super(ip, host);
    }

    @Override
    protected void setupConfig() {
        addExpandableConfigSlots(getHost().getConfig());
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        if (supportsFuzzyRangeSearch()) {
            this.setFuzzyMode(cm.getSetting(Settings.FUZZY_MODE));
        }
        this.setPlaceMode(cm.getSetting(Settings.PLACE_BLOCK));
        this.setFormationPlaneMode(cm.getSetting(Settings.FORMATION_PLANE_MODE));
    }

    @Override
    public boolean isSlotEnabled(int idx) {
        final int upgrades = getUpgrades().getInstalledUpgrades(AEItems.CAPACITY_CARD.item());
        return upgrades > idx;
    }

    public YesNo getPlaceMode() {
        return this.placeMode;
    }

    private void setPlaceMode(YesNo placeMode) {
        this.placeMode = placeMode;
    }

    public FormationPlaneMode getFormationPlaneMode() {
        return this.formationPlaneMode;
    }

    private void setFormationPlaneMode(FormationPlaneMode formationPlaneMode) {
        this.formationPlaneMode = formationPlaneMode;
    }

    public boolean supportsFuzzyMode() {
        return hasUpgrade(AEItems.FUZZY_CARD.item()) && supportsFuzzyRangeSearch();
    }

    private boolean supportsFuzzyRangeSearch() {
        for (AEKey key : this.getHost().getConfig().keySet()) {
            if (key.supportsFuzzyRangeSearch()) {
                return true;
            }
        }
        return false;
    }
}

