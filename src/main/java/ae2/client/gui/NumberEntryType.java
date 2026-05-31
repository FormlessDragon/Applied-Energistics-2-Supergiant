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

package ae2.client.gui;

import ae2.api.config.PowerUnit;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import org.jetbrains.annotations.Nullable;

public record NumberEntryType(int amountPerUnit, @Nullable String unit) {
    public static final NumberEntryType ENERGY = new NumberEntryType(1, PowerUnit.AE.getSymbolName());
    public static final NumberEntryType UNITLESS = new NumberEntryType(1, null);

    public static NumberEntryType of(@Nullable AEKey key) {
        if (key == null) {
            return UNITLESS;
        }
        AEKeyType keyType = key.getType();
        return new NumberEntryType(keyType.getAmountPerUnit(), keyType.getUnitSymbol());
    }
}
