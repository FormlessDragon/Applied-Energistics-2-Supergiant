/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 TeamAppliedEnergistics
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package ae2.api.inventories;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

/**
 * Adapts an {@link InternalInventory} to the {@link IInventory} interface in a read-only fashion.
 */
class ContainerAdapter implements IInventory {
    private final InternalInventory inventory;

    public ContainerAdapter(InternalInventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public int getSizeInventory() {
        return inventory.size();
    }

    @Override
    public boolean isEmpty() {
        return !inventory.iterator().hasNext();
    }

    @Override
    public ItemStack getStackInSlot(int slotIndex) {
        return inventory.getStackInSlot(slotIndex);
    }

    @Override
    public ItemStack decrStackSize(int slotIndex, int count) {
        return this.inventory.extractItem(slotIndex, count, false);
    }

    @Override
    public ItemStack removeStackFromSlot(int slotIndex) {
        return this.inventory.extractItem(slotIndex, this.inventory.getStackInSlot(slotIndex).getCount(), false);
    }

    @Override
    public void setInventorySlotContents(int slotIndex, ItemStack stack) {
        inventory.setItemDirect(slotIndex, stack);
    }

    /**
     * Since our inventories support a per-slot max size, we find the largest max-size allowable and return that.
     */
    @Override
    public int getInventoryStackLimit() {
        int max = 64;
        for (int i = 0; i < inventory.size(); ++i) {
            max = Math.min(max, inventory.getSlotLimit(i));
        }
        return max;
    }

    @Override
    public void markDirty() {
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory(EntityPlayer player) {
    }

    @Override
    public void closeInventory(EntityPlayer player) {
    }

    @Override
    public boolean isItemValidForSlot(int slotIndex, ItemStack stack) {
        return inventory.isItemValid(slotIndex, stack);
    }

    @Override
    public int getField(int id) {
        return 0;
    }

    @Override
    public void setField(int id, int value) {
    }

    @Override
    public int getFieldCount() {
        return 0;
    }

    @Override
    public void clear() {
        for (int i = 0; i < inventory.size(); i++) {
            inventory.setItemDirect(i, ItemStack.EMPTY);
        }
    }

    @Override
    public String getName() {
        return "internal_inventory";
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    public ITextComponent getDisplayName() {
        return new TextComponentString(getName());
    }
}
