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

package ae2.block.qnb;

import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IUnlistedProperty;

import java.util.Set;

public record QnbFormedState(Set<EnumFacing> adjacentQuantumBridges, boolean corner, boolean powered) {

    public static final IUnlistedProperty<QnbFormedState> PROPERTY = new IUnlistedProperty<>() {
        @Override
        public String getName() {
            return "formed_state";
        }

        @Override
        public boolean isValid(QnbFormedState value) {
            return value != null;
        }

        @Override
        public Class<QnbFormedState> getType() {
            return QnbFormedState.class;
        }

        @Override
        public String valueToString(QnbFormedState value) {
            return String.valueOf(value);
        }
    };

}
