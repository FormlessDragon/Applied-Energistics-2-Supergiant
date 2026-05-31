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

package ae2.container.me.common;

import ae2.api.stacks.AEKey;
import org.jetbrains.annotations.Nullable;

/**
 * Contains information about something that is stored inside the grid inventory. This is used to synchronize the
 * grid inventory to a client, and incrementally update the information. To this end, each stack sent to the client is
 * identified by a {@link #serial}, which is subsequently used to update the inventory entry for that specific
 * item/fluid/etc.
 */
public record GridInventoryEntry(long serial, @Nullable AEKey what, long storedAmount, long requestableAmount,
                                 boolean craftable) {

    public boolean isMeaningful() {
        return storedAmount > 0 || requestableAmount > 0 || craftable;
    }
}

