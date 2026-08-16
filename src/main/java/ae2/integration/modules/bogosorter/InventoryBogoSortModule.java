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

package ae2.integration.modules.bogosorter;

import ae2.container.AEBaseContainer;
import ae2.container.implementations.ContainerSkyChest;
import ae2.container.me.common.ContainerMEStorage;
import ae2.container.me.items.ContainerBasicCellChest;
import ae2.container.me.items.ContainerCraftingTerm;
import ae2.container.me.patternencode.ContainerPatternEncodingTerm;
import ae2.container.me.items.ContainerWirelessCraftingTerm;
import ae2.container.slot.DisabledSlot;
import ae2.core.AELog;
import com.cleanroommc.bogosorter.api.IBogoSortAPI;
import com.cleanroommc.bogosorter.api.IButtonPos;
import com.cleanroommc.bogosorter.api.IPosSetter;
import com.cleanroommc.bogosorter.api.ISlot;
import com.cleanroommc.bogosorter.api.ISlotGroup;
import com.cleanroommc.bogosorter.api.ISortingContextBuilder;
import com.cleanroommc.bogosorter.common.sort.SortHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public final class InventoryBogoSortModule {

    private static final boolean LOADED = Loader.isModLoaded("bogosorter");

    private InventoryBogoSortModule() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static @Nullable Comparator<ItemStack> getComparator() {
        return LOADED ? SortHandler.getClientItemComparator() : null;
    }

    public static void init() {
        if (!LOADED) {
            return;
        }
        Registration.init();
    }

    private static final class Registration {
        private static final List<ISlot> ME_NETWORK_TARGET_SLOTS = List.of(
            new MeNetworkInsertSlot(0),
            new MeNetworkInsertSlot(1));
        private static final IInventory ME_NETWORK_TARGET_INVENTORY =
            new InventoryBasic("AE2 ME network BogoSorter target", false, 0);
        private static final IPosSetter DISABLE_SORT_BUTTON = Registration::disableSortButton;

        private Registration() {
        }

        private static void init() {
            IBogoSortAPI api = IBogoSortAPI.getInstance();
            api.addSlotGetter(Slot.class, LockedAwareSlot::new);
            api.addSlotGetter(DisabledSlot.class, LockedAwareSlot::new);
            api.addCompat(ContainerSkyChest.class, (container, builder) -> builder.addSlotGroup(0, 36, 9));
            registerTerminal(api, ContainerMEStorage.class);
            registerTerminal(api, ContainerBasicCellChest.class);
            registerTerminal(api, ContainerCraftingTerm.class);
            registerTerminal(api, ContainerWirelessCraftingTerm.class);
            registerTerminal(api, ContainerPatternEncodingTerm.class);
        }

        private static <T extends ContainerMEStorage> void registerTerminal(IBogoSortAPI api, Class<T> containerClass) {
            api.addCompat(containerClass, Registration::addMeNetworkTargetGroup);
            api.addCustomInsertable(containerClass, Registration::insertIntoMeNetwork);
        }

        private static void addMeNetworkTargetGroup(ContainerMEStorage container, ISortingContextBuilder builder) {
            builder.addSlotGroup(ME_NETWORK_TARGET_SLOTS, ME_NETWORK_TARGET_SLOTS.size())
                   .buttonPosSetter(DISABLE_SORT_BUTTON);
        }

        private static void disableSortButton(@SuppressWarnings("unused") ISlotGroup group, IButtonPos buttonPos) {
            buttonPos.setEnabled(false);
        }

        private static ItemStack insertIntoMeNetwork(Container container,
                                                     @SuppressWarnings("unused") List<ISlot> targetSlots,
                                                     ItemStack input,
                                                     @SuppressWarnings("unused") boolean fillEmptySlot) {
            if (container instanceof ContainerMEStorage terminal) {
                return terminal.insertBogoSorterShortcutStack(input);
            }

            String message = "BogoSorter ME insertable received unsupported container: "
                + container.getClass().getName();
            AELog.warn(message);
            throw new IllegalArgumentException(message);
        }

        private static final class LockedAwareSlot implements ISlot {
            private final Slot slot;
            private final boolean lockedByAeContainer;

            private LockedAwareSlot(Slot slot) {
                this.slot = slot;
                this.lockedByAeContainer = isLockedByAeContainer(slot);
            }

            private static boolean isLockedByAeContainer(Slot slot) {
                if (!(slot.inventory instanceof InventoryPlayer inventory)) {
                    return false;
                }
                return inventory.player.openContainer instanceof AEBaseContainer container
                    && container.isPlayerInventorySlotLocked(slot);
            }

            @Override
            public Slot bogo$getRealSlot() {
                return this.slot;
            }

            @Override
            public int bogo$getX() {
                return this.slot.xPos;
            }

            @Override
            public int bogo$getY() {
                return this.slot.yPos;
            }

            @Override
            public int bogo$getSlotNumber() {
                return this.slot.slotNumber;
            }

            @Override
            public int bogo$getSlotIndex() {
                return this.slot.getSlotIndex();
            }

            @Override
            public IInventory bogo$getInventory() {
                return this.slot.inventory;
            }

            @Override
            public void bogo$putStack(ItemStack itemStack) {
                if (!this.lockedByAeContainer) {
                    this.slot.putStack(itemStack);
                }
            }

            @Override
            public ItemStack bogo$getStack() {
                return this.lockedByAeContainer ? ItemStack.EMPTY : this.slot.getStack();
            }

            @Override
            public int bogo$getMaxStackSize(ItemStack itemStack) {
                return this.lockedByAeContainer ? 0 : this.slot.getSlotStackLimit();
            }

            @Override
            public int bogo$getItemStackLimit(ItemStack itemStack) {
                return this.lockedByAeContainer ? 0 : this.slot.getItemStackLimit(itemStack);
            }

            @Override
            public boolean bogo$isEnabled() {
                return !this.lockedByAeContainer && this.slot.isEnabled();
            }

            @Override
            public boolean bogo$isItemValid(ItemStack itemStack) {
                return !this.lockedByAeContainer && this.slot.isItemValid(itemStack);
            }

            @Override
            public boolean bogo$canTakeStack(EntityPlayer entityPlayer) {
                return !this.lockedByAeContainer && this.slot.canTakeStack(entityPlayer);
            }

            @Override
            public void bogo$onSlotChanged() {
                if (!this.lockedByAeContainer) {
                    this.slot.onSlotChanged();
                }
            }

            @Override
            public void bogo$onSlotChanged(ItemStack itemStack, ItemStack itemStack1) {
                if (!this.lockedByAeContainer) {
                    this.slot.onSlotChange(itemStack, itemStack1);
                }
            }

            @Override
            public ItemStack bogo$onTake(EntityPlayer entityPlayer, ItemStack itemStack) {
                return this.lockedByAeContainer ? ItemStack.EMPTY : this.slot.onTake(entityPlayer, itemStack);
            }
        }

        private record MeNetworkInsertSlot(int index) implements ISlot {
            private static final int SLOT_NUMBER_BASE = Integer.MIN_VALUE;

            @Override
            public Slot bogo$getRealSlot() {
                throw unsupported("real slot access");
            }

            @Override
            public int bogo$getX() {
                return 0;
            }

            @Override
            public int bogo$getY() {
                return 0;
            }

            @Override
            public int bogo$getSlotNumber() {
                return SLOT_NUMBER_BASE + this.index;
            }

            @Override
            public int bogo$getSlotIndex() {
                return -1;
            }

            @Override
            public IInventory bogo$getInventory() {
                return ME_NETWORK_TARGET_INVENTORY;
            }

            @Override
            public void bogo$putStack(ItemStack itemStack) {
                throw unsupported("direct stack mutation");
            }

            @Override
            public ItemStack bogo$getStack() {
                // The slot only describes a non-player ME network target for custom insertion.
                return ItemStack.EMPTY;
            }

            @Override
            public int bogo$getMaxStackSize(ItemStack itemStack) {
                return itemStack.getMaxStackSize();
            }

            @Override
            public int bogo$getItemStackLimit(ItemStack itemStack) {
                return itemStack.getMaxStackSize();
            }

            @Override
            public boolean bogo$isEnabled() {
                return true;
            }

            @Override
            public boolean bogo$isItemValid(ItemStack itemStack) {
                return true;
            }

            @Override
            public boolean bogo$canTakeStack(EntityPlayer entityPlayer) {
                return false;
            }

            @Override
            public void bogo$onSlotChanged() {
                // Sorting and shortcut insertion never mutate this virtual slot.
            }

            @Override
            public void bogo$onSlotChanged(ItemStack itemStack, ItemStack itemStack1) {
                // Sorting and shortcut insertion never mutate this virtual slot.
            }

            @Override
            public ItemStack bogo$onTake(EntityPlayer entityPlayer, ItemStack itemStack) {
                throw unsupported("take callback");
            }

            private UnsupportedOperationException unsupported(String operation) {
                return new UnsupportedOperationException(
                    "BogoSorter ME network virtual target slot does not support " + operation);
            }
        }
    }
}
