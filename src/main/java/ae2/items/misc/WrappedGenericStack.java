/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2018, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.items.misc;

import ae2.api.behaviors.ContainerItemStrategies;
import ae2.api.config.Actionable;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.core.definitions.AEItems;
import ae2.items.AEBaseItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static ae2.api.stacks.GenericStack.AMOUNT_FIELD;

/**
 * Wraps a {@link GenericStack} in an {@link ItemStack}. Even stacks that actually represent vanilla {@link Item items}
 * will be wrapped in this item, to allow items with amount 0 to be represented as itemstacks without becoming the empty
 * item.
 */
public class WrappedGenericStack extends AEBaseItem implements GenericStackHolderItem {
    private static final String WRAPPED_STACK = "wrapped_stack";

    public WrappedGenericStack() {
        setMaxStackSize(1);
    }

    public static ItemStack wrap(GenericStack stack) {
        Objects.requireNonNull(stack, "stack");
        var item = AEItems.WRAPPED_GENERIC_STACK.asItem();
        var result = new ItemStack(item);
        result.setTagInfo(WRAPPED_STACK, GenericStack.writeTag(stack));
        return result;
    }

    public static ItemStack wrap(AEKey what, long amount) {
        Objects.requireNonNull(what, "what");
        return wrap(new GenericStack(what, amount));
    }

    @Nullable
    public AEKey unwrapWhat(ItemStack stack) {
        return AEKey.fromTagGeneric(getWrapped(stack));
    }

    public long unwrapAmount(ItemStack stack) {
        var wrapped = getWrapped(stack);
        return wrapped == null ? 0 : Math.max(0, wrapped.getLong(AMOUNT_FIELD));
    }

    @Override
    public boolean onOtherStackedOnMe(ItemStack stack, ItemStack otherStack, Slot slot, EntityPlayer player) {
        var what = unwrapWhat(stack);
        if (what == null && slot.getStack() == stack) {
            slot.putStack(ItemStack.EMPTY);
            return true;
        }

        if (what == null) {
            return true;
        }

        var heldContainer = ContainerItemStrategies.findCarriedContextForKey(what, player, player.openContainer);
        if (heldContainer != null) {
            long amount = unwrapAmount(stack);
            long inserted = heldContainer.insert(what, amount, Actionable.MODULATE);
            heldContainer.playFillSound(player, what);

            if (inserted >= amount) {
                slot.putStack(ItemStack.EMPTY);
            } else {
                slot.putStack(wrap(what, amount - inserted));
            }
        }

        return true;
    }

    @Nullable
    private NBTTagCompound getWrapped(ItemStack stack) {
        if (stack.getItem() != this) {
            return null;
        }

        var tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(WRAPPED_STACK, 10)) {
            return null;
        }

        return tag.getCompoundTag(WRAPPED_STACK);
    }

    @Nullable
    public GenericStack unwrap(ItemStack stack) {
        NBTTagCompound wrapped = getWrapped(stack);
        GenericStack result = wrapped == null ? null : GenericStack.readTag(wrapped);
        if (result == null || result.amount() >= 0) {
            return result;
        }
        return new GenericStack(result.what(), 0);
    }

    @Nullable
    @Override
    public GenericStack getGenericStack(ItemStack stack) {
        return unwrap(stack);
    }
}
