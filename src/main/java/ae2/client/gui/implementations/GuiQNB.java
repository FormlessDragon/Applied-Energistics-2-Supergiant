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

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.style.GuiStyle;
import ae2.container.implementations.ContainerQNB;
import ae2.core.definitions.AEItems;
import ae2.core.localization.GuiText;
import ae2.tile.qnb.TileQuantumBridge;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class GuiQNB extends AEBaseGui<ContainerQNB> {

    public GuiQNB(ContainerQNB container, InventoryPlayer playerInventory, ITextComponent title, GuiStyle style) {
        super(container, playerInventory, style);
    }

    private static boolean isValidEntangledSingularity(ItemStack stack) {
        return TileQuantumBridge.isValidEntangledSingularity(stack);
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        Slot slot = this.getSlotUnderMouse();

        if (slot != null && slot.getHasStack() && !this.isPlayerSideSlot(slot)) {
            ItemStack stack = slot.getStack();

            if (AEItems.QUANTUM_ENTANGLED_SINGULARITY.is(stack) && !isValidEntangledSingularity(stack)) {
                List<String> tooltip = new ObjectArrayList<>(this.getItemToolTip(stack));
                tooltip.add(TextFormatting.RED + GuiText.InvalidSingularity.getLocal());
                this.drawTooltipLines(mouseX, mouseY, tooltip);
                return;
            }
        }

        super.renderHoveredToolTip(mouseX, mouseY);
    }
}
