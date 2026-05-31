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

import ae2.api.config.Actionable;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.MEStorage;
import ae2.helpers.externalstorage.GenericStackInv;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.Consumer;

public class PatternProviderReturnInventory extends GenericStackInv {
    public static final int NUMBER_OF_SLOTS = 9;

    private boolean injectingIntoNetwork;

    public PatternProviderReturnInventory(Runnable listener) {
        super(listener, NUMBER_OF_SLOTS);
        useRegisteredCapacities();
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canInsert() {
        return !injectingIntoNetwork;
    }

    public boolean injectIntoNetwork(MEStorage storage, IActionSource src, Consumer<GenericStack> insertionCallback) {
        boolean didSomething = false;
        boolean changed = false;
        this.injectingIntoNetwork = true;

        try {
            for (int i = 0; i < this.stacks.length; ++i) {
                GenericStack stack = this.stacks[i];
                if (stack == null) {
                    continue;
                }

                long sizeBefore = stack.amount();
                long inserted = storage.insert(stack.what(), stack.amount(), Actionable.MODULATE, src);
                if (inserted >= stack.amount()) {
                    this.stacks[i] = null;
                } else {
                    this.stacks[i] = new GenericStack(stack.what(), stack.amount() - inserted);
                }

                inserted = Math.max(0, sizeBefore - GenericStack.getStackSizeOrZero(this.stacks[i]));
                if (inserted > 0) {
                    didSomething = true;
                    changed = true;
                    insertionCallback.accept(new GenericStack(stack.what(), inserted));
                }
            }
        } finally {
            this.injectingIntoNetwork = false;
            if (changed) {
                this.notifyListener();
            }
        }

        return didSomething;
    }

    public void addDrops(List<ItemStack> drops, World level, BlockPos pos) {
        for (GenericStack stack : this.stacks) {
            if (stack != null) {
                stack.what().addDrops(stack.amount(), drops, level, pos);
            }
        }
    }
}
