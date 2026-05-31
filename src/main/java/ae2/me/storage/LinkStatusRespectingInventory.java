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

package ae2.me.storage;

import ae2.api.storage.ILinkStatus;
import ae2.api.storage.MEStorage;

import java.util.function.Supplier;

/**
 * A delegating ME inventory that falls back to an empty inventory if the {@link ILinkStatus link status} indicates that
 * the terminal is not connected.
 */
public class LinkStatusRespectingInventory extends DelegatingMEInventory {
    private final Supplier<ILinkStatus> linkStatusSupplier;

    public LinkStatusRespectingInventory(MEStorage delegate, Supplier<ILinkStatus> linkStatusSupplier) {
        super(delegate);
        this.linkStatusSupplier = linkStatusSupplier;
    }

    @Override
    protected MEStorage getDelegate() {
        if (linkStatusSupplier.get().connected()) {
            return super.getDelegate();
        }
        return NullInventory.of();
    }
}
