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

package ae2.core.definitions;

import ae2.api.ids.AEPartIds;
import ae2.api.parts.IPart;
import ae2.api.parts.IPartItem;
import ae2.api.parts.PartModels;
import ae2.api.util.AEColor;
import ae2.items.parts.ColoredPartItem;
import ae2.items.parts.PartItem;
import ae2.items.parts.PartModelsHelper;
import ae2.parts.automation.AnnihilationPlanePart;
import ae2.parts.automation.AnnihilationPlanePartItem;
import ae2.parts.automation.EnergyLevelEmitterPart;
import ae2.parts.automation.ExportBusPart;
import ae2.parts.automation.FormationPlanePart;
import ae2.parts.automation.ImportBusPart;
import ae2.parts.automation.StorageLevelEmitterPart;
import ae2.parts.automation.ThresholdLevelEmitterPart;
import ae2.parts.automation.special.ModExportBusPart;
import ae2.parts.automation.special.ModStorageBusPart;
import ae2.parts.automation.special.ODExportBusPart;
import ae2.parts.automation.special.ODStorageBusPart;
import ae2.parts.automation.special.PreciseExportBusPart;
import ae2.parts.automation.special.PreciseStorageBusPart;
import ae2.parts.automation.special.ThresholdExportBusPart;
import ae2.parts.crafting.PatternProviderPart;
import ae2.parts.encoding.PatternEncodingTerminalPart;
import ae2.parts.misc.CableAnchorPart;
import ae2.parts.misc.InterfacePart;
import ae2.parts.misc.InvertedToggleBusPart;
import ae2.parts.misc.ToggleBusPart;
import ae2.parts.networking.CoveredCablePart;
import ae2.parts.networking.CoveredDenseCablePart;
import ae2.parts.networking.EnergyAcceptorPart;
import ae2.parts.networking.GlassCablePart;
import ae2.parts.networking.QuartzFiberPart;
import ae2.parts.networking.SmartCablePart;
import ae2.parts.networking.SmartDenseCablePart;
import ae2.parts.p2p.FEP2PTunnelPart;
import ae2.parts.p2p.FluidP2PTunnelPart;
import ae2.parts.p2p.ItemP2PTunnelPart;
import ae2.parts.p2p.LightP2PTunnelPart;
import ae2.parts.p2p.MEP2PTunnelPart;
import ae2.parts.p2p.RedstoneP2PTunnelPart;
import ae2.parts.reporting.ConversionMonitorPart;
import ae2.parts.reporting.CraftingTerminalPart;
import ae2.parts.reporting.DarkPanelPart;
import ae2.parts.reporting.ItemTerminalPart;
import ae2.parts.reporting.PanelPart;
import ae2.parts.reporting.PatternAccessTerminalPart;
import ae2.parts.reporting.SemiDarkPanelPart;
import ae2.parts.reporting.StorageMonitorPart;
import ae2.parts.storagebus.StorageBusPart;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Internal implementation for the API parts
 */
public final class AEParts {
    public static final List<ColoredItemDefinition<?>> COLORED_PARTS = new ObjectArrayList<>();

    @SuppressWarnings("unused")
    public static final ColoredItemDefinition<ColoredPartItem<SmartCablePart>> SMART_CABLE = createColoredPart(
        AEPartIds.CABLE_SMART, SmartCablePart.class, SmartCablePart::new);
    @SuppressWarnings("unused")
    public static final ColoredItemDefinition<ColoredPartItem<CoveredCablePart>> COVERED_CABLE = createColoredPart(
        AEPartIds.CABLE_COVERED, CoveredCablePart.class, CoveredCablePart::new);
    @SuppressWarnings("unused")
    public static final ColoredItemDefinition<ColoredPartItem<GlassCablePart>> GLASS_CABLE = createColoredPart(
        AEPartIds.CABLE_GLASS, GlassCablePart.class, GlassCablePart::new);
    @SuppressWarnings("unused")
    public static final ColoredItemDefinition<ColoredPartItem<CoveredDenseCablePart>> COVERED_DENSE_CABLE = createColoredPart(
        AEPartIds.CABLE_DENSE_COVERED, CoveredDenseCablePart.class, CoveredDenseCablePart::new);
    @SuppressWarnings("unused")
    public static final ColoredItemDefinition<ColoredPartItem<SmartDenseCablePart>> SMART_DENSE_CABLE = createColoredPart(
        AEPartIds.CABLE_DENSE_SMART, SmartDenseCablePart.class, SmartDenseCablePart::new);
    public static final ItemDefinition<PartItem<StorageBusPart>> STORAGE_BUS = createPart(AEPartIds.STORAGE_BUS,
        StorageBusPart.class, StorageBusPart::new);
    public static final ItemDefinition<PartItem<ImportBusPart>> IMPORT_BUS = createPart(AEPartIds.IMPORT_BUS,
        ImportBusPart.class, ImportBusPart::new);
    public static final ItemDefinition<PartItem<ExportBusPart>> EXPORT_BUS = createPart(AEPartIds.EXPORT_BUS,
        ExportBusPart.class, ExportBusPart::new);
    public static final ItemDefinition<PartItem<ODStorageBusPart>> OD_STORAGE_BUS = createPart(
        AEPartIds.OD_STORAGE_BUS, ODStorageBusPart.class, ODStorageBusPart::new);
    public static final ItemDefinition<PartItem<ODExportBusPart>> OD_EXPORT_BUS = createPart(
        AEPartIds.OD_EXPORT_BUS, ODExportBusPart.class, ODExportBusPart::new);
    public static final ItemDefinition<PartItem<ModStorageBusPart>> MOD_STORAGE_BUS = createPart(
        AEPartIds.MOD_STORAGE_BUS, ModStorageBusPart.class, ModStorageBusPart::new);
    public static final ItemDefinition<PartItem<ModExportBusPart>> MOD_EXPORT_BUS = createPart(
        AEPartIds.MOD_EXPORT_BUS, ModExportBusPart.class, ModExportBusPart::new);
    public static final ItemDefinition<PartItem<PreciseStorageBusPart>> PRECISE_STORAGE_BUS = createPart(
        AEPartIds.PRECISE_STORAGE_BUS, PreciseStorageBusPart.class, PreciseStorageBusPart::new);
    public static final ItemDefinition<PartItem<PreciseExportBusPart>> PRECISE_EXPORT_BUS = createPart(
        AEPartIds.PRECISE_EXPORT_BUS, PreciseExportBusPart.class, PreciseExportBusPart::new);
    public static final ItemDefinition<PartItem<ThresholdExportBusPart>> THRESHOLD_EXPORT_BUS = createPart(
        AEPartIds.THRESHOLD_EXPORT_BUS, ThresholdExportBusPart.class, ThresholdExportBusPart::new);
    public static final ItemDefinition<PartItem<StorageLevelEmitterPart>> LEVEL_EMITTER = createPart(
        AEPartIds.LEVEL_EMITTER, StorageLevelEmitterPart.class, StorageLevelEmitterPart::new);
    public static final ItemDefinition<PartItem<EnergyLevelEmitterPart>> ENERGY_LEVEL_EMITTER = createPart(
        AEPartIds.ENERGY_LEVEL_EMITTER, EnergyLevelEmitterPart.class, EnergyLevelEmitterPart::new);
    public static final ItemDefinition<PartItem<ThresholdLevelEmitterPart>> THRESHOLD_LEVEL_EMITTER = createPart(
        AEPartIds.THRESHOLD_LEVEL_EMITTER, ThresholdLevelEmitterPart.class, ThresholdLevelEmitterPart::new);
    public static final ItemDefinition<PartItem<AnnihilationPlanePart>> ANNIHILATION_PLANE = createCustomPartItem(
        AnnihilationPlanePartItem::new);
    public static final ItemDefinition<PartItem<FormationPlanePart>> FORMATION_PLANE = createPart(
        AEPartIds.FORMATION_PLANE, FormationPlanePart.class, FormationPlanePart::new);
    public static final ItemDefinition<PartItem<CraftingTerminalPart>> CRAFTING_TERMINAL = createPart(
        AEPartIds.CRAFTING_TERMINAL, CraftingTerminalPart.class, CraftingTerminalPart::new);
    public static final ItemDefinition<PartItem<PatternProviderPart>> PATTERN_PROVIDER = createPart(
        AEPartIds.PATTERN_PROVIDER, PatternProviderPart.class, PatternProviderPart::new);
    public static final ItemDefinition<PartItem<InterfacePart>> INTERFACE = createPart(AEPartIds.INTERFACE,
        InterfacePart.class, InterfacePart::new);
    public static final ItemDefinition<PartItem<QuartzFiberPart>> QUARTZ_FIBER = createPart(
        AEPartIds.QUARTZ_FIBER, QuartzFiberPart.class, QuartzFiberPart::new);
    public static final ItemDefinition<PartItem<ToggleBusPart>> TOGGLE_BUS = createPart(
        AEPartIds.TOGGLE_BUS, ToggleBusPart.class, ToggleBusPart::new);
    public static final ItemDefinition<PartItem<InvertedToggleBusPart>> INVERTED_TOGGLE_BUS = createPart(
        AEPartIds.INVERTED_TOGGLE_BUS, InvertedToggleBusPart.class, InvertedToggleBusPart::new);
    public static final ItemDefinition<PartItem<CableAnchorPart>> CABLE_ANCHOR = createPart(
        AEPartIds.CABLE_ANCHOR, CableAnchorPart.class, CableAnchorPart::new);
    public static final ItemDefinition<PartItem<ItemTerminalPart>> TERMINAL = createPart(AEPartIds.TERMINAL,
        ItemTerminalPart.class, ItemTerminalPart::new);
    public static final ItemDefinition<PartItem<PanelPart>> MONITOR = createPart(AEPartIds.MONITOR,
        PanelPart.class, PanelPart::new);
    public static final ItemDefinition<PartItem<SemiDarkPanelPart>> SEMI_DARK_MONITOR = createPart(
        AEPartIds.SEMI_DARK_MONITOR, SemiDarkPanelPart.class, SemiDarkPanelPart::new);
    public static final ItemDefinition<PartItem<DarkPanelPart>> DARK_MONITOR = createPart(
        AEPartIds.DARK_MONITOR, DarkPanelPart.class, DarkPanelPart::new);
    public static final ItemDefinition<PartItem<StorageMonitorPart>> STORAGE_MONITOR = createPart(
        AEPartIds.STORAGE_MONITOR, StorageMonitorPart.class, StorageMonitorPart::new);
    public static final ItemDefinition<PartItem<ConversionMonitorPart>> CONVERSION_MONITOR = createPart(
        AEPartIds.CONVERSION_MONITOR, ConversionMonitorPart.class, ConversionMonitorPart::new);
    public static final ItemDefinition<PartItem<EnergyAcceptorPart>> ENERGY_ACCEPTOR = createPart(
        AEPartIds.ENERGY_ACCEPTOR, EnergyAcceptorPart.class, EnergyAcceptorPart::new);
    public static final ItemDefinition<PartItem<PatternEncodingTerminalPart>> PATTERN_ENCODING_TERMINAL = createPart(
        AEPartIds.PATTERN_ENCODING_TERMINAL, PatternEncodingTerminalPart.class, PatternEncodingTerminalPart::new);
    public static final ItemDefinition<PartItem<PatternAccessTerminalPart>> PATTERN_ACCESS_TERMINAL = createPart(
        AEPartIds.PATTERN_ACCESS_TERMINAL, PatternAccessTerminalPart.class, PatternAccessTerminalPart::new);
    public static final ItemDefinition<PartItem<MEP2PTunnelPart>> ME_P2P_TUNNEL = createPart(
        AEPartIds.ME_P2P_TUNNEL, MEP2PTunnelPart.class, MEP2PTunnelPart::new);
    public static final ItemDefinition<PartItem<RedstoneP2PTunnelPart>> REDSTONE_P2P_TUNNEL = createPart(
        AEPartIds.REDSTONE_P2P_TUNNEL, RedstoneP2PTunnelPart.class, RedstoneP2PTunnelPart::new);
    public static final ItemDefinition<PartItem<ItemP2PTunnelPart>> ITEM_P2P_TUNNEL = createPart(
        AEPartIds.ITEM_P2P_TUNNEL, ItemP2PTunnelPart.class, ItemP2PTunnelPart::new);
    public static final ItemDefinition<PartItem<FluidP2PTunnelPart>> FLUID_P2P_TUNNEL = createPart(
        AEPartIds.FLUID_P2P_TUNNEL, FluidP2PTunnelPart.class, FluidP2PTunnelPart::new);
    public static final ItemDefinition<PartItem<FEP2PTunnelPart>> FE_P2P_TUNNEL = createPart(
        AEPartIds.FE_P2P_TUNNEL, FEP2PTunnelPart.class, FEP2PTunnelPart::new);
    public static final ItemDefinition<PartItem<LightP2PTunnelPart>> LIGHT_P2P_TUNNEL = createPart(
        AEPartIds.LIGHT_P2P_TUNNEL, LightP2PTunnelPart.class, LightP2PTunnelPart::new);

    private static final ItemDefinition<?>[] PARTS = {
        STORAGE_BUS,
        IMPORT_BUS,
        EXPORT_BUS,
        OD_STORAGE_BUS,
        OD_EXPORT_BUS,
        MOD_STORAGE_BUS,
        MOD_EXPORT_BUS,
        PRECISE_STORAGE_BUS,
        PRECISE_EXPORT_BUS,
        THRESHOLD_EXPORT_BUS,
        LEVEL_EMITTER,
        ENERGY_LEVEL_EMITTER,
        THRESHOLD_LEVEL_EMITTER,
        ANNIHILATION_PLANE,
        FORMATION_PLANE,
        CRAFTING_TERMINAL,
        PATTERN_PROVIDER,
        INTERFACE,
        QUARTZ_FIBER,
        TOGGLE_BUS,
        INVERTED_TOGGLE_BUS,
        CABLE_ANCHOR,
        TERMINAL,
        MONITOR,
        SEMI_DARK_MONITOR,
        DARK_MONITOR,
        STORAGE_MONITOR,
        CONVERSION_MONITOR,
        ENERGY_ACCEPTOR,
        PATTERN_ENCODING_TERMINAL,
        PATTERN_ACCESS_TERMINAL,
        ME_P2P_TUNNEL,
        REDSTONE_P2P_TUNNEL,
        ITEM_P2P_TUNNEL,
        FLUID_P2P_TUNNEL,
        FE_P2P_TUNNEL,
        LIGHT_P2P_TUNNEL
    };

    private AEParts() {
        // Used to control in which order static constructors are called
    }

    public static void init() {
    }

    public static ItemDefinition<?>[] all() {
        return PARTS.clone();
    }

    public static void register(RegistryEvent.Register<Item> event) {
        for (ItemDefinition<?> definition : PARTS) {
            event.getRegistry().register(definition.item());
        }
        for (ColoredItemDefinition<?> definition : COLORED_PARTS) {
            for (AEColor color : AEColor.values()) {
                event.getRegistry().register(definition.item(color));
            }
        }
    }

    private static <T extends IPart> ItemDefinition<PartItem<T>> createPart(ResourceLocation id, Class<T> partClass,
                                                                            Function<IPartItem<T>, T> factory) {
        PartModels.registerModels(PartModelsHelper.createModels(partClass));
        return new ItemDefinition<>(id, new PartItem<>(partClass, factory));
    }

    private static <T extends IPart> ItemDefinition<PartItem<T>> createCustomPartItem(Supplier<PartItem<T>> factory) {
        PartModels.registerModels(PartModelsHelper.createModels(AnnihilationPlanePart.class));
        return new ItemDefinition<>(AEPartIds.ANNIHILATION_PLANE, factory.get());
    }

    private static <T extends IPart> ColoredItemDefinition<ColoredPartItem<T>> createColoredPart(
        Map<AEColor, ResourceLocation> ids, Class<T> partClass, Function<ColoredPartItem<T>, T> factory) {
        PartModels.registerModels(PartModelsHelper.createModels(partClass));

        ColoredItemDefinition<ColoredPartItem<T>> definition = new ColoredItemDefinition<>();
        for (AEColor color : AEColor.values()) {
            ResourceLocation id = ids.get(color);
            definition.add(color, id, new ItemDefinition<>(id, new ColoredPartItem<>(partClass, factory, color)));
        }

        COLORED_PARTS.add(definition);
        return definition;
    }
}
