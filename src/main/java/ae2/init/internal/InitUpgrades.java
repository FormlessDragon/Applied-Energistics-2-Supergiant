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

import ae2.api.implementations.items.WirelessTerminalUpgradeHelper;
import ae2.api.upgrades.Upgrades;
import ae2.core.AEConfig;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEItems;
import ae2.core.definitions.AEParts;
import ae2.core.localization.GuiText;

import java.util.List;

public final class InitUpgrades {

    private InitUpgrades() {
    }

    public static void init() {
        String interfaceGroup = GuiText.Interface.getTranslationKey();
        String itemIoBusGroup = GuiText.IOBuses.getTranslationKey();
        String patternProviderGroup = GuiText.CraftingInterface.getTranslationKey();
        String storageBusGroup = GuiText.StorageBus.getTranslationKey();
        String storageCellGroup = GuiText.StorageCells.getTranslationKey();
        String portableCellGroup = GuiText.PortableCells.getTranslationKey();
        Upgrades.add(AEItems.FUZZY_CARD.item(), AEItems.VIEW_CELL.item(), 1);
        Upgrades.add(AEItems.INVERTER_CARD.item(), AEItems.VIEW_CELL.item(), 1);

        Upgrades.add(AEItems.CRAFTING_CARD.item(), AEParts.INTERFACE.item(), 1, interfaceGroup);
        Upgrades.add(AEItems.CRAFTING_CARD.item(), AEBlocks.INTERFACE.item(), 1, interfaceGroup);
        Upgrades.add(AEItems.FUZZY_CARD.item(), AEParts.INTERFACE.item(), 1, interfaceGroup);
        Upgrades.add(AEItems.FUZZY_CARD.item(), AEBlocks.INTERFACE.item(), 1, interfaceGroup);
        Upgrades.add(AEItems.PSEUDO_CRAFTING_CARD.item(), AEParts.PATTERN_PROVIDER.item(), 1, patternProviderGroup);
        Upgrades.add(AEItems.PSEUDO_CRAFTING_CARD.item(), AEBlocks.PATTERN_PROVIDER.item(), 1, patternProviderGroup);
        int patternExpansionCards = AEConfig.instance().getPatternProviderExpansionCardLimit();
        Upgrades.add(AEItems.PATTERN_EXPANSION_CARD.item(), AEParts.PATTERN_PROVIDER.item(), patternExpansionCards,
            patternProviderGroup);
        Upgrades.add(AEItems.PATTERN_EXPANSION_CARD.item(), AEBlocks.PATTERN_PROVIDER.item(), patternExpansionCards,
            patternProviderGroup);

        Upgrades.add(AEItems.SPEED_CARD.item(), AEBlocks.MOLECULAR_ASSEMBLER.item(), 5);
        Upgrades.add(AEItems.PARALLEL_CARD.item(), AEBlocks.MOLECULAR_ASSEMBLER.item(), 3);
        Upgrades.add(AEItems.PATTERN_EXPANSION_CARD.item(), AEBlocks.MOLECULAR_ASSEMBLER.item(),
            AEConfig.instance().getMolecularAssemblerPatternExpansionCardLimit(), patternProviderGroup);
        Upgrades.add(AEItems.SPEED_CARD.item(), AEBlocks.INSCRIBER.item(), 4);
        Upgrades.add(AEItems.PARALLEL_CARD.item(), AEBlocks.INSCRIBER.item(), 3);
        Upgrades.add(AEItems.SPEED_CARD.item(), AEBlocks.CRYSTAL_ASSEMBLER.item(), 4);
        Upgrades.add(AEItems.PARALLEL_CARD.item(), AEBlocks.CRYSTAL_ASSEMBLER.item(), 3);

        Upgrades.add(AEItems.SPEED_CARD.item(), AEBlocks.IO_PORT.item(), 3);
        Upgrades.add(AEItems.REDSTONE_CARD.item(), AEBlocks.IO_PORT.item(), 1);

        Upgrades.add(AEItems.FUZZY_CARD.item(), AEParts.LEVEL_EMITTER.item(), 1);
        Upgrades.add(AEItems.CRAFTING_CARD.item(), AEParts.LEVEL_EMITTER.item(), 1);
        Upgrades.add(AEItems.FUZZY_CARD.item(), AEParts.THRESHOLD_LEVEL_EMITTER.item(), 1);

        Upgrades.add(AEItems.FUZZY_CARD.item(), AEParts.IMPORT_BUS.item(), 1, itemIoBusGroup);
        Upgrades.add(AEItems.REDSTONE_CARD.item(), AEParts.IMPORT_BUS.item(), 1, itemIoBusGroup);
        Upgrades.add(AEItems.CAPACITY_CARD.item(), AEParts.IMPORT_BUS.item(), 5, itemIoBusGroup);
        Upgrades.add(AEItems.SPEED_CARD.item(), AEParts.IMPORT_BUS.item(), 4, itemIoBusGroup);
        Upgrades.add(AEItems.INVERTER_CARD.item(), AEParts.IMPORT_BUS.item(), 1, itemIoBusGroup);

        Upgrades.add(AEItems.FUZZY_CARD.item(), AEParts.EXPORT_BUS.item(), 1, itemIoBusGroup);
        Upgrades.add(AEItems.REDSTONE_CARD.item(), AEParts.EXPORT_BUS.item(), 1, itemIoBusGroup);
        Upgrades.add(AEItems.CAPACITY_CARD.item(), AEParts.EXPORT_BUS.item(), 5, itemIoBusGroup);
        Upgrades.add(AEItems.SPEED_CARD.item(), AEParts.EXPORT_BUS.item(), 4, itemIoBusGroup);
        Upgrades.add(AEItems.CRAFTING_CARD.item(), AEParts.EXPORT_BUS.item(), 1, itemIoBusGroup);
        for (var exportBus : List.of(
            AEParts.OD_EXPORT_BUS,
            AEParts.MOD_EXPORT_BUS,
            AEParts.PRECISE_EXPORT_BUS,
            AEParts.THRESHOLD_EXPORT_BUS,
            AEParts.STOCK_EXPORT_BUS,
            AEParts.IMPORT_EXPORT_BUS,
            AEParts.ADVANCED_IO_BUS)) {
            Upgrades.add(AEItems.FUZZY_CARD.item(), exportBus.item(), 1, itemIoBusGroup);
            Upgrades.add(AEItems.REDSTONE_CARD.item(), exportBus.item(), 1, itemIoBusGroup);
            Upgrades.add(AEItems.CAPACITY_CARD.item(), exportBus.item(), 5, itemIoBusGroup);
            Upgrades.add(AEItems.SPEED_CARD.item(), exportBus.item(), 4, itemIoBusGroup);
            Upgrades.add(AEItems.CRAFTING_CARD.item(), exportBus.item(), 1, itemIoBusGroup);
        }

        for (var itemCell : List.of(
            AEItems.ITEM_CELL_1K,
            AEItems.ITEM_CELL_4K,
            AEItems.ITEM_CELL_16K,
            AEItems.ITEM_CELL_64K,
            AEItems.ITEM_CELL_256K)) {
            Upgrades.add(AEItems.FUZZY_CARD.item(), itemCell.item(), 1, storageCellGroup);
            Upgrades.add(AEItems.INVERTER_CARD.item(), itemCell.item(), 1, storageCellGroup);
            Upgrades.add(AEItems.EQUAL_DISTRIBUTION_CARD.item(), itemCell.item(), 1, storageCellGroup);
            Upgrades.add(AEItems.STICKY_CARD.item(), itemCell.item(), 1, storageCellGroup);
            Upgrades.add(AEItems.VOID_CARD.item(), itemCell.item(), 1, storageCellGroup);
        }

        for (var fluidCell : List.of(
            AEItems.FLUID_CELL_1K,
            AEItems.FLUID_CELL_4K,
            AEItems.FLUID_CELL_16K,
            AEItems.FLUID_CELL_64K,
            AEItems.FLUID_CELL_256K)) {
            Upgrades.add(AEItems.INVERTER_CARD.item(), fluidCell.item(), 1, storageCellGroup);
            Upgrades.add(AEItems.EQUAL_DISTRIBUTION_CARD.item(), fluidCell.item(), 1, storageCellGroup);
            Upgrades.add(AEItems.STICKY_CARD.item(), fluidCell.item(), 1, storageCellGroup);
            Upgrades.add(AEItems.VOID_CARD.item(), fluidCell.item(), 1, storageCellGroup);
        }

        Upgrades.add(AEItems.INVERTER_CARD.item(), AEItems.VOID_CELL.item(), 1, storageCellGroup);
        Upgrades.add(AEItems.FUZZY_CARD.item(), AEItems.VOID_CELL.item(), 1, storageCellGroup);

        for (var portableItemCell : List.of(
            AEItems.PORTABLE_ITEM_CELL1K,
            AEItems.PORTABLE_ITEM_CELL4K,
            AEItems.PORTABLE_ITEM_CELL16K,
            AEItems.PORTABLE_ITEM_CELL64K,
            AEItems.PORTABLE_ITEM_CELL256K)) {
            Upgrades.add(AEItems.FUZZY_CARD.item(), portableItemCell.item(), 1, portableCellGroup);
            Upgrades.add(AEItems.INVERTER_CARD.item(), portableItemCell.item(), 1, portableCellGroup);
            Upgrades.add(AEItems.EQUAL_DISTRIBUTION_CARD.item(), portableItemCell.item(), 1, portableCellGroup);
            Upgrades.add(AEItems.STICKY_CARD.item(), portableItemCell.item(), 1, portableCellGroup);
            Upgrades.add(AEItems.VOID_CARD.item(), portableItemCell.item(), 1, portableCellGroup);
            Upgrades.add(AEItems.ENERGY_CARD.item(), portableItemCell.item(), 2, portableCellGroup);
        }

        for (var portableFluidCell : List.of(
            AEItems.PORTABLE_FLUID_CELL1K,
            AEItems.PORTABLE_FLUID_CELL4K,
            AEItems.PORTABLE_FLUID_CELL16K,
            AEItems.PORTABLE_FLUID_CELL64K,
            AEItems.PORTABLE_FLUID_CELL256K)) {
            Upgrades.add(AEItems.INVERTER_CARD.item(), portableFluidCell.item(), 1, portableCellGroup);
            Upgrades.add(AEItems.EQUAL_DISTRIBUTION_CARD.item(), portableFluidCell.item(), 1, portableCellGroup);
            Upgrades.add(AEItems.STICKY_CARD.item(), portableFluidCell.item(), 1, portableCellGroup);
            Upgrades.add(AEItems.VOID_CARD.item(), portableFluidCell.item(), 1, portableCellGroup);
            Upgrades.add(AEItems.ENERGY_CARD.item(), portableFluidCell.item(), 2, portableCellGroup);
        }

        WirelessTerminalUpgradeHelper.addDefaultUpgrades();
        WirelessTerminalUpgradeHelper.addUpgradeToAllTerminals(AEItems.QUANTUM_BRIDGE_CARD.item(), 1);
        WirelessTerminalUpgradeHelper.addUpgradeToAllTerminals(AEItems.MAGNET_CARD.item(), 1);

        Upgrades.add(AEItems.FUZZY_CARD.item(), AEParts.STORAGE_BUS.item(), 1, storageBusGroup);
        Upgrades.add(AEItems.INVERTER_CARD.item(), AEParts.STORAGE_BUS.item(), 1, storageBusGroup);
        Upgrades.add(AEItems.CAPACITY_CARD.item(), AEParts.STORAGE_BUS.item(), 5, storageBusGroup);
        Upgrades.add(AEItems.VOID_CARD.item(), AEParts.STORAGE_BUS.item(), 1, storageBusGroup);
        Upgrades.add(AEItems.STICKY_CARD.item(), AEParts.STORAGE_BUS.item(), 1, storageBusGroup);
        for (var storageBus : List.of(
            AEParts.OD_STORAGE_BUS,
            AEParts.MOD_STORAGE_BUS,
            AEParts.PRECISE_STORAGE_BUS)) {
            Upgrades.add(AEItems.FUZZY_CARD.item(), storageBus.item(), 1, storageBusGroup);
            Upgrades.add(AEItems.INVERTER_CARD.item(), storageBus.item(), 1, storageBusGroup);
            Upgrades.add(AEItems.CAPACITY_CARD.item(), storageBus.item(), 5, storageBusGroup);
            Upgrades.add(AEItems.VOID_CARD.item(), storageBus.item(), 1, storageBusGroup);
            Upgrades.add(AEItems.STICKY_CARD.item(), storageBus.item(), 1, storageBusGroup);
        }

        Upgrades.add(AEItems.FUZZY_CARD.item(), AEParts.FORMATION_PLANE.item(), 1);
        Upgrades.add(AEItems.INVERTER_CARD.item(), AEParts.FORMATION_PLANE.item(), 1);
        Upgrades.add(AEItems.CAPACITY_CARD.item(), AEParts.FORMATION_PLANE.item(), 5);
        Upgrades.add(AEItems.FUZZY_CARD.item(), AEParts.ANNIHILATION_PLANE.item(), 1);
        Upgrades.add(AEItems.INVERTER_CARD.item(), AEParts.ANNIHILATION_PLANE.item(), 1);
        Upgrades.add(AEItems.CAPACITY_CARD.item(), AEParts.ANNIHILATION_PLANE.item(), 5);
        Upgrades.add(AEItems.VOID_CARD.item(), AEParts.ANNIHILATION_PLANE.item(), 1);

        Upgrades.add(AEItems.ENERGY_CARD.item(), AEItems.COLOR_APPLICATOR.item(), 2);
        Upgrades.add(AEItems.EQUAL_DISTRIBUTION_CARD.item(), AEItems.COLOR_APPLICATOR.item(), 1);
        Upgrades.add(AEItems.VOID_CARD.item(), AEItems.COLOR_APPLICATOR.item(), 1);
        Upgrades.add(AEItems.ENERGY_CARD.item(), AEItems.MATTER_CANNON.item(), 2);
        Upgrades.add(AEItems.FUZZY_CARD.item(), AEItems.MATTER_CANNON.item(), 1);
        Upgrades.add(AEItems.INVERTER_CARD.item(), AEItems.MATTER_CANNON.item(), 1);
        Upgrades.add(AEItems.VOID_CARD.item(), AEItems.MATTER_CANNON.item(), 1);
        Upgrades.add(AEItems.SPEED_CARD.item(), AEItems.MATTER_CANNON.item(), 4);

        Upgrades.add(AEItems.SPEED_CARD.item(), AEBlocks.VIBRATION_CHAMBER.item(), 3);
        Upgrades.add(AEItems.ENERGY_CARD.item(), AEBlocks.VIBRATION_CHAMBER.item(), 3);
    }
}
