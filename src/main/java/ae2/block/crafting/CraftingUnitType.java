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

package ae2.block.crafting;

import ae2.core.definitions.AEBlocks;
import net.minecraft.item.Item;

public enum CraftingUnitType implements ICraftingUnitType {
    UNIT(0),
    ACCELERATOR(0),
    STORAGE_1K(1),
    STORAGE_4K(4),
    STORAGE_16K(16),
    STORAGE_64K(64),
    STORAGE_256K(256),
    MONITOR(0);

    private final int storageKb;

    CraftingUnitType(int storageKb) {
        this.storageKb = storageKb;
    }

    @Override
    public long getStorageBytes() {
        return 1024L * this.storageKb;
    }

    @Override
    public int getAcceleratorThreads() {
        return this == ACCELERATOR ? 1 : 0;
    }

    @Override
    public Item getItemFromType() {
        return switch (this) {
            case UNIT -> AEBlocks.CRAFTING_UNIT.item();
            case ACCELERATOR -> AEBlocks.CRAFTING_ACCELERATOR.item();
            case STORAGE_1K -> AEBlocks.CRAFTING_STORAGE_1K.item();
            case STORAGE_4K -> AEBlocks.CRAFTING_STORAGE_4K.item();
            case STORAGE_16K -> AEBlocks.CRAFTING_STORAGE_16K.item();
            case STORAGE_64K -> AEBlocks.CRAFTING_STORAGE_64K.item();
            case STORAGE_256K -> AEBlocks.CRAFTING_STORAGE_256K.item();
            default -> AEBlocks.CRAFTING_MONITOR.item();
        };
    }
}

