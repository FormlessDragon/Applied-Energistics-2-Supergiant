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

package ae2.items.contents;

import ae2.api.config.Actionable;
import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.IInWorldGridNodeHost;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.items.tools.NetworkToolItem;
import ae2.util.inv.SupplierInternalInventory;
import com.google.common.primitives.Ints;
import net.minecraft.entity.player.EntityPlayer;
import org.jetbrains.annotations.Nullable;

public class NetworkToolGuiHost<T extends NetworkToolItem> extends ItemGuiHost<T> {
    @Nullable
    private final IInWorldGridNodeHost host;

    private final SupplierInternalInventory<InternalInventory> supplierInv;

    public NetworkToolGuiHost(T item, EntityPlayer player, ItemGuiHostLocator locator,
                              @Nullable IInWorldGridNodeHost host) {
        super(item, player, locator);
        this.host = host;
        this.supplierInv = new SupplierInternalInventory<>(
            new StackDependentSupplier<>(this::getItemStack, NetworkToolItem::getInventory));
    }

    @Override
    public long insert(EntityPlayer player, AEKey what, long amount, Actionable mode) {
        if (what instanceof AEItemKey itemKey) {
            var stack = itemKey.toStack(Ints.saturatedCast(amount));
            var overflow = getInventory().addItems(stack, mode == Actionable.SIMULATE);
            return stack.getCount() - overflow.getCount();
        }

        return 0;
    }

    @Nullable
    public IInWorldGridNodeHost getGridHost() {
        return this.host;
    }

    public InternalInventory getInventory() {
        return this.supplierInv;
    }
}
