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

package appeng.container.implementations;

import appeng.api.inventories.InternalInventory;
import appeng.container.SlotSemantics;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.IProgressProvider;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.MolecularAssemblerPatternSlot;
import appeng.container.slot.OutputSlot;
import appeng.container.slot.RestrictedInputSlot;
import appeng.tile.crafting.IMolecularAssemblerSupportedPattern;
import appeng.tile.crafting.TileMolecularAssembler;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.world.World;

public class ContainerMolecularAssembler extends UpgradeableContainer<TileMolecularAssembler>
    implements MolecularAssemblerPatternSlot.Host, IProgressProvider {
    private static final int MAX_CRAFT_PROGRESS = 100;

    @GuiSync(4)
    public int craftProgress;

    private Slot encodedPatternSlot;

    public ContainerMolecularAssembler( InventoryPlayer playerInventory, TileMolecularAssembler host) {
        super(playerInventory, host);
    }

    @Override
    protected void setupConfig() {
        InternalInventory inventory = this.getHost().getSubInventory(TileMolecularAssembler.INV_MAIN);
        if (inventory == null) {
            return;
        }

        for (int slot = 0; slot < 9; slot++) {
            this.addSlot(new MolecularAssemblerPatternSlot(this, inventory, slot), SlotSemantics.MACHINE_CRAFTING_GRID);
        }

        this.encodedPatternSlot = this.addSlot(
            new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.MOLECULAR_ASSEMBLER_PATTERN, inventory,
                10),
            SlotSemantics.ENCODED_PATTERN);
        this.addSlot(new OutputSlot(inventory, 9, 0, 0), SlotSemantics.MACHINE_OUTPUT);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            this.craftProgress = this.getHost().getCraftingProgress();
        }
        super.broadcastChanges();
    }

    @Override
    public int getCurrentProgress() {
        return this.craftProgress;
    }

    @Override
    public int getMaxProgress() {
        return MAX_CRAFT_PROGRESS;
    }

    @Override
    public void onSlotChange(Slot slot) {
        if (slot == this.encodedPatternSlot) {
            for (Slot otherSlot : this.inventorySlots) {
                if (otherSlot != slot && otherSlot instanceof AppEngSlot appEngSlot) {
                    appEngSlot.resetCachedValidation();
                }
            }
        }
    }

    @Override
    public IMolecularAssemblerSupportedPattern getCurrentPattern() {
        return this.getHost().getCurrentPattern();
    }

    @Override
    public World getWorld() {
        return this.getHost().getWorld();
    }

}
