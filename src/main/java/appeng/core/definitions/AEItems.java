/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package appeng.core.definitions;

import appeng.api.crafting.EncodedPatternItemBuilder;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEItemIds;
import appeng.api.stacks.AEKeyType;
import appeng.api.upgrades.Upgrades;
import appeng.api.util.AEColor;
import appeng.core.AEConfig;
import appeng.core.DebugCreativeTab;
import appeng.core.FacadeCreativeTab;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.debug.DebugCardItem;
import appeng.debug.EraserItem;
import appeng.debug.MeteoritePlacerItem;
import appeng.debug.ReplicatorCardItem;
import appeng.items.materials.EnergyCardItem;
import appeng.items.materials.MaterialItem;
import appeng.items.materials.NamePressItem;
import appeng.items.materials.QuantumEntangledSingularityItem;
import appeng.items.materials.StorageComponentItem;
import appeng.items.misc.MeteoriteCompassItem;
import appeng.items.misc.MissingContentItem;
import appeng.items.misc.PaintBallItem;
import appeng.items.misc.WrappedGenericStack;
import appeng.items.parts.FacadeItem;
import appeng.items.storage.BasicStorageCell;
import appeng.items.storage.CreativeCellItem;
import appeng.items.storage.SpatialStorageCellItem;
import appeng.items.storage.StorageTier;
import appeng.items.storage.ViewCellItem;
import appeng.items.storage.VoidCellItem;
import appeng.items.tools.MemoryCardItem;
import appeng.items.tools.NetworkToolItem;
import appeng.items.tools.fluix.FluixAxeItem;
import appeng.items.tools.fluix.FluixHoeItem;
import appeng.items.tools.fluix.FluixPickaxeItem;
import appeng.items.tools.fluix.FluixSpadeItem;
import appeng.items.tools.fluix.FluixSwordItem;
import appeng.items.tools.powered.ChargedStaffItem;
import appeng.items.tools.powered.ColorApplicatorItem;
import appeng.items.tools.powered.EntropyManipulatorItem;
import appeng.items.tools.powered.MatterCannonItem;
import appeng.items.tools.powered.PortableCellItem;
import appeng.items.tools.powered.WirelessCraftingTerminalItem;
import appeng.items.tools.powered.WirelessPatternAccessTerminalItem;
import appeng.items.tools.powered.WirelessPatternEncodingTerminalItem;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.items.tools.powered.WirelessUniversalTerminalItem;
import appeng.items.tools.quartz.QuartzAxeItem;
import appeng.items.tools.quartz.QuartzCuttingKnifeItem;
import appeng.items.tools.quartz.QuartzHoeItem;
import appeng.items.tools.quartz.QuartzPickaxeItem;
import appeng.items.tools.quartz.QuartzSpadeItem;
import appeng.items.tools.quartz.QuartzSwordItem;
import appeng.items.tools.quartz.QuartzToolType;
import appeng.items.tools.quartz.QuartzWrenchItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.event.RegistryEvent;

import java.util.function.IntSupplier;

/**
 * Internal implementation for the API items
 */
public final class AEItems {
    public static final ItemDefinition<WrappedGenericStack> WRAPPED_GENERIC_STACK = new ItemDefinition<>(
        AEItemIds.WRAPPED_GENERIC_STACK, new WrappedGenericStack(), DebugCreativeTab.INSTANCE);
    public static final ItemDefinition<FacadeItem> FACADE = new ItemDefinition<>(AEItemIds.FACADE, new FacadeItem(),
        FacadeCreativeTab.INSTANCE);
    public static final ItemDefinition<MaterialItem> BLANK_PATTERN = new ItemDefinition<>(AEItemIds.BLANK_PATTERN,
        new MaterialItem());
    public static final ItemDefinition<Item> CRAFTING_PATTERN = new ItemDefinition<>(AEItemIds.CRAFTING_PATTERN,
        PatternDetailsHelper.encodedPatternItemBuilder(AECraftingPattern::new)
                            .invalidPatternTooltip(AECraftingPattern::getInvalidPatternTooltip)
                            .itemProperties(new EncodedPatternItemBuilder.Properties().stacksTo(64))
                            .build(), DebugCreativeTab.INSTANCE);
    public static final ItemDefinition<Item> PROCESSING_PATTERN = new ItemDefinition<>(AEItemIds.PROCESSING_PATTERN,
        PatternDetailsHelper.encodedPatternItemBuilder(AEProcessingPattern::new)
                            .invalidPatternTooltip(AEProcessingPattern::getInvalidPatternTooltip)
                            .itemProperties(new EncodedPatternItemBuilder.Properties().stacksTo(64))
                            .build(), DebugCreativeTab.INSTANCE);
    public static final ItemDefinition<MaterialItem> CERTUS_QUARTZ_CRYSTAL = new ItemDefinition<>(
        AEItemIds.CERTUS_QUARTZ_CRYSTAL, new MaterialItem());
    public static final ItemDefinition<MaterialItem> CERTUS_QUARTZ_CRYSTAL_CHARGED = new ItemDefinition<>(
        AEItemIds.CERTUS_QUARTZ_CRYSTAL_CHARGED, new MaterialItem());
    public static final ItemDefinition<MaterialItem> CERTUS_QUARTZ_DUST = new ItemDefinition<>(
        AEItemIds.CERTUS_QUARTZ_DUST, new MaterialItem());
    public static final ItemDefinition<MaterialItem> SILICON = new ItemDefinition<>(AEItemIds.SILICON,
        new MaterialItem());
    public static final ItemDefinition<MaterialItem> MATTER_BALL = new ItemDefinition<>(AEItemIds.MATTER_BALL,
        new MaterialItem());
    public static final ItemDefinition<MaterialItem> FLUIX_CRYSTAL = new ItemDefinition<>(AEItemIds.FLUIX_CRYSTAL,
        new MaterialItem());
    public static final ItemDefinition<MaterialItem> FLUIX_DUST = new ItemDefinition<>(AEItemIds.FLUIX_DUST,
        new MaterialItem());
    public static final ItemDefinition<MaterialItem> FLUIX_PEARL = new ItemDefinition<>(AEItemIds.FLUIX_PEARL,
        new MaterialItem());
    public static final ItemDefinition<QuartzAxeItem> CERTUS_QUARTZ_AXE = new ItemDefinition<>(
        AEItemIds.CERTUS_QUARTZ_AXE, new QuartzAxeItem(QuartzToolType.CERTUS));
    public static final ItemDefinition<QuartzHoeItem> CERTUS_QUARTZ_HOE = new ItemDefinition<>(
        AEItemIds.CERTUS_QUARTZ_HOE, new QuartzHoeItem(QuartzToolType.CERTUS));
    public static final ItemDefinition<QuartzSpadeItem> CERTUS_QUARTZ_SHOVEL = new ItemDefinition<>(
        AEItemIds.CERTUS_QUARTZ_SHOVEL, new QuartzSpadeItem(QuartzToolType.CERTUS));
    public static final ItemDefinition<QuartzPickaxeItem> CERTUS_QUARTZ_PICK = new ItemDefinition<>(
        AEItemIds.CERTUS_QUARTZ_PICK, new QuartzPickaxeItem(QuartzToolType.CERTUS));
    public static final ItemDefinition<QuartzSwordItem> CERTUS_QUARTZ_SWORD = new ItemDefinition<>(
        AEItemIds.CERTUS_QUARTZ_SWORD, new QuartzSwordItem(QuartzToolType.CERTUS));
    public static final ItemDefinition<QuartzWrenchItem> CERTUS_QUARTZ_WRENCH = new ItemDefinition<>(
        AEItemIds.CERTUS_QUARTZ_WRENCH, new QuartzWrenchItem());
    public static final ItemDefinition<QuartzCuttingKnifeItem> CERTUS_QUARTZ_KNIFE = new ItemDefinition<>(
        AEItemIds.CERTUS_QUARTZ_KNIFE, new QuartzCuttingKnifeItem());
    public static final ItemDefinition<QuartzAxeItem> NETHER_QUARTZ_AXE = new ItemDefinition<>(
        AEItemIds.NETHER_QUARTZ_AXE, new QuartzAxeItem(QuartzToolType.NETHER));
    public static final ItemDefinition<QuartzHoeItem> NETHER_QUARTZ_HOE = new ItemDefinition<>(
        AEItemIds.NETHER_QUARTZ_HOE, new QuartzHoeItem(QuartzToolType.NETHER));
    public static final ItemDefinition<QuartzSpadeItem> NETHER_QUARTZ_SHOVEL = new ItemDefinition<>(
        AEItemIds.NETHER_QUARTZ_SHOVEL, new QuartzSpadeItem(QuartzToolType.NETHER));
    public static final ItemDefinition<QuartzPickaxeItem> NETHER_QUARTZ_PICK = new ItemDefinition<>(
        AEItemIds.NETHER_QUARTZ_PICK, new QuartzPickaxeItem(QuartzToolType.NETHER));
    public static final ItemDefinition<QuartzSwordItem> NETHER_QUARTZ_SWORD = new ItemDefinition<>(
        AEItemIds.NETHER_QUARTZ_SWORD, new QuartzSwordItem(QuartzToolType.NETHER));
    public static final ItemDefinition<QuartzWrenchItem> NETHER_QUARTZ_WRENCH = new ItemDefinition<>(
        AEItemIds.NETHER_QUARTZ_WRENCH, new QuartzWrenchItem());
    public static final ItemDefinition<QuartzCuttingKnifeItem> NETHER_QUARTZ_KNIFE = new ItemDefinition<>(
        AEItemIds.NETHER_QUARTZ_KNIFE, new QuartzCuttingKnifeItem());
    public static final ItemDefinition<FluixAxeItem> FLUIX_AXE = new ItemDefinition<>(AEItemIds.FLUIX_AXE,
        new FluixAxeItem());
    public static final ItemDefinition<FluixHoeItem> FLUIX_HOE = new ItemDefinition<>(AEItemIds.FLUIX_HOE,
        new FluixHoeItem());
    public static final ItemDefinition<FluixSpadeItem> FLUIX_SHOVEL = new ItemDefinition<>(AEItemIds.FLUIX_SHOVEL,
        new FluixSpadeItem());
    public static final ItemDefinition<FluixPickaxeItem> FLUIX_PICK = new ItemDefinition<>(AEItemIds.FLUIX_PICK,
        new FluixPickaxeItem());
    public static final ItemDefinition<FluixSwordItem> FLUIX_SWORD = new ItemDefinition<>(AEItemIds.FLUIX_SWORD,
        new FluixSwordItem());
    public static final ItemDefinition<MaterialItem> CALCULATION_PROCESSOR_PRESS = new ItemDefinition<>(
        AEItemIds.CALCULATION_PROCESSOR_PRESS, new MaterialItem());
    public static final ItemDefinition<MaterialItem> ENGINEERING_PROCESSOR_PRESS = new ItemDefinition<>(
        AEItemIds.ENGINEERING_PROCESSOR_PRESS, new MaterialItem());
    public static final ItemDefinition<MaterialItem> LOGIC_PROCESSOR_PRESS = new ItemDefinition<>(
        AEItemIds.LOGIC_PROCESSOR_PRESS, new MaterialItem());
    public static final ItemDefinition<MaterialItem> CALCULATION_PROCESSOR_PRINT = new ItemDefinition<>(
        AEItemIds.CALCULATION_PROCESSOR_PRINT, new MaterialItem());
    public static final ItemDefinition<MaterialItem> ENGINEERING_PROCESSOR_PRINT = new ItemDefinition<>(
        AEItemIds.ENGINEERING_PROCESSOR_PRINT, new MaterialItem());
    public static final ItemDefinition<MaterialItem> LOGIC_PROCESSOR_PRINT = new ItemDefinition<>(
        AEItemIds.LOGIC_PROCESSOR_PRINT, new MaterialItem());
    public static final ItemDefinition<MaterialItem> SILICON_PRESS = new ItemDefinition<>(AEItemIds.SILICON_PRESS,
        new MaterialItem());
    public static final ItemDefinition<MaterialItem> SILICON_PRINT = new ItemDefinition<>(AEItemIds.SILICON_PRINT,
        new MaterialItem());
    public static final ItemDefinition<MaterialItem> LOGIC_PROCESSOR = new ItemDefinition<>(AEItemIds.LOGIC_PROCESSOR,
        new MaterialItem());
    public static final ItemDefinition<MaterialItem> CALCULATION_PROCESSOR = new ItemDefinition<>(
        AEItemIds.CALCULATION_PROCESSOR, new MaterialItem());
    public static final ItemDefinition<MaterialItem> ENGINEERING_PROCESSOR = new ItemDefinition<>(
        AEItemIds.ENGINEERING_PROCESSOR, new MaterialItem());
    public static final ItemDefinition<MaterialItem> BASIC_CARD = new ItemDefinition<>(AEItemIds.BASIC_CARD,
        new MaterialItem());
    public static final ItemDefinition<Item> REDSTONE_CARD = new ItemDefinition<>(AEItemIds.REDSTONE_CARD,
        Upgrades.createUpgradeCardItem());
    public static final ItemDefinition<Item> CAPACITY_CARD = new ItemDefinition<>(AEItemIds.CAPACITY_CARD,
        Upgrades.createUpgradeCardItem());
    public static final ItemDefinition<Item> VOID_CARD = new ItemDefinition<>(AEItemIds.VOID_CARD,
        Upgrades.createUpgradeCardItem());
    public static final ItemDefinition<MaterialItem> ADVANCED_CARD = new ItemDefinition<>(AEItemIds.ADVANCED_CARD,
        new MaterialItem());
    public static final ItemDefinition<Item> FUZZY_CARD = new ItemDefinition<>(AEItemIds.FUZZY_CARD,
        Upgrades.createUpgradeCardItem());
    public static final ItemDefinition<Item> SPEED_CARD = new ItemDefinition<>(AEItemIds.SPEED_CARD,
        Upgrades.createUpgradeCardItem());
    public static final ItemDefinition<Item> INVERTER_CARD = new ItemDefinition<>(AEItemIds.INVERTER_CARD,
        Upgrades.createUpgradeCardItem());
    public static final ItemDefinition<Item> CRAFTING_CARD = new ItemDefinition<>(AEItemIds.CRAFTING_CARD,
        Upgrades.createUpgradeCardItem());
    public static final ItemDefinition<Item> PSEUDO_CRAFTING_CARD = new ItemDefinition<>(AEItemIds.PSEUDO_CRAFTING_CARD,
        Upgrades.createUpgradeCardItem());
    public static final ItemDefinition<Item> EQUAL_DISTRIBUTION_CARD = new ItemDefinition<>(
        AEItemIds.EQUAL_DISTRIBUTION_CARD, Upgrades.createUpgradeCardItem());
    public static final ItemDefinition<Item> STICKY_CARD = new ItemDefinition<>(AEItemIds.STICKY_CARD,
        Upgrades.createUpgradeCardItem());
    public static final ItemDefinition<EnergyCardItem> ENERGY_CARD = new ItemDefinition<>(AEItemIds.ENERGY_CARD,
        new EnergyCardItem(1));
    public static final ItemDefinition<Item> QUANTUM_BRIDGE_CARD = new ItemDefinition<>(AEItemIds.QUANTUM_BRIDGE_CARD,
        Upgrades.createUpgradeCardItem());
    public static final ItemDefinition<Item> MAGNET_CARD = new ItemDefinition<>(AEItemIds.MAGNET_CARD,
        Upgrades.createUpgradeCardItem());
    public static final ItemDefinition<MaterialItem> SPATIAL_2_CELL_COMPONENT = new ItemDefinition<>(
        AEItemIds.SPATIAL_2_CELL_COMPONENT, new MaterialItem());
    public static final ItemDefinition<MaterialItem> SPATIAL_16_CELL_COMPONENT = new ItemDefinition<>(
        AEItemIds.SPATIAL_16_CELL_COMPONENT, new MaterialItem());
    public static final ItemDefinition<MaterialItem> SPATIAL_128_CELL_COMPONENT = new ItemDefinition<>(
        AEItemIds.SPATIAL_128_CELL_COMPONENT, new MaterialItem());
    public static final ItemDefinition<StorageComponentItem> CELL_COMPONENT_1K = new ItemDefinition<>(
        AEItemIds.CELL_COMPONENT_1K, new StorageComponentItem(1));
    public static final ItemDefinition<StorageComponentItem> CELL_COMPONENT_4K = new ItemDefinition<>(
        AEItemIds.CELL_COMPONENT_4K, new StorageComponentItem(4));
    public static final ItemDefinition<StorageComponentItem> CELL_COMPONENT_16K = new ItemDefinition<>(
        AEItemIds.CELL_COMPONENT_16K, new StorageComponentItem(16));
    public static final ItemDefinition<StorageComponentItem> CELL_COMPONENT_64K = new ItemDefinition<>(
        AEItemIds.CELL_COMPONENT_64K, new StorageComponentItem(64));
    public static final ItemDefinition<StorageComponentItem> CELL_COMPONENT_256K = new ItemDefinition<>(
        AEItemIds.CELL_COMPONENT_256K, new StorageComponentItem(256));
    public static final ItemDefinition<SpatialStorageCellItem> SPATIAL_CELL2 = new ItemDefinition<>(
        AEItemIds.SPATIAL_CELL_2, new SpatialStorageCellItem(2));
    public static final ItemDefinition<SpatialStorageCellItem> SPATIAL_CELL16 = new ItemDefinition<>(
        AEItemIds.SPATIAL_CELL_16, new SpatialStorageCellItem(16));
    public static final ItemDefinition<SpatialStorageCellItem> SPATIAL_CELL128 = new ItemDefinition<>(
        AEItemIds.SPATIAL_CELL_128, new SpatialStorageCellItem(128));
    public static final ItemDefinition<CreativeCellItem> CREATIVE_CELL = new ItemDefinition<>(AEItemIds.CREATIVE_CELL,
        new CreativeCellItem());
    public static final ItemDefinition<VoidCellItem> VOID_CELL = new ItemDefinition<>(AEItemIds.VOID_CELL,
        new VoidCellItem());
    public static final ItemDefinition<BasicStorageCell> ITEM_CELL_1K = new ItemDefinition<>(AEItemIds.ITEM_CELL_1K,
        new BasicStorageCell(0.5f, 1, 8, 63, AEKeyType.items()));
    public static final ItemDefinition<BasicStorageCell> ITEM_CELL_4K = new ItemDefinition<>(AEItemIds.ITEM_CELL_4K,
        new BasicStorageCell(1.0f, 4, 32, 63, AEKeyType.items()));
    public static final ItemDefinition<BasicStorageCell> ITEM_CELL_16K = new ItemDefinition<>(AEItemIds.ITEM_CELL_16K,
        new BasicStorageCell(1.5f, 16, 128, 63, AEKeyType.items()));
    public static final ItemDefinition<BasicStorageCell> ITEM_CELL_64K = new ItemDefinition<>(AEItemIds.ITEM_CELL_64K,
        new BasicStorageCell(2.0f, 64, 512, 63, AEKeyType.items()));
    public static final ItemDefinition<BasicStorageCell> ITEM_CELL_256K = new ItemDefinition<>(AEItemIds.ITEM_CELL_256K,
        new BasicStorageCell(2.5f, 256, 2048, 63, AEKeyType.items()));
    public static final ItemDefinition<BasicStorageCell> FLUID_CELL_1K = new ItemDefinition<>(AEItemIds.FLUID_CELL_1K,
        new BasicStorageCell(0.5f, 1, 8, 18, AEKeyType.fluids()));
    public static final ItemDefinition<BasicStorageCell> FLUID_CELL_4K = new ItemDefinition<>(AEItemIds.FLUID_CELL_4K,
        new BasicStorageCell(1.0f, 4, 32, 18, AEKeyType.fluids()));
    public static final ItemDefinition<BasicStorageCell> FLUID_CELL_16K = new ItemDefinition<>(AEItemIds.FLUID_CELL_16K,
        new BasicStorageCell(1.5f, 16, 128, 18, AEKeyType.fluids()));
    public static final ItemDefinition<BasicStorageCell> FLUID_CELL_64K = new ItemDefinition<>(AEItemIds.FLUID_CELL_64K,
        new BasicStorageCell(2.0f, 64, 512, 18, AEKeyType.fluids()));
    public static final ItemDefinition<BasicStorageCell> FLUID_CELL_256K = new ItemDefinition<>(AEItemIds.FLUID_CELL_256K,
        new BasicStorageCell(2.5f, 256, 2048, 18, AEKeyType.fluids()));
    public static final ItemDefinition<MaterialItem> ITEM_CELL_HOUSING = new ItemDefinition<>(
        AEItemIds.ITEM_CELL_HOUSING, new MaterialItem());
    public static final ItemDefinition<MaterialItem> FLUID_CELL_HOUSING = new ItemDefinition<>(
        AEItemIds.FLUID_CELL_HOUSING, new MaterialItem());
    public static final ItemDefinition<MaterialItem> WIRELESS_RECEIVER = new ItemDefinition<>(
        AEItemIds.WIRELESS_RECEIVER, new MaterialItem());
    public static final ItemDefinition<MaterialItem> WIRELESS_BOOSTER = new ItemDefinition<>(
        AEItemIds.WIRELESS_BOOSTER, new MaterialItem());
    public static final ItemDefinition<MaterialItem> FORMATION_CORE = new ItemDefinition<>(AEItemIds.FORMATION_CORE,
        new MaterialItem());
    public static final ItemDefinition<MaterialItem> ANNIHILATION_CORE = new ItemDefinition<>(
        AEItemIds.ANNIHILATION_CORE, new MaterialItem());
    public static final ItemDefinition<MaterialItem> SKY_DUST = new ItemDefinition<>(AEItemIds.SKY_DUST,
        new MaterialItem());
    public static final ItemDefinition<MaterialItem> ENDER_DUST = new ItemDefinition<>(AEItemIds.ENDER_DUST,
        new MaterialItem());
    public static final ItemDefinition<MaterialItem> SINGULARITY = new ItemDefinition<>(AEItemIds.SINGULARITY,
        new MaterialItem());
    public static final ItemDefinition<MissingContentItem> MISSING_CONTENT = new ItemDefinition<>(
        AEItemIds.MISSING_CONTENT, new MissingContentItem(), DebugCreativeTab.INSTANCE);
    public static final ItemDefinition<QuantumEntangledSingularityItem> QUANTUM_ENTANGLED_SINGULARITY = new ItemDefinition<>(
        AEItemIds.QUANTUM_ENTANGLED_SINGULARITY, new QuantumEntangledSingularityItem());
    public static final ItemDefinition<ViewCellItem> VIEW_CELL = new ItemDefinition<>(AEItemIds.VIEW_CELL,
        new ViewCellItem());
    public static final ItemDefinition<NamePressItem> NAME_PRESS = new ItemDefinition<>(AEItemIds.NAME_PRESS,
        new NamePressItem());
    public static final ItemDefinition<MeteoriteCompassItem> METEORITE_COMPASS = new ItemDefinition<>(
        AEItemIds.METEORITE_COMPASS, new MeteoriteCompassItem());
    public static final ItemDefinition<PortableCellItem> PORTABLE_ITEM_CELL1K = new ItemDefinition<>(
        AEItemIds.PORTABLE_ITEM_CELL1K,
        new PortableCellItem(AEKeyType.items(), 54, appeng.container.GuiIds.GuiKey.PORTABLE_ITEM_CELL, StorageTier.SIZE_1K,
            getPortableCellBattery(), 0x80caff));
    public static final ItemDefinition<PortableCellItem> PORTABLE_ITEM_CELL4K = new ItemDefinition<>(
        AEItemIds.PORTABLE_ITEM_CELL4K,
        new PortableCellItem(AEKeyType.items(), 45, appeng.container.GuiIds.GuiKey.PORTABLE_ITEM_CELL, StorageTier.SIZE_4K,
            getPortableCellBattery(), 0x80caff));
    public static final ItemDefinition<PortableCellItem> PORTABLE_ITEM_CELL16K = new ItemDefinition<>(
        AEItemIds.PORTABLE_ITEM_CELL16K,
        new PortableCellItem(AEKeyType.items(), 36, appeng.container.GuiIds.GuiKey.PORTABLE_ITEM_CELL, StorageTier.SIZE_16K,
            getPortableCellBattery(), 0x80caff));
    public static final ItemDefinition<PortableCellItem> PORTABLE_ITEM_CELL64K = new ItemDefinition<>(
        AEItemIds.PORTABLE_ITEM_CELL64K,
        new PortableCellItem(AEKeyType.items(), 27, appeng.container.GuiIds.GuiKey.PORTABLE_ITEM_CELL, StorageTier.SIZE_64K,
            getPortableCellBattery(), 0x80caff));
    public static final ItemDefinition<PortableCellItem> PORTABLE_ITEM_CELL256K = new ItemDefinition<>(
        AEItemIds.PORTABLE_ITEM_CELL256K,
        new PortableCellItem(AEKeyType.items(), 18, appeng.container.GuiIds.GuiKey.PORTABLE_ITEM_CELL, StorageTier.SIZE_256K,
            getPortableCellBattery(), 0x80caff));
    public static final ItemDefinition<PortableCellItem> PORTABLE_FLUID_CELL1K = new ItemDefinition<>(
        AEItemIds.PORTABLE_FLUID_CELL1K,
        new PortableCellItem(AEKeyType.fluids(), 18, appeng.container.GuiIds.GuiKey.PORTABLE_FLUID_CELL, StorageTier.SIZE_1K,
            getPortableCellBattery(), 0x80caff));
    public static final ItemDefinition<PortableCellItem> PORTABLE_FLUID_CELL4K = new ItemDefinition<>(
        AEItemIds.PORTABLE_FLUID_CELL4K,
        new PortableCellItem(AEKeyType.fluids(), 18, appeng.container.GuiIds.GuiKey.PORTABLE_FLUID_CELL, StorageTier.SIZE_4K,
            getPortableCellBattery(), 0x80caff));
    public static final ItemDefinition<PortableCellItem> PORTABLE_FLUID_CELL16K = new ItemDefinition<>(
        AEItemIds.PORTABLE_FLUID_CELL16K,
        new PortableCellItem(AEKeyType.fluids(), 18, appeng.container.GuiIds.GuiKey.PORTABLE_FLUID_CELL, StorageTier.SIZE_16K,
            getPortableCellBattery(), 0x80caff));
    public static final ItemDefinition<PortableCellItem> PORTABLE_FLUID_CELL64K = new ItemDefinition<>(
        AEItemIds.PORTABLE_FLUID_CELL64K,
        new PortableCellItem(AEKeyType.fluids(), 18, appeng.container.GuiIds.GuiKey.PORTABLE_FLUID_CELL, StorageTier.SIZE_64K,
            getPortableCellBattery(), 0x80caff));
    public static final ItemDefinition<PortableCellItem> PORTABLE_FLUID_CELL256K = new ItemDefinition<>(
        AEItemIds.PORTABLE_FLUID_CELL256K,
        new PortableCellItem(AEKeyType.fluids(), 18, appeng.container.GuiIds.GuiKey.PORTABLE_FLUID_CELL, StorageTier.SIZE_256K,
            getPortableCellBattery(), 0x80caff));
    public static final ItemDefinition<WirelessTerminalItem> WIRELESS_TERMINAL = new ItemDefinition<>(
        AEItemIds.WIRELESS_TERMINAL, new WirelessTerminalItem(getWirelessTerminalBattery()));
    public static final ItemDefinition<WirelessCraftingTerminalItem> WIRELESS_CRAFTING_TERMINAL = new ItemDefinition<>(
        AEItemIds.WIRELESS_CRAFTING_TERMINAL, new WirelessCraftingTerminalItem(getWirelessTerminalBattery()));
    public static final ItemDefinition<WirelessPatternEncodingTerminalItem> WIRELESS_PATTERN_ENCODING_TERMINAL =
        new ItemDefinition<>(AEItemIds.WIRELESS_PATTERN_ENCODING_TERMINAL,
            new WirelessPatternEncodingTerminalItem(getWirelessTerminalBattery()));
    public static final ItemDefinition<WirelessPatternAccessTerminalItem> WIRELESS_PATTERN_ACCESS_TERMINAL =
        new ItemDefinition<>(AEItemIds.WIRELESS_PATTERN_ACCESS_TERMINAL,
            new WirelessPatternAccessTerminalItem(getWirelessTerminalBattery()));
    public static final ItemDefinition<WirelessUniversalTerminalItem> WIRELESS_UNIVERSAL_TERMINAL =
        new ItemDefinition<>(AEItemIds.WIRELESS_UNIVERSAL_TERMINAL,
            new WirelessUniversalTerminalItem(getWirelessTerminalBattery()));
    public static final ItemDefinition<ChargedStaffItem> CHARGED_STAFF = new ItemDefinition<>(
        AEItemIds.CHARGED_STAFF, new ChargedStaffItem());
    public static final ItemDefinition<MatterCannonItem> MATTER_CANNON = new ItemDefinition<>(AEItemIds.MATTER_CANNON,
        new MatterCannonItem());
    public static final ItemDefinition<EntropyManipulatorItem> ENTROPY_MANIPULATOR = new ItemDefinition<>(
        AEItemIds.ENTROPY_MANIPULATOR, new EntropyManipulatorItem());
    public static final ItemDefinition<NetworkToolItem> NETWORK_TOOL = new ItemDefinition<>(AEItemIds.NETWORK_TOOL,
        new NetworkToolItem());
    public static final ItemDefinition<MemoryCardItem> MEMORY_CARD = new ItemDefinition<>(AEItemIds.MEMORY_CARD,
        new MemoryCardItem());
    public static final ItemDefinition<DebugCardItem> DEBUG_CARD = new ItemDefinition<>(AEItemIds.DEBUG_CARD,
        new DebugCardItem(), DebugCreativeTab.INSTANCE);
    public static final ItemDefinition<EraserItem> DEBUG_ERASER = new ItemDefinition<>(AEItemIds.DEBUG_ERASER,
        new EraserItem(), DebugCreativeTab.INSTANCE);
    public static final ItemDefinition<MeteoritePlacerItem> DEBUG_METEORITE_PLACER = new ItemDefinition<>(
        AEItemIds.DEBUG_METEORITE_PLACER, new MeteoritePlacerItem(), DebugCreativeTab.INSTANCE);
    public static final ItemDefinition<ReplicatorCardItem> DEBUG_REPLICATOR_CARD = new ItemDefinition<>(
        AEItemIds.DEBUG_REPLICATOR_CARD, new ReplicatorCardItem(), DebugCreativeTab.INSTANCE);
    public static final ItemDefinition<ColorApplicatorItem> COLOR_APPLICATOR = new ItemDefinition<>(
        AEItemIds.COLOR_APPLICATOR, new ColorApplicatorItem());
    public static final ColoredItemDefinition<PaintBallItem> COLORED_PAINT_BALL = createColoredPaintBalls(
        AEItemIds.COLORED_PAINT_BALL, false);
    public static final ColoredItemDefinition<PaintBallItem> COLORED_LUMEN_PAINT_BALL = createColoredPaintBalls(
        AEItemIds.COLORED_LUMEN_PAINT_BALL, true);

    private static final ItemDefinition<?>[] ITEMS = {
        WRAPPED_GENERIC_STACK,
        FACADE,
        BLANK_PATTERN,
        CRAFTING_PATTERN,
        PROCESSING_PATTERN,
        CERTUS_QUARTZ_CRYSTAL,
        CERTUS_QUARTZ_CRYSTAL_CHARGED,
        CERTUS_QUARTZ_DUST,
        SILICON,
        MATTER_BALL,
        FLUIX_CRYSTAL,
        FLUIX_DUST,
        FLUIX_PEARL,
        CERTUS_QUARTZ_AXE,
        CERTUS_QUARTZ_HOE,
        CERTUS_QUARTZ_SHOVEL,
        CERTUS_QUARTZ_PICK,
        CERTUS_QUARTZ_SWORD,
        CERTUS_QUARTZ_WRENCH,
        CERTUS_QUARTZ_KNIFE,
        NETHER_QUARTZ_AXE,
        NETHER_QUARTZ_HOE,
        NETHER_QUARTZ_SHOVEL,
        NETHER_QUARTZ_PICK,
        NETHER_QUARTZ_SWORD,
        NETHER_QUARTZ_WRENCH,
        NETHER_QUARTZ_KNIFE,
        FLUIX_AXE,
        FLUIX_HOE,
        FLUIX_SHOVEL,
        FLUIX_PICK,
        FLUIX_SWORD,
        CALCULATION_PROCESSOR_PRESS,
        ENGINEERING_PROCESSOR_PRESS,
        LOGIC_PROCESSOR_PRESS,
        CALCULATION_PROCESSOR_PRINT,
        ENGINEERING_PROCESSOR_PRINT,
        LOGIC_PROCESSOR_PRINT,
        SILICON_PRESS,
        SILICON_PRINT,
        NAME_PRESS,
        LOGIC_PROCESSOR,
        CALCULATION_PROCESSOR,
        ENGINEERING_PROCESSOR,
        BASIC_CARD,
        REDSTONE_CARD,
        CAPACITY_CARD,
        VOID_CARD,
        ADVANCED_CARD,
        FUZZY_CARD,
        SPEED_CARD,
        INVERTER_CARD,
        CRAFTING_CARD,
        PSEUDO_CRAFTING_CARD,
        EQUAL_DISTRIBUTION_CARD,
        STICKY_CARD,
        ENERGY_CARD,
        QUANTUM_BRIDGE_CARD,
        MAGNET_CARD,
        SPATIAL_2_CELL_COMPONENT,
        SPATIAL_16_CELL_COMPONENT,
        SPATIAL_128_CELL_COMPONENT,
        CELL_COMPONENT_1K,
        CELL_COMPONENT_4K,
        CELL_COMPONENT_16K,
        CELL_COMPONENT_64K,
        CELL_COMPONENT_256K,
        SPATIAL_CELL2,
        SPATIAL_CELL16,
        SPATIAL_CELL128,
        CREATIVE_CELL,
        VOID_CELL,
        ITEM_CELL_1K,
        ITEM_CELL_4K,
        ITEM_CELL_16K,
        ITEM_CELL_64K,
        ITEM_CELL_256K,
        FLUID_CELL_1K,
        FLUID_CELL_4K,
        FLUID_CELL_16K,
        FLUID_CELL_64K,
        FLUID_CELL_256K,
        ITEM_CELL_HOUSING,
        FLUID_CELL_HOUSING,
        WIRELESS_RECEIVER,
        WIRELESS_BOOSTER,
        FORMATION_CORE,
        ANNIHILATION_CORE,
        SKY_DUST,
        ENDER_DUST,
        SINGULARITY,
        MISSING_CONTENT,
        QUANTUM_ENTANGLED_SINGULARITY,
        VIEW_CELL,
        METEORITE_COMPASS,
        PORTABLE_ITEM_CELL1K,
        PORTABLE_ITEM_CELL4K,
        PORTABLE_ITEM_CELL16K,
        PORTABLE_ITEM_CELL64K,
        PORTABLE_ITEM_CELL256K,
        PORTABLE_FLUID_CELL1K,
        PORTABLE_FLUID_CELL4K,
        PORTABLE_FLUID_CELL16K,
        PORTABLE_FLUID_CELL64K,
        PORTABLE_FLUID_CELL256K,
        WIRELESS_TERMINAL,
        WIRELESS_CRAFTING_TERMINAL,
        WIRELESS_PATTERN_ENCODING_TERMINAL,
        WIRELESS_PATTERN_ACCESS_TERMINAL,
        WIRELESS_UNIVERSAL_TERMINAL,
        CHARGED_STAFF,
        MATTER_CANNON,
        ENTROPY_MANIPULATOR,
        NETWORK_TOOL,
        MEMORY_CARD,
        DEBUG_CARD,
        DEBUG_ERASER,
        DEBUG_METEORITE_PLACER,
        DEBUG_REPLICATOR_CARD,
        COLOR_APPLICATOR
    };

    private AEItems() {
    }

    private static double getPortableCellBattery() {
        return getConfiguredBattery(AEConfig.instance()::getPortableCellBattery, 20000);
    }

    private static double getWirelessTerminalBattery() {
        return getConfiguredBattery(AEConfig.instance()::getWirelessTerminalBattery, 1600000);
    }

    private static double getConfiguredBattery(IntSupplier supplier, double fallback) {
        try {
            return supplier.getAsInt();
        } catch (IllegalStateException ignored) {
            return fallback;
        }
    }

    public static ItemDefinition<?>[] all() {
        return ITEMS.clone();
    }

    public static void register(RegistryEvent.Register<Item> event) {
        for (BlockDefinition<?> definition : AEBlocks.all()) {
            ItemBlock itemBlock = definition.item();
            if (itemBlock != null) {
                event.getRegistry().register(itemBlock);
            }
        }
        for (ItemDefinition<?> definition : ITEMS) {
            event.getRegistry().register(definition.item());
        }
        registerColored(event, COLORED_PAINT_BALL);
        registerColored(event, COLORED_LUMEN_PAINT_BALL);
        AEParts.register(event);
    }

    private static ColoredItemDefinition<PaintBallItem> createColoredPaintBalls(
        java.util.Map<AEColor, net.minecraft.util.ResourceLocation> ids, boolean lumen) {
        ColoredItemDefinition<PaintBallItem> definition = new ColoredItemDefinition<>();
        for (java.util.Map.Entry<AEColor, net.minecraft.util.ResourceLocation> entry : ids.entrySet()) {
            definition.add(entry.getKey(), entry.getValue(), new ItemDefinition<>(entry.getValue(),
                new PaintBallItem(entry.getKey(), lumen)));
        }
        return definition;
    }

    private static void registerColored(RegistryEvent.Register<Item> event, ColoredItemDefinition<?> definition) {
        for (AEColor color : AEColor.values()) {
            Item item = definition.item(color);
            if (item != null) {
                event.getRegistry().register(item);
            }
        }
    }
}
