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

import ae2.api.behaviors.ExternalStorageStrategy;
import ae2.api.config.PatternProviderInsertionMode;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEKeyType;
import ae2.parts.automation.StackWorldBehaviors;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.items.IItemHandler;

import org.jetbrains.annotations.Nullable;
import java.util.Map;

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
        return PatternProviderTargets.get(this.level, this.pos, this.side, this.src, this.strategies);
    }
}
