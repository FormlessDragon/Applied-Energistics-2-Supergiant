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
import ae2.api.config.PatternProviderInsertionMode;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEKey;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;
import java.util.Set;

public interface PatternProviderTarget {
    @Nullable
    static PatternProviderTarget get(World level, BlockPos pos, EnumFacing side, IActionSource src) {
        return PatternProviderTargets.get(level, pos, side, src);
    }

    long insert(AEKey what, long amount, Actionable type);

    long insert(AEKey what, long amount, Actionable type, PatternProviderInsertionMode insertionMode);

    boolean containsPatternInput(Set<AEKey> patternInputs);

    boolean containsAnyStack();

    boolean hasEmptySlots();
}
