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

package appeng.container.implementations;

import appeng.api.config.Settings;
import appeng.api.util.IConfigManager;
import appeng.parts.automation.EnergyLevelEmitterPart;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerEnergyLevelEmitter extends UpgradeableContainer<EnergyLevelEmitterPart> {

    private static final String ACTION_SET_REPORTING_VALUE = "setReportingValue";

    private long reportingValue;

    public ContainerEnergyLevelEmitter( InventoryPlayer ip, EnergyLevelEmitterPart host) {
        super(ip, host);

        registerClientAction(ACTION_SET_REPORTING_VALUE, Long.class, this::setReportingValue);
    }

    public ContainerEnergyLevelEmitter( InventoryPlayer ip, EnergyLevelEmitterPart host,
                                       long initialReportingValue) {
        this(ip, host);
        setInitialReportingValue(initialReportingValue);
    }

    public void setInitialReportingValue(long reportingValue) {
        if (isClientSide()) {
            this.reportingValue = reportingValue;
        }
    }

    public long getReportingValue() {
        return reportingValue;
    }

    public void setReportingValue(long reportingValue) {
        if (isClientSide()) {
            if (reportingValue != this.reportingValue) {
                this.reportingValue = reportingValue;
                sendClientAction(ACTION_SET_REPORTING_VALUE, reportingValue);
            }
        } else {
            getHost().setReportingValue(reportingValue);
        }
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        this.setRedStoneMode(cm.getSetting(Settings.REDSTONE_EMITTER));
    }
}
