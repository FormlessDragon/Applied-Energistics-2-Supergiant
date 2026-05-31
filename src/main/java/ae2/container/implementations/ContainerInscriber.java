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

import ae2.api.config.InscriberInputCapacity;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.util.IConfigManager;
import ae2.container.SlotSemantics;
import ae2.container.guisync.GuiSync;
import ae2.container.interfaces.IProgressProvider;
import ae2.container.slot.OutputSlot;
import ae2.container.slot.RestrictedInputSlot;
import ae2.tile.misc.InscriberRecipes;
import ae2.tile.misc.TileInscriber;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class ContainerInscriber extends UpgradeableContainer<TileInscriber> implements IProgressProvider {
    private final Slot top;
    private final Slot middle;
    private final Slot bottom;

    @GuiSync(2)
    public int maxProcessingTime = -1;

    @GuiSync(3)
    public int processingTime = -1;

    @GuiSync(7)
    public YesNo separateSides = YesNo.NO;
    @GuiSync(8)
    public YesNo autoExport = YesNo.NO;
    @GuiSync(9)
    public InscriberInputCapacity bufferSize = InscriberInputCapacity.SIXTY_FOUR;

    public ContainerInscriber(InventoryPlayer ip, TileInscriber host) {
        super(ip, host);

        var inv = host.getInternalInventory();
        this.top = this.addSlot(
            new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.INSCRIBER_PLATE, inv, 0),
            SlotSemantics.INSCRIBER_PLATE_TOP);
        this.bottom = this.addSlot(
            new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.INSCRIBER_PLATE, inv, 1),
            SlotSemantics.INSCRIBER_PLATE_BOTTOM);
        this.middle = this.addSlot(
            new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.INSCRIBER_INPUT, inv, 2),
            SlotSemantics.MACHINE_INPUT);
        this.addSlot(new OutputSlot(inv, 3, 0, 0), SlotSemantics.MACHINE_OUTPUT);
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        this.separateSides = cm.getSetting(Settings.INSCRIBER_SEPARATE_SIDES);
        this.autoExport = cm.getSetting(Settings.AUTO_EXPORT);
        this.bufferSize = cm.getSetting(Settings.INSCRIBER_INPUT_CAPACITY);
    }

    @Override
    protected void standardDetectAndSendChanges() {
        if (isServerSide()) {
            this.maxProcessingTime = getHost().getMaxProcessingTime();
            this.processingTime = getHost().getProcessingTime();
        }
        super.standardDetectAndSendChanges();
    }

    @Override
    public boolean isValidForSlot(Slot slot, ItemStack stack) {
        final ItemStack top = this.getHost().getInternalInventory().getStackInSlot(0);
        final ItemStack bottom = this.getHost().getInternalInventory().getStackInSlot(1);

        if (slot == this.middle) {
            if (InscriberRecipes.isValidOptionalIngredient(top) || InscriberRecipes.isValidOptionalIngredient(bottom)) {
                return !InscriberRecipes.isValidOptionalIngredient(stack);
            }

            return InscriberRecipes.findRecipe(stack, top, bottom, false) != null;
        } else if (slot == this.top && !bottom.isEmpty() || slot == this.bottom && !top.isEmpty()) {
            ItemStack otherSlot = slot == this.top ? this.bottom.getStack() : this.top.getStack();
            if (InscriberRecipes.isValidOptionalIngredient(otherSlot)) {
                return InscriberRecipes.isValidOptionalIngredient(stack);
            }
            return InscriberRecipes.isValidOptionalIngredientCombination(stack, otherSlot);
        }

        return true;
    }

    @Override
    public int getCurrentProgress() {
        return this.processingTime;
    }

    @Override
    public int getMaxProgress() {
        return this.maxProcessingTime;
    }

    public YesNo getSeparateSides() {
        return this.separateSides;
    }

    public YesNo getAutoExport() {
        return this.autoExport;
    }

    public InscriberInputCapacity getBufferSize() {
        return this.bufferSize;
    }
}
