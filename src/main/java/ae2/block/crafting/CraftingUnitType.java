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

import ae2.api.crafting.cpu.CraftingUnitVisualDefinition;
import ae2.api.crafting.cpu.CraftingUnitVisualKind;
import ae2.core.AppEng;
import ae2.core.definitions.AEBlocks;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

public enum CraftingUnitType implements ICraftingUnitType {
    UNIT(0, 0, CraftingUnitVisualKind.UNIT),
    ACCELERATOR(0, 1, CraftingUnitVisualKind.LIGHT),
    ACCELERATOR_4X(0, 4, CraftingUnitVisualKind.LIGHT),
    STORAGE_1K(1, 0, CraftingUnitVisualKind.LIGHT),
    STORAGE_4K(4, 0, CraftingUnitVisualKind.LIGHT),
    STORAGE_16K(16, 0, CraftingUnitVisualKind.LIGHT),
    STORAGE_64K(64, 0, CraftingUnitVisualKind.LIGHT),
    STORAGE_256K(256, 0, CraftingUnitVisualKind.LIGHT),
    MONITOR(0, 0, CraftingUnitVisualKind.MONITOR);

    private final int storageKb;
    private final int accelerator;
    private final CraftingUnitVisualKind visualKind;
    private final ResourceLocation id;
    private final CraftingUnitVisualDefinition visualDefinition;

    CraftingUnitType(int storageKb, int accelerator, CraftingUnitVisualKind visualKind) {
        this.storageKb = storageKb;
        this.accelerator = accelerator;
        this.visualKind = visualKind;
        this.id = AppEng.makeId(getSerializedName());
        this.visualDefinition = createVisualDefinition();
    }

    @Override
    public long getStorageBytes() {
        return 1024L * this.storageKb;
    }

    @Override
    public int getAcceleratorThreads() {
        return accelerator;
    }

    @Override
    public Item getItemFromType() {
        return switch (this) {
            case UNIT -> AEBlocks.CRAFTING_UNIT.item();
            case ACCELERATOR -> AEBlocks.CRAFTING_ACCELERATOR.item();
            case ACCELERATOR_4X -> AEBlocks.CRAFTING_ACCELERATOR_4X.item();
            case STORAGE_1K -> AEBlocks.CRAFTING_STORAGE_1K.item();
            case STORAGE_4K -> AEBlocks.CRAFTING_STORAGE_4K.item();
            case STORAGE_16K -> AEBlocks.CRAFTING_STORAGE_16K.item();
            case STORAGE_64K -> AEBlocks.CRAFTING_STORAGE_64K.item();
            case STORAGE_256K -> AEBlocks.CRAFTING_STORAGE_256K.item();
            default -> AEBlocks.CRAFTING_MONITOR.item();
        };
    }

    @Override
    public ResourceLocation id() {
        return this.id;
    }

    @Override
    public CraftingUnitVisualDefinition getVisualDefinition() {
        return this.visualDefinition;
    }

    @Override
    public ResourceLocation getFamilyId() {
        return AppEng.makeId("crafting_cpu");
    }

    private String getSerializedName() {
        return switch (this) {
            case UNIT -> "crafting_unit";
            case ACCELERATOR -> "crafting_accelerator";
            case ACCELERATOR_4X -> "crafting_accelerator_4x";
            case STORAGE_1K -> "1k_crafting_storage";
            case STORAGE_4K -> "4k_crafting_storage";
            case STORAGE_16K -> "16k_crafting_storage";
            case STORAGE_64K -> "64k_crafting_storage";
            case STORAGE_256K -> "256k_crafting_storage";
            case MONITOR -> "crafting_monitor";
        };
    }

    private String getVisualName() {
        return switch (this) {
            case UNIT -> "unit";
            case ACCELERATOR -> "accelerator";
            case ACCELERATOR_4X -> "accelerator_4x";
            case STORAGE_1K -> "storage_1k";
            case STORAGE_4K -> "storage_4k";
            case STORAGE_16K -> "storage_16k";
            case STORAGE_64K -> "storage_64k";
            case STORAGE_256K -> "storage_256k";
            case MONITOR -> "monitor";
        };
    }

    private String getLightTextureName() {
        return switch (this) {
            case ACCELERATOR -> "accelerator_light";
            case ACCELERATOR_4X -> "accelerator_4x_light";
            case STORAGE_1K -> "1k_storage_light";
            case STORAGE_4K -> "4k_storage_light";
            case STORAGE_16K -> "16k_storage_light";
            case STORAGE_64K -> "64k_storage_light";
            case STORAGE_256K -> "256k_storage_light";
            default -> throw new IllegalStateException("No light texture for " + this);
        };
    }

    private CraftingUnitVisualDefinition createVisualDefinition() {
        var builder = CraftingUnitVisualDefinition.builder(
                                                      this.visualKind,
                                                      AppEng.makeId("block/crafting/" + getVisualName()),
                                                      AppEng.makeId("block/crafting/" + getVisualName() + "_formed"))
                                                  .ringTextures(
                                                      AppEng.makeId("block/crafting/ring_corner"),
                                                      AppEng.makeId("block/crafting/ring_side_hor"),
                                                      AppEng.makeId("block/crafting/ring_side_ver"));

        if (this.visualKind == CraftingUnitVisualKind.MONITOR) {
            return builder
                .baseTexture(AppEng.makeId("block/crafting/unit_base"))
                .monitorTextures(
                    AppEng.makeId("block/crafting/monitor_base"),
                    AppEng.makeId("block/crafting/monitor_light_dark"),
                    AppEng.makeId("block/crafting/monitor_light_medium"),
                    AppEng.makeId("block/crafting/monitor_light_bright"))
                .build();
        }

        if (this.visualKind == CraftingUnitVisualKind.UNIT) {
            return builder.baseTexture(AppEng.makeId("block/crafting/unit_base")).build();
        }

        return builder
            .baseTexture(AppEng.makeId("block/crafting/light_base"))
            .lightTexture(AppEng.makeId("block/crafting/" + getLightTextureName()))
            .build();
    }
}
