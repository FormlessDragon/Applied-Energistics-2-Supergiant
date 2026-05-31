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
package ae2.tile.misc;

import ae2.api.config.Actionable;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.MEStorage;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

class CondenserMEStorage implements MEStorage {
    private final TileCondenser target;

    CondenserMEStorage(TileCondenser target) {
        this.target = target;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        if (!target.canAddOutput()) {
            return 0;
        }
        if (mode == Actionable.MODULATE) {
            this.target.addPower((double) amount / what.getAmountPerOperation());
        }
        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        var slotItem = this.target.getOutputSlot().getStackInSlot(0);

        if (what instanceof AEItemKey itemKey && itemKey.matches(slotItem)) {
            int count = (int) Math.min(amount, Integer.MAX_VALUE);
            return this.target.getOutputSlot().extractItem(0, count, mode == Actionable.SIMULATE).getCount();
        }

        return 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        var stack = this.target.getOutputSlot().getStackInSlot(0);
        if (!stack.isEmpty()) {
            out.add(AEItemKey.of(stack), stack.getCount());
        }
    }

    @Override
    public ITextComponent getDescription() {
        return new TextComponentTranslation(target.getBlockType().getTranslationKey() + ".name");
    }
}
