/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package ae2.parts.p2p;

import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.stacks.AEKeyType;
import ae2.core.AppEng;
import ae2.items.parts.PartModels;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class ItemP2PTunnelPart extends CapabilityP2PTunnelPart<ItemP2PTunnelPart, IItemHandler> {

    private static final P2PModels MODELS = new P2PModels(AppEng.makeId("part/p2p/p2p_tunnel_items"));
    private static final IItemHandler NULL_ITEM_HANDLER = new NullItemHandler();

    public ItemP2PTunnelPart(IPartItem<?> partItem) {
        super(partItem, CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
        inputHandler = new InputItemHandler();
        outputHandler = new OutputItemHandler();
        emptyHandler = NULL_ITEM_HANDLER;
    }

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(this.isPowered(), this.isActive());
    }

    private static class NullItemHandler implements IItemHandler {

        @Override
        public int getSlots() {
            return 0;
        }

        @Override
        public @NonNull ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public @NonNull ItemStack insertItem(int slot, @NonNull ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public @NonNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 0;
        }

        @Override
        public boolean isItemValid(int slot, @NonNull ItemStack stack) {
            return false;
        }
    }

    private class InputItemHandler implements IItemHandler {

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public @NonNull ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public @NonNull ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            int remainder = stack.getCount();

            final int outputTunnels = ItemP2PTunnelPart.this.getOutputs().size();
            final int amount = stack.getCount();

            if (outputTunnels == 0 || amount == 0) {
                return stack;
            }

            final int amountPerOutput = amount / outputTunnels;
            int overflow = amount % outputTunnels;

            for (ItemP2PTunnelPart target : ItemP2PTunnelPart.this.getOutputs()) {
                try (CapabilityGuard capabilityGuard = target.getAdjacentCapability()) {
                    final IItemHandler output = capabilityGuard.get();
                    final int toSend = amountPerOutput + overflow;

                    if (toSend <= 0) {
                        // Both overflow and amountPerOutput are 0, so they will be for further outputs as well.
                        break;
                    }

                    // So the documentation says that copying the stack should not be necessary because it is not
                    // supposed to be stored or modifed by insertItem. However, ItemStackHandler will gladly store
                    // the stack so we need to do a defensive copy. Forgecord says this is the intended behavior,
                    // and the documentation is wrong.
                    ItemStack stackCopy = stack.copy();
                    stackCopy.setCount(toSend);
                    final int sent = toSend - ItemHandlerHelper.insertItem(output, stackCopy, simulate).getCount();

                    overflow = toSend - sent;
                    remainder -= sent;
                }
            }

            if (!simulate) {
                deductTransportCost(amount - remainder, AEKeyType.items());
            }

            if (remainder == stack.getCount()) {
                return stack;
            } else if (remainder == 0) {
                return ItemStack.EMPTY;
            } else {
                ItemStack copy = stack.copy();
                copy.setCount(remainder);
                return copy;
            }
        }

        @Override
        public @NonNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, @NonNull ItemStack stack) {
            return true;
        }

    }

    private class OutputItemHandler implements IItemHandler {
        @Override
        public int getSlots() {
            try (CapabilityGuard input = getInputCapability()) {
                return input.get().getSlots();
            }
        }

        @Override
        public @NonNull ItemStack getStackInSlot(int slot) {
            try (CapabilityGuard input = getInputCapability()) {
                return input.get().getStackInSlot(slot);
            }
        }

        @Override
        public @NonNull ItemStack insertItem(int slot, @NonNull ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public @NonNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            try (CapabilityGuard input = getInputCapability()) {
                ItemStack result = input.get().extractItem(slot, amount, simulate);

                if (!simulate) {
                    deductTransportCost(result.getCount(), AEKeyType.items());
                }

                return result;
            }
        }

        @Override
        public int getSlotLimit(int slot) {
            try (CapabilityGuard input = getInputCapability()) {
                return input.get().getSlotLimit(slot);
            }
        }

        @Override
        public boolean isItemValid(int slot, @NonNull ItemStack stack) {
            try (CapabilityGuard input = getInputCapability()) {
                return input.get().isItemValid(slot, stack);
            }
        }
    }
}
