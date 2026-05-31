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

import ae2.api.config.Settings;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.util.IConfigManager;
import ae2.container.SlotSemantics;
import ae2.container.slot.FakeSlot;
import ae2.core.definitions.AEItems;
import ae2.parts.automation.StorageLevelEmitterPart;
import net.minecraft.entity.player.InventoryPlayer;
import org.jetbrains.annotations.Nullable;

public class ContainerStorageLevelEmitter extends UpgradeableContainer<StorageLevelEmitterPart> {

    private static final String ACTION_SET_REPORTING_VALUE = "setReportingValue";

    private long currentValue;

    public ContainerStorageLevelEmitter(InventoryPlayer ip, StorageLevelEmitterPart te) {
        super(ip, te);

        registerClientAction(ACTION_SET_REPORTING_VALUE, Long.class, this::setValue);
    }

    public ContainerStorageLevelEmitter(InventoryPlayer ip, StorageLevelEmitterPart te,
                                        @Nullable GenericStack initialFilter, long initialValue) {
        this(ip, te);
        setInitialFilter(initialFilter);
        setInitialValue(initialValue);
    }

    public void setInitialFilter(@Nullable GenericStack filter) {
        if (isClientSide()) {
            getHost().getConfig().setStack(0, filter);
        }
    }

    public void setInitialValue(long value) {
        if (isClientSide()) {
            this.currentValue = value;
        }
    }

    public long getCurrentValue() {
        return currentValue;
    }

    public void setValue(long initialValue) {
        if (isClientSide()) {
            if (initialValue != this.currentValue) {
                this.currentValue = initialValue;
                sendClientAction(ACTION_SET_REPORTING_VALUE, initialValue);
            }
        } else {
            getHost().setReportingValue(initialValue);
        }
    }

    @Override
    protected void setupConfig() {
        var inv = getHost().getConfig().createGuiWrapper();
        this.addSlot(new FakeSlot(inv, 0, 124, 40), SlotSemantics.CONFIG);
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        this.setCraftingMode(cm.getSetting(Settings.CRAFT_VIA_REDSTONE));
        if (cm.hasSetting(Settings.FUZZY_MODE)) {
            this.setFuzzyMode(cm.getSetting(Settings.FUZZY_MODE));
        }
        this.setRedStoneMode(cm.getSetting(Settings.REDSTONE_EMITTER));
    }

    public boolean supportsFuzzySearch() {
        return getHost().getConfigManager().hasSetting(Settings.FUZZY_MODE)
            && hasUpgrade(AEItems.FUZZY_CARD.item());
    }

    @Nullable
    public AEKey getConfiguredFilter() {
        return getHost().getConfig().getKey(0);
    }
}
