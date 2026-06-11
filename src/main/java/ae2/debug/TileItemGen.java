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

package ae2.debug;

import ae2.tile.AEBaseInvTile;
import ae2.util.inv.AppEngInternalInventory;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.Queue;

public class TileItemGen extends AEBaseInvTile {

    private static final Queue<ItemStack> SHARED_POSSIBLE_ITEMS = new ArrayDeque<>();

    private final AppEngInternalInventory inv = new AppEngInternalInventory(this, 16, 64);
    private final Queue<ItemStack> possibleItems = new ArrayDeque<>();
    private Item filter = Items.AIR;

    private static synchronized void initGlobalPossibleItems() {
        if (!SHARED_POSSIBLE_ITEMS.isEmpty()) {
            return;
        }

        for (Item item : ForgeRegistries.ITEMS) {
            addPossibleItem(item, SHARED_POSSIBLE_ITEMS);
        }
    }

    private static void addPossibleItem(Item item, Queue<ItemStack> queue) {
        if (item == null || item == Items.AIR) {
            return;
        }

        ItemStack sampleStack = new ItemStack(item);
        if (sampleStack.isItemStackDamageable()) {
            int maxDamage = sampleStack.getMaxDamage();
            for (int dmg = 0; dmg < maxDamage; dmg++) {
                ItemStack stack = sampleStack.copy();
                stack.setItemDamage(dmg);
                queue.add(stack);
            }
        } else {
            queue.add(sampleStack);
        }
    }

    @Override
    protected void clearRemoved() {
        super.clearRemoved();
        if (SHARED_POSSIBLE_ITEMS.isEmpty()) {
            initGlobalPossibleItems();
        }
        scheduleInit();
    }

    @Override
    public void onReady() {
        super.onReady();
        refillInv();
    }

    @Override
    public AppEngInternalInventory getInternalInventory() {
        return this.inv;
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        if (data.hasKey("filter", Constants.NBT.TAG_STRING)) {
            try {
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(data.getString("filter")));
                this.setItem(item == null ? Items.AIR : item);
            } catch (RuntimeException ignored) {
                this.setItem(Items.AIR);
            }
        }
        super.loadTag(data);
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        data.setString("filter", String.valueOf(ForgeRegistries.ITEMS.getKey(this.filter)));
    }

    public void setItem(Item item) {
        this.filter = item == null ? Items.AIR : item;
        this.possibleItems.clear();
        addPossibleItem(this.filter, this.possibleItems);
        refillInv();
        this.markForUpdate();
        this.saveChanges();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (inv.getStackInSlot(slot).isEmpty()) {
            refillSlot(slot);
        }
    }

    private Queue<ItemStack> getPossibleItems() {
        return this.filter != Items.AIR ? this.possibleItems : SHARED_POSSIBLE_ITEMS;
    }

    private void refillInv() {
        for (int slot = 0; slot < this.inv.size(); slot++) {
            refillSlot(slot);
        }
    }

    private void refillSlot(int slot) {
        ItemStack stack = getPossibleItems().poll();
        if (stack != null) {
            ItemStack copy = stack.copy();
            copy.setCount(copy.getMaxStackSize());
            this.inv.setItemDirect(slot, copy);
            getPossibleItems().add(stack);
        }
    }
}
