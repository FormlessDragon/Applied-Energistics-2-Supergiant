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
package ae2.client.render.model;

import ae2.api.implementations.blockentities.IChestOrDrive;
import net.minecraft.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class DriveModelData {
    private final Item[] items;

    private DriveModelData(Item[] items) {
        this.items = items;
    }

    public static DriveModelData fromDrive(IChestOrDrive drive) {
        Item[] items = new Item[drive.getCellCount()];
        for (int i = 0; i < items.length; i++) {
            items[i] = drive.getCellItem(i);
        }
        return new DriveModelData(items);
    }

    public static DriveModelData createEmpty(int slotCount) {
        return new DriveModelData(new Item[slotCount]);
    }

    @Nullable
    public Item getItem(int index) {
        return index >= 0 && index < this.items.length ? this.items[index] : null;
    }

    @Override
    public String toString() {
        return Arrays.toString(this.items);
    }
}
