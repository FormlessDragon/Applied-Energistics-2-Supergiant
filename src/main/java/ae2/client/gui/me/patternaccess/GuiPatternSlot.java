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

package ae2.client.gui.me.patternaccess;

import ae2.container.slot.AppEngSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * This slot is used in the {@link GuiPatternAccessTerm} to interact with the internal inventory of pattern
 * providers.
 */
public class GuiPatternSlot extends AppEngSlot {

    private final PatternContainerEntry machineInv;

    public GuiPatternSlot(PatternContainerEntry machineInv, int machineInvSlot, int x, int y) {
        super(machineInv.getInventory(), machineInvSlot, x, y);
        this.machineInv = machineInv;
    }

    @Override
    public ItemStack getDisplayStack() {
        if (isRemote()) {
            final ItemStack stack = super.getDisplayStack();
            if (!stack.isEmpty()) {
                World world = this.getContainer() != null ? this.getContainer().getPlayer().world : Minecraft.getMinecraft().world;
                if (world != null) {
                    // Decoded once per stack and cached on the entry; the render loop must not decode per frame.
                    return this.machineInv.getPatternDisplayStack(stack, world);
                }
            }
        }
        return super.getDisplayStack();
    }

    public PatternContainerEntry getMachineInv() {
        return this.machineInv;
    }

    @Override
    public final boolean isItemValid(ItemStack stack) {
        return false;
    }

    @Override
    public final void putStack(ItemStack stack) {
    }

    @Override
    public final int getSlotStackLimit() {
        return 0;
    }

    @Override
    public final ItemStack decrStackSize(int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public final boolean canTakeStack(EntityPlayer player) {
        return false;
    }
}
