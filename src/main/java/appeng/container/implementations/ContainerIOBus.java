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

import appeng.api.util.KeyTypeSelection;
import appeng.api.util.KeyTypeSelectionHost;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.IKeyTypeSelectionContainer;
import appeng.core.definitions.AEItems;
import appeng.parts.automation.IOBusPart;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerIOBus extends UpgradeableContainer<IOBusPart> implements IKeyTypeSelectionContainer {

    private final KeyTypeSelection fallbackKeyTypeSelection = new KeyTypeSelection(() -> {
    }, keyType -> true);
    @GuiSync(20)
    public SyncedKeyTypes importKeyTypes = new SyncedKeyTypes();

    public ContainerIOBus(InventoryPlayer ip, IOBusPart host) {
        super(ip, host);
        updateImportKeyTypes();
    }

    @Override
    protected void setupConfig() {
        addExpandableConfigSlots(getHost().getConfig());
    }

    @Override
    public boolean isSlotEnabled(int idx) {
        final int upgrades = getUpgrades().getInstalledUpgrades(AEItems.CAPACITY_CARD.item());
        return upgrades > idx;
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            updateImportKeyTypes();
        }

        super.broadcastChanges();
    }

    @Override
    public KeyTypeSelection getServerKeyTypeSelection() {
        if (getHost() instanceof KeyTypeSelectionHost selectionHost) {
            return selectionHost.getKeyTypeSelection();
        }
        return fallbackKeyTypeSelection;
    }

    @Override
    public SyncedKeyTypes getClientKeyTypeSelection() {
        return importKeyTypes;
    }

    private void updateImportKeyTypes() {
        if (getHost() instanceof KeyTypeSelectionHost selectionHost) {
            importKeyTypes = new SyncedKeyTypes(selectionHost.getKeyTypeSelection().enabled());
        } else {
            importKeyTypes = new SyncedKeyTypes();
        }
    }
}

