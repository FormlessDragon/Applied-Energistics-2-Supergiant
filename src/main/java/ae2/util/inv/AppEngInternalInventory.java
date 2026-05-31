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

package ae2.util.inv;

import ae2.api.inventories.BaseInternalInventory;
import ae2.util.inv.filter.IAEItemFilter;
import com.google.common.base.Preconditions;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class AppEngInternalInventory extends BaseInternalInventory {
    private final NonNullList<ItemStack> stacks;
    private final int[] maxStack;
    private boolean enableClientEvents = false;
    private InternalInventoryHost host;
    private IAEItemFilter filter;
    private boolean notifyingChanges = false;

    public AppEngInternalInventory(InternalInventoryHost host, int size, int maxStack, IAEItemFilter filter) {
        this.setHost(host);
        this.setFilter(filter);
        this.maxStack = new int[size];
        this.stacks = NonNullList.withSize(size, ItemStack.EMPTY);
        Arrays.fill(this.maxStack, maxStack);
    }

    public AppEngInternalInventory(@Nullable InternalInventoryHost inventory, int size, int maxStack) {
        this(inventory, size, maxStack, null);
    }

    public AppEngInternalInventory(int size) {
        this(null, size, 64);
    }

    public AppEngInternalInventory(@Nullable InternalInventoryHost inventory, int size) {
        this(inventory, size, 64);
    }

    public void setFilter(IAEItemFilter filter) {
        this.filter = filter;
    }

    @Override
    public int getSlotLimit(int slot) {
        return this.maxStack[slot];
    }

    @Override
    public ItemStack getStackInSlot(int slotIndex) {
        return stacks.get(slotIndex);
    }

    @Override
    public void setItemDirect(int slot, ItemStack stack) {
        stacks.set(slot, stack);
        notifyContentsChanged(slot);
    }

    private void notifyContentsChanged(int slot) {
        onContentsChanged(slot);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        Preconditions.checkArgument(slot >= 0 && slot < size(), "slot out of range");

        if (this.filter != null && !this.filter.allowExtract(this, slot, amount)) {
            return ItemStack.EMPTY;
        }

        var stack = stacks.get(slot);
// This inventory adheres to vanilla stack size limits
        int toExtract = Math.min(stack.getCount(), Math.min(amount, stack.getMaxStackSize()));
        if (toExtract <= 0) {
            return ItemStack.EMPTY;
        }

        if (stack.getCount() <= toExtract) {
            if (!simulate) {
                setItemDirect(slot, ItemStack.EMPTY);
                notifyContentsChanged(slot);
                return stack;
            } else {
                return stack.copy();
            }
        } else {
            var result = stack.copy();

            if (!simulate) {
                stack.shrink(toExtract);
                notifyContentsChanged(slot);
            }

            result.setCount(toExtract);
            return result;
        }
    }

    protected void onContentsChanged(int slot) {
        if (this.host != null && this.eventsEnabled() && !this.notifyingChanges) {
            this.notifyingChanges = true;
            this.host.onChangeInventory(this, slot);
            this.host.saveChangedInventory(this);
            this.notifyingChanges = false;
        }
    }

    protected boolean eventsEnabled() {
        return this.host != null && !this.host.isClientSide() || this.isEnableClientEvents();
    }

    public void setMaxStackSize(int slot, int size) {
        this.maxStack[slot] = size;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (this.maxStack[slot] == 0) {
            return false;
        }
        if (this.filter != null) {
            return this.filter.allowInsert(this, slot, stack);
        }
        return true;
    }

    public void writeToNBT(NBTTagCompound data, String name) {
        if (isEmpty()) {
            data.removeTag(name);
            return;
        }

        var items = new NBTTagList();
        for (int i = 0; i < stacks.size(); i++) {
            var stack = stacks.get(i);
            if (!stack.isEmpty()) {
                NBTTagCompound itemTag = new NBTTagCompound();
                itemTag.setInteger("Slot", i);
                stack.writeToNBT(itemTag);
                items.appendTag(itemTag);
            }
        }
        data.setTag(name, items);
    }

    public void readFromNBT(NBTTagCompound data, String name) {
        if (data.hasKey(name, 9)) {
            var tagList = data.getTagList(name, 10);
            for (var i = 0; i < tagList.tagCount(); i++) {
                var itemCompound = tagList.getCompoundTagAt(i);
                int slot = itemCompound.getInteger("Slot");

                if (slot >= 0 && slot < stacks.size()) {
                    stacks.set(slot, new ItemStack(itemCompound));
                }
            }
        }
    }

    private boolean isEnableClientEvents() {
        return this.enableClientEvents;
    }

    public void setEnableClientEvents(boolean enableClientEvents) {
        this.enableClientEvents = enableClientEvents;
    }

    @ApiStatus.Internal
    public InternalInventoryHost getHost() {
        return host;
    }

    protected final void setHost(InternalInventoryHost host) {
        this.host = host;
    }

    @Override
    public int size() {
        return stacks.size();
    }
}
