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

package ae2.parts.automation;

import ae2.api.behaviors.StackImportStrategy;
import ae2.api.networking.IGrid;
import ae2.api.parts.IPartCollisionHelper;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.util.KeyTypeSelection;
import ae2.api.util.KeyTypeSelectionHost;
import ae2.container.GuiIds;
import ae2.core.definitions.AEItems;
import ae2.core.settings.TickRates;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import org.jetbrains.annotations.Nullable;

public class ImportBusPart extends IOBusPart implements KeyTypeSelectionHost {
    private final KeyTypeSelection keyTypeSelection;
    @Nullable
    private StackImportStrategy importStrategy;

    public ImportBusPart(IPartItem<?> partItem) {
        super(TickRates.ImportBus, StackWorldBehaviors.withImportStrategy(), partItem);

        this.keyTypeSelection = new KeyTypeSelection(() -> {
            getHost().markForSave();
            // Reset strategies
            importStrategy = null;
            // We can potentially wake up now
            getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        }, StackWorldBehaviors.hasImportStrategyTypeFilter());
    }

    @Override
    protected boolean doBusWork(IGrid grid) {
        if (importStrategy == null) {
            var self = this.getHost().getTileEntity();
            var side = getSide();
            if (side == null) {
                return false;
            }
            var fromPos = self.getPos().offset(side);
            var fromSide = side.getOpposite();
            importStrategy = StackWorldBehaviors.createImportFacade((WorldServer) getLevel(), fromPos, fromSide,
                keyTypeSelection.enabledPredicate());
        }

        var context = new StackTransferContextImpl(
            grid.getStorageService(),
            grid.getEnergyService(),
            this.source,
            getOperationsPerTick(),
            getFilter());

        context.setInverted(this.isUpgradedWith(AEItems.INVERTER_CARD));
        importStrategy.transfer(context);

        return context.hasDoneWork();
    }

    @Override
    protected GuiIds.GuiKey getGuiKey() {
        return GuiIds.GuiKey.IMPORT_BUS;
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(6, 6, 11, 10, 10, 13);
        bch.addBox(5, 5, 13, 11, 11, 14);
        bch.addBox(4, 4, 14, 12, 12, 16);
    }

    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return MODELS_ON;
        } else {
            return MODELS_OFF;
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound extra) {
        super.readFromNBT(extra);
        keyTypeSelection.readFromNBT(extra);
    }

    @Override
    public void writeToNBT(NBTTagCompound extra) {
        super.writeToNBT(extra);
        keyTypeSelection.writeToNBT(extra);
    }

    @Override
    public KeyTypeSelection getKeyTypeSelection() {
        return keyTypeSelection;
    }
}

