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

package ae2.core.definitions;

import ae2.api.ids.AEBlockIds;
import ae2.core.AppEng;
import ae2.debug.TileCubeGenerator;
import ae2.debug.TileEnergyGenerator;
import ae2.debug.TileItemGen;
import ae2.debug.TilePhantomNode;
import ae2.tile.crafting.TileCraftingMonitor;
import ae2.tile.crafting.TileCraftingUnit;
import ae2.tile.crafting.TileMolecularAssembler;
import ae2.tile.crafting.TilePatternProvider;
import ae2.tile.crafting.TileRequester;
import ae2.tile.misc.TileCaner;
import ae2.tile.misc.TileCellWorkbench;
import ae2.tile.misc.TileCharger;
import ae2.tile.misc.TileCondenser;
import ae2.tile.misc.TileCrank;
import ae2.tile.misc.TileCrystalAssembler;
import ae2.tile.misc.TileCrystalFixer;
import ae2.tile.misc.TileGrowthAccelerator;
import ae2.tile.misc.TileIngredientBuffer;
import ae2.tile.misc.TileInscriber;
import ae2.tile.misc.TileInterface;
import ae2.tile.misc.TileLightDetector;
import ae2.tile.misc.TileMysteriousCube;
import ae2.tile.misc.TilePaint;
import ae2.tile.misc.TileVibrationChamber;
import ae2.tile.networking.TileCableBus;
import ae2.tile.networking.TileController;
import ae2.tile.networking.TileCreativeEnergyCell;
import ae2.tile.networking.TileCrystalResonanceGenerator;
import ae2.tile.networking.TileDenseBeamFormer;
import ae2.tile.networking.TileEnergyAcceptor;
import ae2.tile.networking.TileEnergyCell;
import ae2.tile.networking.TileWirelessAccessPoint;
import ae2.tile.qnb.TileQuantumBridge;
import ae2.tile.spatial.TileSpatialAnchor;
import ae2.tile.spatial.TileSpatialIOPort;
import ae2.tile.spatial.TileSpatialPylon;
import ae2.tile.storage.TileDrive;
import ae2.tile.storage.TileIOPort;
import ae2.tile.storage.TileMEChest;
import ae2.tile.storage.TileSkyChest;
import ae2.tile.storage.TileSkyStoneTank;

public final class AEBlockEntities {

    public static final TileDefinition<TileCableBus> CABLE_BUS = new TileDefinition<>(AppEng.makeId("cable_bus"),
        TileCableBus.class);
    public static final TileDefinition<TileQuantumBridge> QUANTUM_BRIDGE = new TileDefinition<>(
        AppEng.makeId("quantum_ring"), TileQuantumBridge.class);
    public static final TileDefinition<TileMysteriousCube> MYSTERIOUS_CUBE = new TileDefinition<>(
        AEBlockIds.MYSTERIOUS_CUBE, TileMysteriousCube.class);
    public static final TileDefinition<TileSkyStoneTank> SKY_STONE_TANK = new TileDefinition<>(
        AEBlockIds.SKY_STONE_TANK, TileSkyStoneTank.class);
    public static final TileDefinition<TileSkyChest> SKY_CHEST = new TileDefinition<>(AppEng.makeId("sky_chest"),
        TileSkyChest.class);
    public static final TileDefinition<TileVibrationChamber> VIBRATION_CHAMBER = new TileDefinition<>(
        AppEng.makeId("vibration_chamber"), TileVibrationChamber.class);
    public static final TileDefinition<TileLightDetector> LIGHT_DETECTOR = new TileDefinition<>(
        AEBlockIds.LIGHT_DETECTOR, TileLightDetector.class);
    public static final TileDefinition<TileCrank> CRANK = new TileDefinition<>(AEBlockIds.CRANK, TileCrank.class);
    public static final TileDefinition<TileEnergyGenerator> DEBUG_ENERGY_GEN = new TileDefinition<>(
        AEBlockIds.DEBUG_ENERGY_GEN, TileEnergyGenerator.class);
    public static final TileDefinition<TileItemGen> DEBUG_ITEM_GEN = new TileDefinition<>(
        AEBlockIds.DEBUG_ITEM_GEN, TileItemGen.class);
    public static final TileDefinition<TileCubeGenerator> DEBUG_CUBE_GEN = new TileDefinition<>(
        AEBlockIds.DEBUG_CUBE_GEN, TileCubeGenerator.class);
    public static final TileDefinition<TilePhantomNode> DEBUG_PHANTOM_NODE = new TileDefinition<>(
        AEBlockIds.DEBUG_PHANTOM_NODE, TilePhantomNode.class);
    public static final TileDefinition<TileController> CONTROLLER = new TileDefinition<>(
        AppEng.makeId("controller"), TileController.class);
    public static final TileDefinition<TileCreativeEnergyCell> CREATIVE_ENERGY_CELL = new TileDefinition<>(
        AppEng.makeId("creative_energy_cell"), TileCreativeEnergyCell.class);
    public static final TileDefinition<TileCrystalResonanceGenerator> CRYSTAL_RESONANCE_GENERATOR =
        new TileDefinition<>(AppEng.makeId("crystal_resonance_generator"), TileCrystalResonanceGenerator.class);
    public static final TileDefinition<TileEnergyAcceptor> ENERGY_ACCEPTOR = new TileDefinition<>(
        AppEng.makeId("energy_acceptor"), TileEnergyAcceptor.class);
    public static final TileDefinition<TileEnergyCell> ENERGY_CELL = new TileDefinition<>(
        AppEng.makeId("energy_cell"), TileEnergyCell.class);
    public static final TileDefinition<TileWirelessAccessPoint> WIRELESS_ACCESS_POINT = new TileDefinition<>(
        AppEng.makeId("wireless_access_point"), TileWirelessAccessPoint.class);
    public static final TileDefinition<TileDenseBeamFormer> DENSE_BEAM_FORMER = new TileDefinition<>(
        AEBlockIds.DENSE_BEAM_FORMER, TileDenseBeamFormer.class);
    public static final TileDefinition<TileMEChest> ME_CHEST = new TileDefinition<>(AppEng.makeId("chest"),
        TileMEChest.class);
    public static final TileDefinition<TileDrive> DRIVE = new TileDefinition<>(AppEng.makeId("drive"),
        TileDrive.class);
    public static final TileDefinition<TileCellWorkbench> CELL_WORKBENCH = new TileDefinition<>(
        AppEng.makeId("cell_workbench"), TileCellWorkbench.class);
    public static final TileDefinition<TileCondenser> CONDENSER = new TileDefinition<>(AppEng.makeId("condenser"),
        TileCondenser.class);
    public static final TileDefinition<TileCharger> CHARGER = new TileDefinition<>(AppEng.makeId("charger"),
        TileCharger.class);
    public static final TileDefinition<TileGrowthAccelerator> GROWTH_ACCELERATOR = new TileDefinition<>(
        AppEng.makeId("growth_accelerator"), TileGrowthAccelerator.class);
    public static final TileDefinition<TileInscriber> INSCRIBER = new TileDefinition<>(AppEng.makeId("inscriber"),
        TileInscriber.class);
    public static final TileDefinition<TileCrystalAssembler> CRYSTAL_ASSEMBLER = new TileDefinition<>(
        AppEng.makeId("crystal_assembler"), TileCrystalAssembler.class);
    public static final TileDefinition<TileCrystalFixer> CRYSTAL_FIXER = new TileDefinition<>(
        AppEng.makeId("crystal_fixer"), TileCrystalFixer.class);
    public static final TileDefinition<TileIngredientBuffer> INGREDIENT_BUFFER = new TileDefinition<>(
        AppEng.makeId("ingredient_buffer"), TileIngredientBuffer.class);
    public static final TileDefinition<TileCaner> CANER = new TileDefinition<>(AppEng.makeId("caner"),
        TileCaner.class);
    public static final TileDefinition<TilePatternProvider> PATTERN_PROVIDER = new TileDefinition<>(
        AppEng.makeId("pattern_provider"), TilePatternProvider.class);
    public static final TileDefinition<TileRequester> REQUESTER = new TileDefinition<>(AppEng.makeId("requester"),
        TileRequester.class);
    public static final TileDefinition<TileCraftingUnit> CRAFTING_UNIT = new TileDefinition<>(
        AppEng.makeId("crafting_unit"), TileCraftingUnit.class);
    public static final TileDefinition<TileCraftingMonitor> CRAFTING_MONITOR = new TileDefinition<>(
        AppEng.makeId("crafting_monitor"), TileCraftingMonitor.class);
    public static final TileDefinition<TileMolecularAssembler> MOLECULAR_ASSEMBLER = new TileDefinition<>(
        AppEng.makeId("molecular_assembler"), TileMolecularAssembler.class);
    public static final TileDefinition<TileInterface> INTERFACE = new TileDefinition<>(AppEng.makeId("interface"),
        TileInterface.class);
    public static final TileDefinition<TileIOPort> IO_PORT = new TileDefinition<>(AppEng.makeId("io_port"),
        TileIOPort.class);
    public static final TileDefinition<TileSpatialPylon> SPATIAL_PYLON = new TileDefinition<>(
        AppEng.makeId("spatial_pylon"), TileSpatialPylon.class);
    public static final TileDefinition<TileSpatialIOPort> SPATIAL_IO_PORT = new TileDefinition<>(
        AppEng.makeId("spatial_io_port"), TileSpatialIOPort.class);
    public static final TileDefinition<TileSpatialAnchor> SPATIAL_ANCHOR = new TileDefinition<>(
        AppEng.makeId("spatial_anchor"), TileSpatialAnchor.class);
    public static final TileDefinition<TilePaint> PAINT = new TileDefinition<>(AppEng.makeId("paint"),
        TilePaint.class);

    private AEBlockEntities() {
    }

    public static void init() {
        CABLE_BUS.register();
        QUANTUM_BRIDGE.register();
        MYSTERIOUS_CUBE.register();
        SKY_STONE_TANK.register();
        SKY_CHEST.register();
        VIBRATION_CHAMBER.register();
        LIGHT_DETECTOR.register();
        CRANK.register();
        DEBUG_ENERGY_GEN.register();
        DEBUG_ITEM_GEN.register();
        DEBUG_CUBE_GEN.register();
        DEBUG_PHANTOM_NODE.register();
        CONTROLLER.register();
        CREATIVE_ENERGY_CELL.register();
        CRYSTAL_RESONANCE_GENERATOR.register();
        ENERGY_ACCEPTOR.register();
        ENERGY_CELL.register();
        WIRELESS_ACCESS_POINT.register();
        DENSE_BEAM_FORMER.register();
        ME_CHEST.register();
        DRIVE.register();
        CELL_WORKBENCH.register();
        CONDENSER.register();
        CHARGER.register();
        GROWTH_ACCELERATOR.register();
        INSCRIBER.register();
        CRYSTAL_ASSEMBLER.register();
        CRYSTAL_FIXER.register();
        INGREDIENT_BUFFER.register();
        CANER.register();
        PATTERN_PROVIDER.register();
        REQUESTER.register();
        CRAFTING_UNIT.register();
        CRAFTING_MONITOR.register();
        MOLECULAR_ASSEMBLER.register();
        INTERFACE.register();
        IO_PORT.register();
        SPATIAL_PYLON.register();
        SPATIAL_IO_PORT.register();
        SPATIAL_ANCHOR.register();
        PAINT.register();
    }
}
