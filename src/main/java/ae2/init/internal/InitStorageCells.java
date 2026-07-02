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

package ae2.init.internal;

import ae2.api.client.StorageCellModels;
import ae2.api.storage.StorageCells;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEItems;
import ae2.me.cells.BasicCellHandler;
import ae2.me.cells.CreativeCellHandler;
import ae2.me.cells.VoidCellHandler;
import ae2.recipes.game.FacadeRecipe;
import ae2.recipes.game.StorageCellDisassemblyRecipe;
import ae2.recipes.game.StorageCellUpgradeRecipe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.Objects;

public final class InitStorageCells {
    private static final ResourceLocation MODEL_CELL_ITEMS_1K = new ResourceLocation("ae2", "block/drive/cells/1k_item_cell");
    private static final ResourceLocation MODEL_CELL_ITEMS_4K = new ResourceLocation("ae2", "block/drive/cells/4k_item_cell");
    private static final ResourceLocation MODEL_CELL_ITEMS_16K = new ResourceLocation("ae2", "block/drive/cells/16k_item_cell");
    private static final ResourceLocation MODEL_CELL_ITEMS_64K = new ResourceLocation("ae2", "block/drive/cells/64k_item_cell");
    private static final ResourceLocation MODEL_CELL_ITEMS_256K = new ResourceLocation("ae2", "block/drive/cells/256k_item_cell");
    private static final ResourceLocation MODEL_CELL_FLUIDS_1K = new ResourceLocation("ae2", "block/drive/cells/1k_fluid_cell");
    private static final ResourceLocation MODEL_CELL_FLUIDS_4K = new ResourceLocation("ae2", "block/drive/cells/4k_fluid_cell");
    private static final ResourceLocation MODEL_CELL_FLUIDS_16K = new ResourceLocation("ae2", "block/drive/cells/16k_fluid_cell");
    private static final ResourceLocation MODEL_CELL_FLUIDS_64K = new ResourceLocation("ae2", "block/drive/cells/64k_fluid_cell");
    private static final ResourceLocation MODEL_CELL_FLUIDS_256K = new ResourceLocation("ae2", "block/drive/cells/256k_fluid_cell");
    private static final ResourceLocation MODEL_CELL_CREATIVE = new ResourceLocation("ae2", "block/drive/cells/creative_cell");
    private static final ResourceLocation MODEL_CELL_VOID = new ResourceLocation("ae2", "block/drive/cells/void_cell");
    private static boolean initialized;

    private InitStorageCells() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        StorageCells.addCellHandler(BasicCellHandler.INSTANCE);
        StorageCells.addCellHandler(CreativeCellHandler.INSTANCE);
        StorageCells.addCellHandler(VoidCellHandler.INSTANCE);
        initCellDisassembly();

        StorageCellModels.registerModel(AEItems.ITEM_CELL_1K.item(), MODEL_CELL_ITEMS_1K);
        StorageCellModels.registerModel(AEItems.ITEM_CELL_4K.item(), MODEL_CELL_ITEMS_4K);
        StorageCellModels.registerModel(AEItems.ITEM_CELL_16K.item(), MODEL_CELL_ITEMS_16K);
        StorageCellModels.registerModel(AEItems.ITEM_CELL_64K.item(), MODEL_CELL_ITEMS_64K);
        StorageCellModels.registerModel(AEItems.ITEM_CELL_256K.item(), MODEL_CELL_ITEMS_256K);
        StorageCellModels.registerModel(AEItems.FLUID_CELL_1K.item(), MODEL_CELL_FLUIDS_1K);
        StorageCellModels.registerModel(AEItems.FLUID_CELL_4K.item(), MODEL_CELL_FLUIDS_4K);
        StorageCellModels.registerModel(AEItems.FLUID_CELL_16K.item(), MODEL_CELL_FLUIDS_16K);
        StorageCellModels.registerModel(AEItems.FLUID_CELL_64K.item(), MODEL_CELL_FLUIDS_64K);
        StorageCellModels.registerModel(AEItems.FLUID_CELL_256K.item(), MODEL_CELL_FLUIDS_256K);
        StorageCellModels.registerModel(AEItems.CREATIVE_CELL.item(), MODEL_CELL_CREATIVE);
        StorageCellModels.registerModel(AEItems.VOID_CELL.item(), MODEL_CELL_VOID);
        StorageCellModels.registerModel(AEItems.PORTABLE_VOID_CELL.item(), MODEL_CELL_VOID);
        StorageCellModels.registerModel(AEItems.PORTABLE_ITEM_CELL1K.item(), MODEL_CELL_ITEMS_1K);
        StorageCellModels.registerModel(AEItems.PORTABLE_ITEM_CELL4K.item(), MODEL_CELL_ITEMS_4K);
        StorageCellModels.registerModel(AEItems.PORTABLE_ITEM_CELL16K.item(), MODEL_CELL_ITEMS_16K);
        StorageCellModels.registerModel(AEItems.PORTABLE_ITEM_CELL64K.item(), MODEL_CELL_ITEMS_64K);
        StorageCellModels.registerModel(AEItems.PORTABLE_ITEM_CELL256K.item(), MODEL_CELL_ITEMS_256K);
        StorageCellModels.registerModel(AEItems.PORTABLE_FLUID_CELL1K.item(), MODEL_CELL_FLUIDS_1K);
        StorageCellModels.registerModel(AEItems.PORTABLE_FLUID_CELL4K.item(), MODEL_CELL_FLUIDS_4K);
        StorageCellModels.registerModel(AEItems.PORTABLE_FLUID_CELL16K.item(), MODEL_CELL_FLUIDS_16K);
        StorageCellModels.registerModel(AEItems.PORTABLE_FLUID_CELL64K.item(), MODEL_CELL_FLUIDS_64K);
        StorageCellModels.registerModel(AEItems.PORTABLE_FLUID_CELL256K.item(), MODEL_CELL_FLUIDS_256K);
    }

    public static void registerRecipes(IForgeRegistry<IRecipe> registry) {
        registerRecipe(registry, "facade", new FacadeRecipe());

        registerStorageCellUpgradeRecipes(registry, "item_storage_cell",
            new Item[]{
                AEItems.ITEM_CELL_1K.item(),
                AEItems.ITEM_CELL_4K.item(),
                AEItems.ITEM_CELL_16K.item(),
                AEItems.ITEM_CELL_64K.item(),
                AEItems.ITEM_CELL_256K.item()});
        registerStorageCellUpgradeRecipes(registry, "fluid_storage_cell",
            new Item[]{
                AEItems.FLUID_CELL_1K.item(),
                AEItems.FLUID_CELL_4K.item(),
                AEItems.FLUID_CELL_16K.item(),
                AEItems.FLUID_CELL_64K.item(),
                AEItems.FLUID_CELL_256K.item()});
        registerStorageCellUpgradeRecipes(registry, "portable_item_cell",
            new Item[]{
                AEItems.PORTABLE_ITEM_CELL1K.item(),
                AEItems.PORTABLE_ITEM_CELL4K.item(),
                AEItems.PORTABLE_ITEM_CELL16K.item(),
                AEItems.PORTABLE_ITEM_CELL64K.item(),
                AEItems.PORTABLE_ITEM_CELL256K.item()});
        registerStorageCellUpgradeRecipes(registry, "portable_fluid_cell",
            new Item[]{
                AEItems.PORTABLE_FLUID_CELL1K.item(),
                AEItems.PORTABLE_FLUID_CELL4K.item(),
                AEItems.PORTABLE_FLUID_CELL16K.item(),
                AEItems.PORTABLE_FLUID_CELL64K.item(),
                AEItems.PORTABLE_FLUID_CELL256K.item()});
    }

    private static void initCellDisassembly() {
        StorageCellDisassemblyRecipe.clear();

        registerStorageCellDisassembly(AEItems.ITEM_CELL_1K.item(), AEItems.CELL_COMPONENT_1K.item(), AEItems.ITEM_CELL_HOUSING.item());
        registerStorageCellDisassembly(AEItems.ITEM_CELL_4K.item(), AEItems.CELL_COMPONENT_4K.item(), AEItems.ITEM_CELL_HOUSING.item());
        registerStorageCellDisassembly(AEItems.ITEM_CELL_16K.item(), AEItems.CELL_COMPONENT_16K.item(), AEItems.ITEM_CELL_HOUSING.item());
        registerStorageCellDisassembly(AEItems.ITEM_CELL_64K.item(), AEItems.CELL_COMPONENT_64K.item(), AEItems.ITEM_CELL_HOUSING.item());
        registerStorageCellDisassembly(AEItems.ITEM_CELL_256K.item(), AEItems.CELL_COMPONENT_256K.item(), AEItems.ITEM_CELL_HOUSING.item());

        registerStorageCellDisassembly(AEItems.FLUID_CELL_1K.item(), AEItems.CELL_COMPONENT_1K.item(), AEItems.FLUID_CELL_HOUSING.item());
        registerStorageCellDisassembly(AEItems.FLUID_CELL_4K.item(), AEItems.CELL_COMPONENT_4K.item(), AEItems.FLUID_CELL_HOUSING.item());
        registerStorageCellDisassembly(AEItems.FLUID_CELL_16K.item(), AEItems.CELL_COMPONENT_16K.item(), AEItems.FLUID_CELL_HOUSING.item());
        registerStorageCellDisassembly(AEItems.FLUID_CELL_64K.item(), AEItems.CELL_COMPONENT_64K.item(), AEItems.FLUID_CELL_HOUSING.item());
        registerStorageCellDisassembly(AEItems.FLUID_CELL_256K.item(), AEItems.CELL_COMPONENT_256K.item(), AEItems.FLUID_CELL_HOUSING.item());

        registerPortableCellDisassembly(AEItems.PORTABLE_ITEM_CELL1K.item(), AEItems.CELL_COMPONENT_1K.item(), AEItems.ITEM_CELL_HOUSING.item());
        registerPortableCellDisassembly(AEItems.PORTABLE_ITEM_CELL4K.item(), AEItems.CELL_COMPONENT_4K.item(), AEItems.ITEM_CELL_HOUSING.item());
        registerPortableCellDisassembly(AEItems.PORTABLE_ITEM_CELL16K.item(), AEItems.CELL_COMPONENT_16K.item(), AEItems.ITEM_CELL_HOUSING.item());
        registerPortableCellDisassembly(AEItems.PORTABLE_ITEM_CELL64K.item(), AEItems.CELL_COMPONENT_64K.item(), AEItems.ITEM_CELL_HOUSING.item());
        registerPortableCellDisassembly(AEItems.PORTABLE_ITEM_CELL256K.item(), AEItems.CELL_COMPONENT_256K.item(), AEItems.ITEM_CELL_HOUSING.item());

        registerPortableCellDisassembly(AEItems.PORTABLE_FLUID_CELL1K.item(), AEItems.CELL_COMPONENT_1K.item(), AEItems.FLUID_CELL_HOUSING.item());
        registerPortableCellDisassembly(AEItems.PORTABLE_FLUID_CELL4K.item(), AEItems.CELL_COMPONENT_4K.item(), AEItems.FLUID_CELL_HOUSING.item());
        registerPortableCellDisassembly(AEItems.PORTABLE_FLUID_CELL16K.item(), AEItems.CELL_COMPONENT_16K.item(), AEItems.FLUID_CELL_HOUSING.item());
        registerPortableCellDisassembly(AEItems.PORTABLE_FLUID_CELL64K.item(), AEItems.CELL_COMPONENT_64K.item(), AEItems.FLUID_CELL_HOUSING.item());
        registerPortableCellDisassembly(AEItems.PORTABLE_FLUID_CELL256K.item(), AEItems.CELL_COMPONENT_256K.item(), AEItems.FLUID_CELL_HOUSING.item());
    }

    private static void registerStorageCellDisassembly(Item cell, Item component, Item housing) {
        registerDisassembly(cell, new ItemStack(housing), new ItemStack(component));
    }

    private static void registerPortableCellDisassembly(Item cell, Item component, Item housing) {
        registerDisassembly(
            cell,
            new ItemStack(Objects.requireNonNull(AEBlocks.ME_CHEST.item())),
            new ItemStack(Objects.requireNonNull(AEBlocks.ENERGY_CELL.item())),
            new ItemStack(housing),
            new ItemStack(component));
    }

    private static void registerDisassembly(Item cell, ItemStack... results) {
        var stacks = new ObjectArrayList<ItemStack>(results.length);
        for (var result : results) {
            stacks.add(result.copy());
        }
        StorageCellDisassemblyRecipe.register(new StorageCellDisassemblyRecipe(cell, stacks));
    }

    private static void registerStorageCellUpgradeRecipes(IForgeRegistry<IRecipe> registry, String prefix, Item[] cells) {
        Item[] components = {
            AEItems.CELL_COMPONENT_1K.item(),
            AEItems.CELL_COMPONENT_4K.item(),
            AEItems.CELL_COMPONENT_16K.item(),
            AEItems.CELL_COMPONENT_64K.item(),
            AEItems.CELL_COMPONENT_256K.item()};
        String[] tierNames = {"1k", "4k", "16k", "64k", "256k"};

        for (int from = 0; from < cells.length; from++) {
            for (int to = from + 1; to < cells.length; to++) {
                registerRecipe(registry, "upgrade/" + prefix + "_" + tierNames[from] + "_to_" + tierNames[to],
                    new StorageCellUpgradeRecipe(cells[from], components[to], cells[to], components[from]));
            }
        }
    }

    private static void registerRecipe(IForgeRegistry<IRecipe> registry, String id, IRecipe recipe) {
        registry.register(recipe.setRegistryName(new ResourceLocation("ae2", id)));
    }
}
