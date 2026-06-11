/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
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

package ae2.helpers.externalstorage;

import ae2.api.behaviors.GenericInternalInventory;
import ae2.api.behaviors.GenericSlotCapacities;
import ae2.api.config.Actionable;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.AEKeyTypes;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.AEKeySlotFilter;
import ae2.api.storage.MEStorage;
import ae2.core.AELog;
import ae2.core.localization.GuiText;
import ae2.util.ConfigGuiInventory;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2LongArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class GenericStackInv implements MEStorage, GenericInternalInventory {
    protected final GenericStack[] stacks;
    protected final Mode mode;
    private final Runnable listener;
    private final Reference2LongMap<AEKeyType> capacities = new Reference2LongArrayMap<>();
    private final Set<AEKeyType> supportedKeyTypes;
    private boolean suppressOnChange;
    private boolean onChangeSuppressed;
    @Nullable
    private AEKeySlotFilter filter;
    private TextComponentTranslation description = new TextComponentTranslation(GuiText.Nothing.getTranslationKey());

    public GenericStackInv(@Nullable Runnable listener, int size) {
        this(listener, Mode.STORAGE, size);
    }

    public GenericStackInv(@Nullable Runnable listener, Mode mode, int size) {
        this(AEKeyTypes.getAll(), listener, mode, size);
    }

    public GenericStackInv(Set<AEKeyType> supportedKeyTypes, @Nullable Runnable listener, Mode mode, int size) {
        this.supportedKeyTypes = Set.copyOf(Objects.requireNonNull(supportedKeyTypes, "supportedKeyTypes"));
        this.stacks = new GenericStack[size];
        this.listener = listener;
        this.mode = mode;
    }

    @Nullable
    public AEKeySlotFilter getFilter() {
        return filter;
    }

    protected void setFilter(@Nullable AEKeySlotFilter filter) {
        this.filter = filter;
    }

    @Override
    public boolean isSupportedType(AEKeyType type) {
        return supportedKeyTypes.contains(type);
    }

    @Override
    public int size() {
        return stacks.length;
    }

    public boolean isEmpty() {
        for (var stack : stacks) {
            if (stack != null) {
                return false;
            }
        }
        return true;
    }

    @Override
    @Nullable
    public GenericStack getStack(int slot) {
        return stacks[slot];
    }

    @Override
    @Nullable
    public AEKey getKey(int slot) {
        return stacks[slot] != null ? stacks[slot].what() : null;
    }

    @Override
    public long getAmount(int slot) {
        return stacks[slot] != null ? stacks[slot].amount() : 0;
    }

    @Override
    public void setStack(int slot, @Nullable GenericStack stack) {
        if (stack != null && getMaxAmount(stack.what()) < stack.amount()) {
            stack = new GenericStack(stack.what(), getMaxAmount(stack.what()));
        }
        if (!Objects.equals(stacks[slot], stack)) {
            stacks[slot] = stack;
            onChange();
        }
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        return result < 0 ? Long.MAX_VALUE : result;
    }

    @Override
    public long insert(int slot, AEKey what, long amount, Actionable mode) {
        Objects.requireNonNull(what, "what");
        Preconditions.checkArgument(amount >= 0, "amount >= 0");

        if (!canInsert() || !isAllowedIn(slot, what)) {
            return 0;
        }

        var currentWhat = getKey(slot);
        var currentAmount = getAmount(slot);
        if (currentWhat == null || currentWhat.equals(what)) {
            var newAmount = Math.min(saturatingAdd(currentAmount, amount), getMaxAmount(what));
            if (newAmount > currentAmount) {
                if (mode == Actionable.MODULATE) {
                    setStack(slot, new GenericStack(what, newAmount));
                    newAmount = getAmount(slot);
                }
                return newAmount - currentAmount;
            }
        }
        return 0;
    }

    @Override
    public boolean isAllowedIn(int slot, AEKey what) {
        return isSupportedType(what) && (filter == null || filter.isAllowed(slot, what));
    }

    @Override
    public long extract(int slot, AEKey what, long amount, Actionable mode) {
        Objects.requireNonNull(what, "what");
        Preconditions.checkArgument(amount >= 0, "amount >= 0");

        var currentWhat = getKey(slot);
        if (!canExtract() || currentWhat == null || !currentWhat.equals(what)) {
            return 0;
        }

        var currentAmount = getAmount(slot);
        var canExtract = Math.min(currentAmount, amount);

        if (canExtract > 0 && mode == Actionable.MODULATE) {
            var newAmount = currentAmount - canExtract;
            if (newAmount <= 0) {
                setStack(slot, null);
            } else {
                setStack(slot, new GenericStack(what, newAmount));
            }
            var reallyExtracted = Math.max(0, currentAmount - getAmount(slot));
            if (reallyExtracted != canExtract) {
                AELog.warn(
                    "GenericStackInv simulation/modulation extraction mismatch: canExtract=%d, reallyExtracted=%d",
                    canExtract, reallyExtracted);
                canExtract = reallyExtracted;
            }
        }

        return canExtract;
    }

    @Override
    public long getCapacity(AEKeyType space) {
        return capacities.getOrDefault(space, Long.MAX_VALUE);
    }

    @Override
    public boolean canInsert() {
        return true;
    }

    @Override
    public boolean canExtract() {
        return true;
    }

    public void setCapacity(AEKeyType space, long capacity) {
        this.capacities.put(space, capacity);
    }

    public void useRegisteredCapacities() {
        for (var entry : GenericSlotCapacities.getMap().reference2LongEntrySet()) {
            setCapacity(entry.getKey(), entry.getLongValue());
        }
    }

    @Override
    public long getMaxAmount(AEKey key) {
        if (key instanceof AEItemKey itemKey) {
            return Math.min(itemKey.getMaxStackSize(), getCapacity(key.getType()));
        }
        return getCapacity(key.getType());
    }

    @Override
    public final void onChange() {
        if (!suppressOnChange) {
            notifyListener();
        } else {
            onChangeSuppressed = true;
        }
    }

    protected void notifyListener() {
        if (listener != null) {
            listener.run();
        }
    }

    public NBTTagList writeToTag() {
        List<GenericStack> stacks = new ObjectArrayList<>(this.stacks.length);
        Collections.addAll(stacks, this.stacks);
        return GenericStack.writeList(stacks);
    }

    public void writeToChildTag(NBTTagCompound tag, String name) {
        boolean isEmpty = true;
        for (var stack : stacks) {
            if (stack != null) {
                isEmpty = false;
                break;
            }
        }

        if (!isEmpty) {
            tag.setTag(name, writeToTag());
        } else {
            tag.removeTag(name);
        }
    }

    public void readFromTag(NBTTagList tag) {
        var decoded = GenericStack.readList(tag);
        boolean changed = false;
        for (int i = 0; i < size(); i++) {
            GenericStack stack = i < decoded.size() ? decoded.get(i) : null;
            if (!Objects.equals(stack, stacks[i])) {
                stacks[i] = stack;
                changed = true;
            }
        }
        if (changed) {
            onChange();
        }
    }

    public void clear() {
        boolean changed = false;
        for (int i = 0; i < stacks.length; i++) {
            changed |= stacks[i] != null;
            stacks[i] = null;
        }
        if (changed) {
            onChange();
        }
    }

    public void readFromChildTag(NBTTagCompound tag, String name) {
        if (tag.hasKey(name, Constants.NBT.TAG_LIST)) {
            readFromTag(tag.getTagList(name, Constants.NBT.TAG_COMPOUND));
        } else {
            clear();
        }
    }

    public void readFromList(List<@Nullable GenericStack> stacks) {
        for (var i = 0; i < size(); i++) {
            if (i < stacks.size()) {
                setStack(i, stacks.get(i));
            } else {
                setStack(i, null);
            }
        }
    }

    public List<@Nullable GenericStack> toList() {
        List<GenericStack> result = new ObjectArrayList<>(size());
        for (int i = 0; i < size(); i++) {
            result.add(getStack(i));
        }
        return result;
    }

    @Override
    public void beginBatch() {
        Preconditions.checkState(!suppressOnChange, "beginBatch was called without endBatch");
        suppressOnChange = true;
    }

    @Override
    public void endBatch() {
        Preconditions.checkState(suppressOnChange, "endBatch was called without beginBatch");
        suppressOnChange = false;
        if (onChangeSuppressed) {
            onChangeSuppressed = false;
            onChange();
        }
    }

    @Override
    public void endBatchSuppressed() {
        Preconditions.checkState(suppressOnChange, "endBatch was called without beginBatch");
        suppressOnChange = false;
        onChangeSuppressed = false;
    }

    public Mode getMode() {
        return mode;
    }

    public ConfigGuiInventory createGuiWrapper() {
        return new ConfigGuiInventory(this);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        Objects.requireNonNull(what, "what");
        Preconditions.checkArgument(amount >= 0, "amount >= 0");
        if (!isSupportedType(what)) {
            return 0;
        }

        if (this.mode == Mode.CONFIG_TYPES) {
            int freeSlot = -1;
            for (int i = 0; i < stacks.length; i++) {
                var key = getKey(i);
                if (key == what) {
                    return 0;
                } else if (key == null && freeSlot == -1) {
                    freeSlot = i;
                }
            }
            if (freeSlot != -1 && mode == Actionable.MODULATE) {
                setStack(freeSlot, new GenericStack(what, 0));
            }
            return 0;
        }

        var inserted = 0L;
        for (int i = 0; i < stacks.length && inserted < amount; i++) {
            inserted += insert(i, what, amount - inserted, mode);
        }
        return inserted;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        Objects.requireNonNull(what, "what");
        Preconditions.checkArgument(amount >= 0, "amount >= 0");

        var extracted = 0L;
        for (int i = 0; i < stacks.length && extracted < amount; i++) {
            extracted += extract(i, what, amount - extracted, mode);
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (var stack : stacks) {
            if (stack != null) {
                out.add(stack.what(), stack.amount());
            }
        }
    }

    @Override
    public ITextComponent getDescription() {
        return description;
    }

    public void setDescription(TextComponentTranslation description) {
        this.description = description;
    }

    public enum Mode {
        CONFIG_TYPES,
        CONFIG_STACKS,
        STORAGE
    }
}
