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

package ae2.helpers.patternprovider;

import ae2.api.AECapabilities;
import ae2.api.behaviors.ExternalStorageStrategy;
import ae2.api.config.Actionable;
import ae2.api.config.PatternProviderInsertionMode;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.storage.MEStorage;
import ae2.me.storage.CompositeStorage;
import ae2.parts.automation.StackWorldBehaviors;
import com.google.common.primitives.Ints;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

class PatternProviderTargetCache {
    private final WorldServer level;
    private final BlockPos pos;
    private final EnumFacing side;
    private final IActionSource src;
    private final Map<AEKeyType, ExternalStorageStrategy> strategies;

    PatternProviderTargetCache(WorldServer level, BlockPos pos, EnumFacing side, IActionSource src) {
        this.level = level;
        this.pos = pos;
        this.side = side;
        this.src = src;
        this.strategies = StackWorldBehaviors.createExternalStorageStrategies(level, pos, side);
    }

    static ItemStack insertIntoItemHandler(IItemHandler handler, ItemStack stack, boolean simulate,
                                           PatternProviderInsertionMode mode) {
        return switch (mode) {
            case DEFAULT -> insertIntoAllItemSlots(handler, stack, simulate);
            case PREFER_EMPTY -> insertIntoAllItemSlots(handler, insertIntoEmptyItemSlots(handler, stack, simulate),
                simulate);
            case EMPTY_ONLY -> insertIntoEmptyItemSlots(handler, stack, simulate);
        };
    }

    private static ItemStack insertIntoAllItemSlots(IItemHandler handler, ItemStack stack, boolean simulate) {
        ItemStack remaining = stack;
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = handler.insertItem(slot, remaining, simulate);
        }
        return remaining;
    }

    private static ItemStack insertIntoEmptyItemSlots(IItemHandler handler, ItemStack stack, boolean simulate) {
        ItemStack remaining = stack;
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            if (handler.getStackInSlot(slot).isEmpty()) {
                remaining = handler.insertItem(slot, remaining, simulate);
            }
        }
        return remaining;
    }

    @Nullable
    PatternProviderTarget find() {
        TileEntity blockEntity = this.level.getTileEntity(this.pos);
        MEStorage storage = null;
        if (blockEntity != null && blockEntity.hasCapability(AECapabilities.ME_STORAGE, this.side)) {
            storage = blockEntity.getCapability(AECapabilities.ME_STORAGE, this.side);
        }

        if (storage != null) {
            return wrapMeStorage(storage);
        }

        Reference2ObjectMap<AEKeyType, PatternProviderTarget> externalTargets = new Reference2ObjectOpenHashMap<>(
            this.strategies.size());
        if (blockEntity != null && blockEntity.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, this.side)) {
            IItemHandler itemHandler = blockEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
                this.side);
            if (itemHandler != null) {
                externalTargets.put(AEKeyType.items(), wrapItemHandler(itemHandler));
            }
        }

        Reference2ObjectMap<AEKeyType, MEStorage> externalStorages = new Reference2ObjectOpenHashMap<>(
            this.strategies.size());
        for (Entry<AEKeyType, ExternalStorageStrategy> entry : this.strategies.entrySet()) {
            if (externalTargets.containsKey(entry.getKey())) {
                continue;
            }

            MEStorage wrapper = entry.getValue().createWrapper(false, () -> {
            });
            if (wrapper != null) {
                externalStorages.put(entry.getKey(), wrapper);
            }
        }

        if (!externalStorages.isEmpty()) {
            PatternProviderTarget storageTarget = wrapMeStorage(new CompositeStorage(externalStorages));
            for (Entry<AEKeyType, MEStorage> entry : externalStorages.entrySet()) {
                externalTargets.put(entry.getKey(), storageTarget);
            }
        }

        if (!externalTargets.isEmpty()) {
            return wrapTargets(externalTargets);
        }

        return null;
    }

    private PatternProviderTarget wrapTargets(Reference2ObjectMap<AEKeyType, PatternProviderTarget> targets) {
        return new PatternProviderTarget() {
            @Override
            public long insert(AEKey what, long amount, Actionable type) {
                return insert(what, amount, type, PatternProviderInsertionMode.DEFAULT);
            }

            @Override
            public long insert(AEKey what, long amount, Actionable type, PatternProviderInsertionMode insertionMode) {
                PatternProviderTarget target = targets.get(what.getType());
                return target == null ? 0 : target.insert(what, amount, type, insertionMode);
            }

            @Override
            public boolean containsPatternInput(Set<AEKey> patternInputs) {
                for (PatternProviderTarget target : targets.values()) {
                    if (target.containsPatternInput(patternInputs)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean containsAnyStack() {
                for (PatternProviderTarget target : targets.values()) {
                    if (target.containsAnyStack()) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean hasEmptySlots() {
                for (PatternProviderTarget target : targets.values()) {
                    if (target.hasEmptySlots()) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    private PatternProviderTarget wrapItemHandler(IItemHandler handler) {
        return new PatternProviderTarget() {
            @Override
            public long insert(AEKey what, long amount, Actionable type) {
                return insert(what, amount, type, PatternProviderInsertionMode.DEFAULT);
            }

            @Override
            public long insert(AEKey what, long amount, Actionable type, PatternProviderInsertionMode insertionMode) {
                if (!(what instanceof AEItemKey itemKey)) {
                    return 0;
                }

                ItemStack input = itemKey.toStack(Ints.saturatedCast(amount));
                ItemStack remaining = insertIntoItemHandler(handler, input, type == Actionable.SIMULATE,
                    insertionMode);
                return amount - remaining.getCount();
            }

            @Override
            public boolean containsPatternInput(Set<AEKey> patternInputs) {
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    AEItemKey what = AEItemKey.of(handler.getStackInSlot(slot));
                    if (what != null && patternInputs.contains(what.dropSecondary())) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean containsAnyStack() {
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    if (!handler.getStackInSlot(slot).isEmpty()) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean hasEmptySlots() {
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    if (handler.getStackInSlot(slot).isEmpty()) {
                        return true;
                    }
                }
                return false;
            }

        };
    }

    private PatternProviderTarget wrapMeStorage(MEStorage storage) {
        return new PatternProviderTarget() {
            @Override
            public long insert(AEKey what, long amount, Actionable type) {
                return insert(what, amount, type, PatternProviderInsertionMode.DEFAULT);
            }

            @Override
            public long insert(AEKey what, long amount, Actionable type, PatternProviderInsertionMode insertionMode) {
                return storage.insert(what, amount, type, src);
            }

            @Override
            public boolean containsPatternInput(Set<AEKey> patternInputs) {
                for (Object2LongMap.Entry<AEKey> stack : storage.getAvailableStacks()) {
                    if (patternInputs.contains(stack.getKey().dropSecondary())) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean containsAnyStack() {
                return !storage.getAvailableStacks().isEmpty();
            }

            @Override
            public boolean hasEmptySlots() {
                return false;
            }
        };
    }
}
