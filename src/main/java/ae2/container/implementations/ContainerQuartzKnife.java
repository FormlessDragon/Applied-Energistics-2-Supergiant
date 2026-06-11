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

import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.api.inventories.InternalInventory;
import ae2.container.AEBaseContainer;
import ae2.container.SlotSemantics;
import ae2.container.slot.OutputSlot;
import ae2.container.slot.RestrictedInputSlot;
import ae2.core.definitions.AEItems;
import ae2.util.Platform;
import ae2.util.inv.AppEngInternalInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.ITextComponent.Serializer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;

public class ContainerQuartzKnife extends AEBaseContainer {
    private static final String NAME_PRESS_NAME_TAG = "name_press_name";
    private static final int MAX_NAME_LENGTH = 32;
    private final InternalInventory input = new AppEngInternalInventory(null, 1, 1);
    private String currentName = "";

    public ContainerQuartzKnife(InventoryPlayer playerInventory, ItemGuiHost<?> host) {
        super(playerInventory, host);

        this.addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.METAL_INGOTS, this.input, 0),
            SlotSemantics.MACHINE_INPUT);
        this.addSlot(new QuartzKnifeSlot(this.input), SlotSemantics.MACHINE_OUTPUT);
        this.addPlayerInventorySlots(8, 84);
        registerClientAction("setName", String.class, this::setName);
    }

    public void setName(String value) {
        if (value != null && value.length() > MAX_NAME_LENGTH) {
            return;
        }
        this.currentName = value;
        if (isClientSide()) {
            sendClientAction("setName", value);
        }
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        ItemStack item = this.input.extractItem(0, Integer.MAX_VALUE, false);
        if (!item.isEmpty()) {
            player.dropItem(item, false);
        }
    }

    private class QuartzKnifeSlot extends OutputSlot {
        QuartzKnifeSlot(InternalInventory inv) {
            super(inv, 0, 0, 0, null);
        }

        @Override
        public ItemStack getStack() {
            ItemStack inputStack = this.getInventory().getStackInSlot(0);
            if (inputStack.isEmpty() || currentName.isBlank()) {
                return ItemStack.EMPTY;
            }

            ItemStack namePressStack = AEItems.NAME_PRESS.stack();
            ITextComponent name = new TextComponentString(currentName);
            Platform.openNbtData(namePressStack).setString(NAME_PRESS_NAME_TAG, Serializer.componentToJson(name));
            return namePressStack;
        }

        @Override
        public ItemStack decrStackSize(int amount) {
            ItemStack ret = this.getStack();
            if (!ret.isEmpty()) {
                this.makePlate();
            }
            return ret;
        }

        @Override
        public void putStack(ItemStack stack) {
            if (stack.isEmpty()) {
                this.makePlate();
            }
        }

        private void makePlate() {
            if (!this.getInventory().extractItem(0, 1, false).isEmpty()) {
                ItemGuiHost<?> host = ContainerQuartzKnife.this.getItemGuiHost();
                if (host == null) {
                    return;
                }
                ItemStack item = host.getItemStack();
                InventoryPlayer playerInv = ContainerQuartzKnife.this.getPlayerInventory();
                Integer slotIndex = host.getPlayerInventorySlot();
                if (slotIndex != null) {
                    ItemStack before = item.copy();
                    item.setItemDamage(item.getItemDamage() + 1);
                    if (item.getItemDamage() >= item.getMaxDamage()) {
                        playerInv.setInventorySlotContents(slotIndex, ItemStack.EMPTY);
                        MinecraftForge.EVENT_BUS.post(new PlayerDestroyItemEvent(playerInv.player, before, null));
                    } else {
                        playerInv.setInventorySlotContents(slotIndex, item);
                    }
                }
                ContainerQuartzKnife.this.broadcastChanges();
            }
        }
    }
}
