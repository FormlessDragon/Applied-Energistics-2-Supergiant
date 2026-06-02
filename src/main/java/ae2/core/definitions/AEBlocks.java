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

import ae2.api.ids.AEBlockIds;
import ae2.block.crafting.CraftingBlockItem;
import ae2.block.crafting.CraftingMonitorBlock;
import ae2.block.crafting.CraftingUnitBlock;
import ae2.block.crafting.CraftingUnitType;
import ae2.block.crafting.MolecularAssemblerBlock;
import ae2.block.crafting.PatternProviderBlock;
import ae2.block.crafting.RequesterBlock;
import ae2.block.misc.AEDecorativeBlock;
import ae2.block.misc.BuddingCertusQuartzBlock;
import ae2.block.misc.CanerBlock;
import ae2.block.misc.CellWorkbenchBlock;
import ae2.block.misc.CertusQuartzClusterBlock;
import ae2.block.misc.ChargerBlock;
import ae2.block.misc.CondenserBlock;
import ae2.block.misc.CrankBlock;
import ae2.block.misc.CrystalAssemblerBlock;
import ae2.block.misc.CrystalFixerBlock;
import ae2.block.misc.EntroBuddingBlock;
import ae2.block.misc.EntroClusterBlock;
import ae2.block.misc.GrowthAcceleratorBlock;
import ae2.block.misc.IngredientBufferBlock;
import ae2.block.misc.InscriberBlock;
import ae2.block.misc.InterfaceBlock;
import ae2.block.misc.LightDetectorBlock;
import ae2.block.misc.MysteriousCubeBlock;
import ae2.block.misc.NotSoMysteriousCubeBlock;
import ae2.block.misc.QuartzFixtureBlock;
import ae2.block.misc.TinyTNTBlock;
import ae2.block.misc.VibrationChamberBlock;
import ae2.block.networking.CableBusBlock;
import ae2.block.networking.ControllerBlock;
import ae2.block.networking.CreativeEnergyCellBlock;
import ae2.block.networking.CrystalResonanceGeneratorBlock;
import ae2.block.networking.EnergyAcceptorBlock;
import ae2.block.networking.EnergyCellBlock;
import ae2.block.networking.EnergyCellBlockItem;
import ae2.block.networking.WirelessAccessPointBlock;
import ae2.block.paint.PaintSplotchesBlock;
import ae2.block.qnb.QuantumLinkChamberBlock;
import ae2.block.qnb.QuantumRingBlock;
import ae2.block.spatial.MatrixFrameBlock;
import ae2.block.spatial.SpatialAnchorBlock;
import ae2.block.spatial.SpatialIOPortBlock;
import ae2.block.spatial.SpatialPylonBlock;
import ae2.block.storage.DriveBlock;
import ae2.block.storage.IOPortBlock;
import ae2.block.storage.MEChestBlock;
import ae2.block.storage.SkyChestBlock;
import ae2.block.storage.SkyStoneTankBlock;
import ae2.core.AppEng;
import ae2.core.DebugCreativeTab;
import ae2.debug.CubeGeneratorBlock;
import ae2.debug.EnergyGeneratorBlock;
import ae2.debug.ItemGenBlock;
import ae2.debug.PhantomNodeBlock;
import ae2.decorative.slab.AEDoubleSlabBlock;
import ae2.decorative.slab.AEHalfSlabBlock;
import ae2.decorative.slab.AESlabItemBlock;
import ae2.decorative.solid.QuartzGlassBlock;
import ae2.decorative.solid.QuartzLampBlock;
import ae2.decorative.stair.AEDecorativeStairBlock;
import ae2.decorative.wall.AEDecorativeWallBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;

/**
 * Internal implementation for the API blocks
 */
public final class AEBlocks {

    public static final BlockDefinition<CableBusBlock> CABLE_BUS = new BlockDefinition<>(AppEng.makeId("cable_bus"),
        new CableBusBlock(), DebugCreativeTab.INSTANCE);
    public static final BlockDefinition<ControllerBlock> CONTROLLER = new BlockDefinition<>(AppEng.makeId("controller"),
        new ControllerBlock());
    public static final BlockDefinition<EnergyCellBlock> ENERGY_CELL = new BlockDefinition<>(
        AppEng.makeId("energy_cell"),
        new EnergyCellBlock(200000.0D, 800.0D, 200),
        EnergyCellBlockItem::new);
    public static final BlockDefinition<EnergyCellBlock> DENSE_ENERGY_CELL = new BlockDefinition<>(
        AppEng.makeId("dense_energy_cell"),
        new EnergyCellBlock(1600000.0D, 1600.0D, 1600),
        EnergyCellBlockItem::new);
    public static final BlockDefinition<CreativeEnergyCellBlock> CREATIVE_ENERGY_CELL = new BlockDefinition<>(
        AppEng.makeId("creative_energy_cell"),
        new CreativeEnergyCellBlock());
    public static final BlockDefinition<EnergyAcceptorBlock> ENERGY_ACCEPTOR = new BlockDefinition<>(
        AppEng.makeId("energy_acceptor"),
        new EnergyAcceptorBlock());
    public static final BlockDefinition<WirelessAccessPointBlock> WIRELESS_ACCESS_POINT = new BlockDefinition<>(
        AppEng.makeId("wireless_access_point"),
        new WirelessAccessPointBlock());
    public static final BlockDefinition<CrystalResonanceGeneratorBlock> CRYSTAL_RESONANCE_GENERATOR = new BlockDefinition<>(
        AppEng.makeId("crystal_resonance_generator"),
        new CrystalResonanceGeneratorBlock());
    public static final BlockDefinition<MEChestBlock> ME_CHEST = new BlockDefinition<>("ME Chest",
        AEBlockIds.ME_CHEST, new MEChestBlock());
    public static final BlockDefinition<DriveBlock> DRIVE = new BlockDefinition<>(AppEng.makeId("drive"),
        new DriveBlock());
    public static final BlockDefinition<CellWorkbenchBlock> CELL_WORKBENCH = new BlockDefinition<>(
        AppEng.makeId("cell_workbench"),
        new CellWorkbenchBlock());
    public static final BlockDefinition<CondenserBlock> CONDENSER = new BlockDefinition<>(AEBlockIds.CONDENSER,
        new CondenserBlock());
    public static final BlockDefinition<AEDecorativeBlock> QUARTZ_BLOCK = new BlockDefinition<>(
        AEBlockIds.QUARTZ_BLOCK,
        new AEDecorativeBlock(Material.ROCK, 1.5F, 6.0F));
    public static final BlockDefinition<AEDecorativeBlock> CUT_QUARTZ_BLOCK = new BlockDefinition<>(
        "Cut Certus Quartz Block",
        AEBlockIds.CUT_QUARTZ_BLOCK,
        new AEDecorativeBlock(Material.ROCK, 1.5F, 6.0F));
    public static final BlockDefinition<AEDecorativeBlock> SMOOTH_QUARTZ_BLOCK = new BlockDefinition<>(
        "Smooth Certus Quartz Block",
        AEBlockIds.SMOOTH_QUARTZ_BLOCK,
        new AEDecorativeBlock(Material.ROCK, 1.5F, 6.0F));
    public static final BlockDefinition<AEDecorativeBlock> QUARTZ_BRICKS = new BlockDefinition<>(
        "Certus Quartz Bricks",
        AEBlockIds.QUARTZ_BRICKS,
        new AEDecorativeBlock(Material.ROCK, 1.5F, 6.0F));
    public static final BlockDefinition<BlockRotatedPillar> QUARTZ_PILLAR = new BlockDefinition<>(
        "Certus Quartz Pillar",
        AEBlockIds.QUARTZ_PILLAR,
        new BlockRotatedPillar(Material.ROCK, MapColor.QUARTZ) {
            {
                this.setHardness(1.5F);
                this.setResistance(6.0F);
            }
        });
    public static final BlockDefinition<AEDecorativeBlock> CHISELED_QUARTZ_BLOCK = new BlockDefinition<>(
        "Chiseled Certus Quartz Block",
        AEBlockIds.CHISELED_QUARTZ_BLOCK,
        new AEDecorativeBlock(Material.ROCK, 1.5F, 6.0F));
    public static final BlockDefinition<AEDecorativeBlock> FLUIX_BLOCK = new BlockDefinition<>(
        AEBlockIds.FLUIX_BLOCK,
        new AEDecorativeBlock(Material.ROCK, 1.5F, 6.0F));
    public static final BlockDefinition<AEDecorativeBlock> ENTRO_BLOCK = new BlockDefinition<>(
        AEBlockIds.ENTRO_BLOCK,
        new AEDecorativeBlock(Material.ROCK, 1.5F, 6.0F));
    public static final BlockDefinition<AEDecorativeBlock> MACHINE_FRAME = new BlockDefinition<>(
        AEBlockIds.MACHINE_FRAME,
        new AEDecorativeBlock(Material.ROCK, 1.5F, 6.0F));
    public static final BlockDefinition<QuartzGlassBlock> QUARTZ_GLASS = new BlockDefinition<>(
        AEBlockIds.QUARTZ_GLASS,
        new QuartzGlassBlock());
    public static final BlockDefinition<QuartzLampBlock> QUARTZ_VIBRANT_GLASS = new BlockDefinition<>(
        AEBlockIds.QUARTZ_VIBRANT_GLASS,
        new QuartzLampBlock());
    public static final BlockDefinition<QuartzFixtureBlock> QUARTZ_FIXTURE = new BlockDefinition<>(
        AEBlockIds.QUARTZ_FIXTURE,
        new QuartzFixtureBlock());
    public static final BlockDefinition<LightDetectorBlock> LIGHT_DETECTOR = new BlockDefinition<>(
        AEBlockIds.LIGHT_DETECTOR,
        new LightDetectorBlock());
    public static final BlockDefinition<BuddingCertusQuartzBlock> FLAWLESS_BUDDING_QUARTZ = new BlockDefinition<>(
        AEBlockIds.FLAWLESS_BUDDING_QUARTZ,
        new BuddingCertusQuartzBlock());
    public static final BlockDefinition<BuddingCertusQuartzBlock> FLAWED_BUDDING_QUARTZ = new BlockDefinition<>(
        AEBlockIds.FLAWED_BUDDING_QUARTZ,
        new BuddingCertusQuartzBlock());
    public static final BlockDefinition<BuddingCertusQuartzBlock> CHIPPED_BUDDING_QUARTZ = new BlockDefinition<>(
        AEBlockIds.CHIPPED_BUDDING_QUARTZ,
        new BuddingCertusQuartzBlock());
    public static final BlockDefinition<BuddingCertusQuartzBlock> DAMAGED_BUDDING_QUARTZ = new BlockDefinition<>(
        AEBlockIds.DAMAGED_BUDDING_QUARTZ,
        new BuddingCertusQuartzBlock());
    public static final BlockDefinition<CertusQuartzClusterBlock> SMALL_QUARTZ_BUD = new BlockDefinition<>(
        AEBlockIds.SMALL_QUARTZ_BUD,
        new CertusQuartzClusterBlock(4, 3, 0.0625F));
    public static final BlockDefinition<CertusQuartzClusterBlock> MEDIUM_QUARTZ_BUD = new BlockDefinition<>(
        AEBlockIds.MEDIUM_QUARTZ_BUD,
        new CertusQuartzClusterBlock(4, 4, 0.125F));
    public static final BlockDefinition<CertusQuartzClusterBlock> LARGE_QUARTZ_BUD = new BlockDefinition<>(
        AEBlockIds.LARGE_QUARTZ_BUD,
        new CertusQuartzClusterBlock(3, 5, 0.25F));
    public static final BlockDefinition<CertusQuartzClusterBlock> QUARTZ_CLUSTER = new BlockDefinition<>(
        AEBlockIds.QUARTZ_CLUSTER,
        new CertusQuartzClusterBlock(3, 7, 0.5F));
    public static final BlockDefinition<EntroBuddingBlock> ENTRO_BUDDING_FULLY = new BlockDefinition<>(
        AEBlockIds.ENTRO_BUDDING_FULLY,
        new EntroBuddingBlock(EntroBuddingBlock.Stage.FULLY));
    public static final BlockDefinition<EntroBuddingBlock> ENTRO_BUDDING_MOSTLY = new BlockDefinition<>(
        AEBlockIds.ENTRO_BUDDING_MOSTLY,
        new EntroBuddingBlock(EntroBuddingBlock.Stage.MOSTLY));
    public static final BlockDefinition<EntroBuddingBlock> ENTRO_BUDDING_HALF = new BlockDefinition<>(
        AEBlockIds.ENTRO_BUDDING_HALF,
        new EntroBuddingBlock(EntroBuddingBlock.Stage.HALF));
    public static final BlockDefinition<EntroBuddingBlock> ENTRO_BUDDING_HARDLY = new BlockDefinition<>(
        AEBlockIds.ENTRO_BUDDING_HARDLY,
        new EntroBuddingBlock(EntroBuddingBlock.Stage.HARDLY));
    public static final BlockDefinition<EntroClusterBlock> ENTRO_CLUSTER_SMALL = new BlockDefinition<>(
        AEBlockIds.ENTRO_CLUSTER_SMALL,
        new EntroClusterBlock(4, 3, 0.0625F, false));
    public static final BlockDefinition<EntroClusterBlock> ENTRO_CLUSTER_MEDIUM = new BlockDefinition<>(
        AEBlockIds.ENTRO_CLUSTER_MEDIUM,
        new EntroClusterBlock(4, 4, 0.125F, false));
    public static final BlockDefinition<EntroClusterBlock> ENTRO_CLUSTER_LARGE = new BlockDefinition<>(
        AEBlockIds.ENTRO_CLUSTER_LARGE,
        new EntroClusterBlock(3, 5, 0.25F, false));
    public static final BlockDefinition<EntroClusterBlock> ENTRO_CLUSTER = new BlockDefinition<>(
        AEBlockIds.ENTRO_CLUSTER,
        new EntroClusterBlock(3, 7, 0.5F, true));
    public static final BlockDefinition<AEDecorativeBlock> SKY_STONE_BLOCK = new BlockDefinition<>(
        AEBlockIds.SKY_STONE_BLOCK,
        new AEDecorativeBlock(Material.ROCK, 50.0F, 150.0F));
    public static final BlockDefinition<AEDecorativeBlock> SMOOTH_SKY_STONE_BLOCK = new BlockDefinition<>(
        "Sky Stone Block",
        AEBlockIds.SMOOTH_SKY_STONE_BLOCK,
        new AEDecorativeBlock(Material.ROCK, 50.0F, 150.0F));
    public static final BlockDefinition<AEDecorativeBlock> SKY_STONE_BRICK = new BlockDefinition<>(
        AEBlockIds.SKY_STONE_BRICK,
        new AEDecorativeBlock(Material.ROCK, 50.0F, 150.0F));
    public static final BlockDefinition<AEDecorativeBlock> SKY_STONE_SMALL_BRICK = new BlockDefinition<>(
        AEBlockIds.SKY_STONE_SMALL_BRICK,
        new AEDecorativeBlock(Material.ROCK, 50.0F, 150.0F));
    public static final BlockDefinition<AEDecorativeStairBlock> SKY_STONE_STAIRS = new BlockDefinition<>(
        AEBlockIds.SKY_STONE_STAIRS,
        new AEDecorativeStairBlock(SKY_STONE_BLOCK.block().getDefaultState(), 50.0F, 150.0F));
    public static final BlockDefinition<AEDecorativeStairBlock> SMOOTH_SKY_STONE_STAIRS = new BlockDefinition<>(
        "Sky Stone Block Stairs",
        AEBlockIds.SMOOTH_SKY_STONE_STAIRS,
        new AEDecorativeStairBlock(SMOOTH_SKY_STONE_BLOCK.block().getDefaultState(), 50.0F, 150.0F));
    public static final BlockDefinition<AEDecorativeStairBlock> SKY_STONE_BRICK_STAIRS = new BlockDefinition<>(
        AEBlockIds.SKY_STONE_BRICK_STAIRS,
        new AEDecorativeStairBlock(SKY_STONE_BRICK.block().getDefaultState(), 50.0F, 150.0F));
    public static final BlockDefinition<AEDecorativeStairBlock> SKY_STONE_SMALL_BRICK_STAIRS = new BlockDefinition<>(
        AEBlockIds.SKY_STONE_SMALL_BRICK_STAIRS,
        new AEDecorativeStairBlock(SKY_STONE_SMALL_BRICK.block().getDefaultState(), 50.0F, 150.0F));
    public static final BlockDefinition<AEDecorativeStairBlock> FLUIX_STAIRS = new BlockDefinition<>(
        AEBlockIds.FLUIX_STAIRS,
        new AEDecorativeStairBlock(FLUIX_BLOCK.block().getDefaultState(), 1.5F, 6.0F));
    public static final BlockDefinition<AEDecorativeStairBlock> QUARTZ_STAIRS = new BlockDefinition<>(
        "Certus Quartz Stairs",
        AEBlockIds.QUARTZ_STAIRS,
        new AEDecorativeStairBlock(QUARTZ_BLOCK.block().getDefaultState(), 1.5F, 6.0F));
    public static final BlockDefinition<AEDecorativeStairBlock> CUT_QUARTZ_STAIRS = new BlockDefinition<>(
        "Cut Certus Quartz Stairs",
        AEBlockIds.CUT_QUARTZ_STAIRS,
        new AEDecorativeStairBlock(CUT_QUARTZ_BLOCK.block().getDefaultState(), 1.5F, 6.0F));
    public static final BlockDefinition<AEDecorativeStairBlock> SMOOTH_QUARTZ_STAIRS = new BlockDefinition<>(
        "Smooth Certus Quartz Stairs",
        AEBlockIds.SMOOTH_QUARTZ_STAIRS,
        new AEDecorativeStairBlock(SMOOTH_QUARTZ_BLOCK.block().getDefaultState(), 1.5F, 6.0F));
    public static final BlockDefinition<AEDecorativeStairBlock> QUARTZ_BRICK_STAIRS = new BlockDefinition<>(
        "Certus Quartz Brick Stairs",
        AEBlockIds.QUARTZ_BRICK_STAIRS,
        new AEDecorativeStairBlock(QUARTZ_BRICKS.block().getDefaultState(), 1.5F, 6.0F));
    public static final BlockDefinition<AEDecorativeStairBlock> CHISELED_QUARTZ_STAIRS = new BlockDefinition<>(
        "Chiseled Certus Quartz Stairs",
        AEBlockIds.CHISELED_QUARTZ_STAIRS,
        new AEDecorativeStairBlock(CHISELED_QUARTZ_BLOCK.block().getDefaultState(), 1.5F, 6.0F));
    public static final BlockDefinition<AEDecorativeStairBlock> QUARTZ_PILLAR_STAIRS = new BlockDefinition<>(
        "Certus Quartz Pillar Stairs",
        AEBlockIds.QUARTZ_PILLAR_STAIRS,
        new AEDecorativeStairBlock(QUARTZ_PILLAR.block().getDefaultState(), 1.5F, 6.0F));
    public static final BlockDefinition<AEDecorativeWallBlock> SKY_STONE_WALL = new BlockDefinition<>(
        AEBlockIds.SKY_STONE_WALL,
        new AEDecorativeWallBlock(SKY_STONE_BLOCK.block(), 50.0F, 150.0F));
    public static final BlockDefinition<AEDecorativeWallBlock> SMOOTH_SKY_STONE_WALL = new BlockDefinition<>(
        "Sky Stone Block Wall",
        AEBlockIds.SMOOTH_SKY_STONE_WALL,
        new AEDecorativeWallBlock(SMOOTH_SKY_STONE_BLOCK.block(), 50.0F, 150.0F));
    public static final BlockDefinition<AEDecorativeWallBlock> SKY_STONE_BRICK_WALL = new BlockDefinition<>(
        AEBlockIds.SKY_STONE_BRICK_WALL,
        new AEDecorativeWallBlock(SKY_STONE_BRICK.block(), 50.0F, 150.0F));
    public static final BlockDefinition<AEDecorativeWallBlock> SKY_STONE_SMALL_BRICK_WALL = new BlockDefinition<>(
        AEBlockIds.SKY_STONE_SMALL_BRICK_WALL,
        new AEDecorativeWallBlock(SKY_STONE_SMALL_BRICK.block(), 50.0F, 150.0F));
    public static final BlockDefinition<AEDecorativeWallBlock> FLUIX_WALL = new BlockDefinition<>(
        AEBlockIds.FLUIX_WALL,
        new AEDecorativeWallBlock(FLUIX_BLOCK.block(), 1.5F, 6.0F));
    public static final BlockDefinition<AEDecorativeWallBlock> QUARTZ_WALL = new BlockDefinition<>(
        "Certus Quartz Wall",
        AEBlockIds.QUARTZ_WALL,
        new AEDecorativeWallBlock(QUARTZ_BLOCK.block(), 1.5F, 6.0F));
    public static final BlockDefinition<AEDecorativeWallBlock> CUT_QUARTZ_WALL = new BlockDefinition<>(
        "Cut Certus Quartz Wall",
        AEBlockIds.CUT_QUARTZ_WALL,
        new AEDecorativeWallBlock(CUT_QUARTZ_BLOCK.block(), 1.5F, 6.0F));
    public static final BlockDefinition<AEDecorativeWallBlock> SMOOTH_QUARTZ_WALL = new BlockDefinition<>(
        "Smooth Certus Quartz Wall",
        AEBlockIds.SMOOTH_QUARTZ_WALL,
        new AEDecorativeWallBlock(SMOOTH_QUARTZ_BLOCK.block(), 1.5F, 6.0F));
    public static final BlockDefinition<AEDecorativeWallBlock> QUARTZ_BRICK_WALL = new BlockDefinition<>(
        "Certus Quartz Brick Wall",
        AEBlockIds.QUARTZ_BRICK_WALL,
        new AEDecorativeWallBlock(QUARTZ_BRICKS.block(), 1.5F, 6.0F));
    public static final BlockDefinition<AEDecorativeWallBlock> CHISELED_QUARTZ_WALL = new BlockDefinition<>(
        "Chiseled Certus Quartz Wall",
        AEBlockIds.CHISELED_QUARTZ_WALL,
        new AEDecorativeWallBlock(CHISELED_QUARTZ_BLOCK.block(), 1.5F, 6.0F));
    public static final BlockDefinition<AEDecorativeWallBlock> QUARTZ_PILLAR_WALL = new BlockDefinition<>(
        "Certus Quartz Pillar Wall",
        AEBlockIds.QUARTZ_PILLAR_WALL,
        new AEDecorativeWallBlock(QUARTZ_PILLAR.block(), 1.5F, 6.0F));
    public static final BlockDefinition<MysteriousCubeBlock> MYSTERIOUS_CUBE = new BlockDefinition<>(
        AEBlockIds.MYSTERIOUS_CUBE,
        new MysteriousCubeBlock());
    public static final BlockDefinition<NotSoMysteriousCubeBlock> NOT_SO_MYSTERIOUS_CUBE = new BlockDefinition<>(
        AEBlockIds.NOT_SO_MYSTERIOUS_CUBE,
        new NotSoMysteriousCubeBlock());
    public static final BlockDefinition<SkyChestBlock> SKY_STONE_CHEST = new BlockDefinition<>(
        "Sky Stone Chest",
        AEBlockIds.SKY_STONE_CHEST,
        new SkyChestBlock(SkyChestBlock.SkyChestType.STONE));
    public static final BlockDefinition<SkyChestBlock> SMOOTH_SKY_STONE_CHEST = new BlockDefinition<>(
        "Sky Stone Block Chest",
        AEBlockIds.SMOOTH_SKY_STONE_CHEST,
        new SkyChestBlock(SkyChestBlock.SkyChestType.BLOCK));
    @SuppressWarnings("unused")
    public static final BlockDefinition<SkyChestBlock> SKY_CHEST = SMOOTH_SKY_STONE_CHEST;
    public static final BlockDefinition<SkyStoneTankBlock> SKY_STONE_TANK = new BlockDefinition<>(
        AEBlockIds.SKY_STONE_TANK,
        new SkyStoneTankBlock());
    public static final BlockDefinition<ChargerBlock> CHARGER = new BlockDefinition<>(AppEng.makeId("charger"),
        new ChargerBlock());
    public static final BlockDefinition<CrankBlock> CRANK = new BlockDefinition<>(AEBlockIds.CRANK,
        new CrankBlock());
    public static final BlockDefinition<VibrationChamberBlock> VIBRATION_CHAMBER = new BlockDefinition<>(
        AppEng.makeId("vibration_chamber"),
        new VibrationChamberBlock());
    public static final BlockDefinition<GrowthAcceleratorBlock> GROWTH_ACCELERATOR = new BlockDefinition<>(
        AEBlockIds.GROWTH_ACCELERATOR,
        new GrowthAcceleratorBlock());
    public static final BlockDefinition<InscriberBlock> INSCRIBER = new BlockDefinition<>(AppEng.makeId("inscriber"),
        new InscriberBlock());
    public static final BlockDefinition<CrystalAssemblerBlock> CRYSTAL_ASSEMBLER = new BlockDefinition<>(
        AEBlockIds.CRYSTAL_ASSEMBLER,
        new CrystalAssemblerBlock());
    public static final BlockDefinition<CrystalFixerBlock> CRYSTAL_FIXER = new BlockDefinition<>(
        AEBlockIds.CRYSTAL_FIXER,
        new CrystalFixerBlock());
    public static final BlockDefinition<IngredientBufferBlock> INGREDIENT_BUFFER = new BlockDefinition<>(
        AEBlockIds.INGREDIENT_BUFFER,
        new IngredientBufferBlock());
    public static final BlockDefinition<CanerBlock> CANER = new BlockDefinition<>(
        AEBlockIds.CANER,
        new CanerBlock());
    public static final BlockDefinition<PatternProviderBlock> PATTERN_PROVIDER = new BlockDefinition<>(
        AppEng.makeId("pattern_provider"),
        new PatternProviderBlock());
    public static final BlockDefinition<RequesterBlock> REQUESTER = new BlockDefinition<>(
        AEBlockIds.REQUESTER,
        new RequesterBlock());
    public static final BlockDefinition<CraftingUnitBlock> CRAFTING_UNIT = new BlockDefinition<>(
        AppEng.makeId("crafting_unit"),
        new CraftingUnitBlock(CraftingUnitType.UNIT),
        CraftingBlockItem::new);
    public static final BlockDefinition<CraftingUnitBlock> CRAFTING_ACCELERATOR = new BlockDefinition<>(
        AppEng.makeId("crafting_accelerator"),
        new CraftingUnitBlock(CraftingUnitType.ACCELERATOR),
        CraftingBlockItem::new);
    public static final BlockDefinition<CraftingUnitBlock> CRAFTING_ACCELERATOR_4X = new BlockDefinition<>(
        AEBlockIds.CRAFTING_ACCELERATOR_4X,
        new CraftingUnitBlock(CraftingUnitType.ACCELERATOR_4X),
        CraftingBlockItem::new);
    public static final BlockDefinition<CraftingUnitBlock> CRAFTING_STORAGE_1K = new BlockDefinition<>(
        AppEng.makeId("1k_crafting_storage"),
        new CraftingUnitBlock(CraftingUnitType.STORAGE_1K),
        CraftingBlockItem::new);
    public static final BlockDefinition<CraftingUnitBlock> CRAFTING_STORAGE_4K = new BlockDefinition<>(
        AppEng.makeId("4k_crafting_storage"),
        new CraftingUnitBlock(CraftingUnitType.STORAGE_4K),
        CraftingBlockItem::new);
    public static final BlockDefinition<CraftingUnitBlock> CRAFTING_STORAGE_16K = new BlockDefinition<>(
        AppEng.makeId("16k_crafting_storage"),
        new CraftingUnitBlock(CraftingUnitType.STORAGE_16K),
        CraftingBlockItem::new);
    public static final BlockDefinition<CraftingUnitBlock> CRAFTING_STORAGE_64K = new BlockDefinition<>(
        AppEng.makeId("64k_crafting_storage"),
        new CraftingUnitBlock(CraftingUnitType.STORAGE_64K),
        CraftingBlockItem::new);
    public static final BlockDefinition<CraftingUnitBlock> CRAFTING_STORAGE_256K = new BlockDefinition<>(
        AppEng.makeId("256k_crafting_storage"),
        new CraftingUnitBlock(CraftingUnitType.STORAGE_256K),
        CraftingBlockItem::new);
    public static final BlockDefinition<CraftingMonitorBlock> CRAFTING_MONITOR = new BlockDefinition<>(
        AppEng.makeId("crafting_monitor"),
        new CraftingMonitorBlock(CraftingUnitType.MONITOR),
        CraftingBlockItem::new);
    public static final BlockDefinition<MolecularAssemblerBlock> MOLECULAR_ASSEMBLER = new BlockDefinition<>(
        AppEng.makeId("molecular_assembler"),
        new MolecularAssemblerBlock());
    public static final BlockDefinition<InterfaceBlock> INTERFACE = new BlockDefinition<>(AppEng.makeId("interface"),
        new InterfaceBlock());
    public static final BlockDefinition<IOPortBlock> IO_PORT = new BlockDefinition<>(AppEng.makeId("io_port"),
        new IOPortBlock());
    public static final BlockDefinition<QuantumRingBlock> QUANTUM_RING = new BlockDefinition<>(
        AppEng.makeId("quantum_ring"),
        new QuantumRingBlock());
    public static final BlockDefinition<QuantumLinkChamberBlock> QUANTUM_LINK = new BlockDefinition<>(
        AppEng.makeId("quantum_link"),
        new QuantumLinkChamberBlock());
    public static final BlockDefinition<SpatialPylonBlock> SPATIAL_PYLON = new BlockDefinition<>(
        AppEng.makeId("spatial_pylon"),
        new SpatialPylonBlock());
    public static final BlockDefinition<SpatialIOPortBlock> SPATIAL_IO_PORT = new BlockDefinition<>(
        AppEng.makeId("spatial_io_port"),
        new SpatialIOPortBlock());
    public static final BlockDefinition<SpatialAnchorBlock> SPATIAL_ANCHOR = new BlockDefinition<>(
        AppEng.makeId("spatial_anchor"),
        new SpatialAnchorBlock());
    public static final BlockDefinition<TinyTNTBlock> TINY_TNT = new BlockDefinition<>(AEBlockIds.TINY_TNT,
        new TinyTNTBlock(Material.TNT));
    public static final BlockDefinition<MatrixFrameBlock> MATRIX_FRAME = new BlockDefinition<>(
        AppEng.makeId("matrix_frame"),
        new MatrixFrameBlock(), DebugCreativeTab.INSTANCE);
    public static final BlockDefinition<PaintSplotchesBlock> PAINT = new BlockDefinition<>(AppEng.makeId("paint"),
        new PaintSplotchesBlock(), DebugCreativeTab.INSTANCE);
    public static final BlockDefinition<EnergyGeneratorBlock> DEBUG_ENERGY_GEN = new BlockDefinition<>(
        AEBlockIds.DEBUG_ENERGY_GEN,
        new EnergyGeneratorBlock(), DebugCreativeTab.INSTANCE);
    public static final BlockDefinition<ItemGenBlock> DEBUG_ITEM_GEN = new BlockDefinition<>(
        AEBlockIds.DEBUG_ITEM_GEN,
        new ItemGenBlock(), DebugCreativeTab.INSTANCE);
    public static final BlockDefinition<CubeGeneratorBlock> DEBUG_CUBE_GEN = new BlockDefinition<>(
        AEBlockIds.DEBUG_CUBE_GEN,
        new CubeGeneratorBlock(), DebugCreativeTab.INSTANCE);
    public static final BlockDefinition<PhantomNodeBlock> DEBUG_PHANTOM_NODE = new BlockDefinition<>(
        AEBlockIds.DEBUG_PHANTOM_NODE,
        new PhantomNodeBlock(), DebugCreativeTab.INSTANCE);
    private static final SlabDefinitions SKY_STONE_SLABS = createSlab("Sky Stone Slab", AEBlockIds.SKY_STONE_SLAB,
        MapColor.BLACK, 50.0F, 150.0F);
    public static final BlockDefinition<AEHalfSlabBlock> SKY_STONE_SLAB = SKY_STONE_SLABS.half;
    public static final BlockDefinition<AEDoubleSlabBlock> SKY_STONE_SLAB_DOUBLE = SKY_STONE_SLABS.dbl;
    private static final SlabDefinitions SMOOTH_SKY_STONE_SLABS = createSlab("Sky Stone Block Slab",
        AEBlockIds.SMOOTH_SKY_STONE_SLAB, MapColor.BLACK, 50.0F, 150.0F);
    public static final BlockDefinition<AEHalfSlabBlock> SMOOTH_SKY_STONE_SLAB = SMOOTH_SKY_STONE_SLABS.half;
    public static final BlockDefinition<AEDoubleSlabBlock> SMOOTH_SKY_STONE_SLAB_DOUBLE = SMOOTH_SKY_STONE_SLABS.dbl;
    private static final SlabDefinitions SKY_STONE_BRICK_SLABS = createSlab("Sky Stone Brick Slab",
        AEBlockIds.SKY_STONE_BRICK_SLAB, MapColor.BLACK, 50.0F, 150.0F);
    public static final BlockDefinition<AEHalfSlabBlock> SKY_STONE_BRICK_SLAB = SKY_STONE_BRICK_SLABS.half;
    public static final BlockDefinition<AEDoubleSlabBlock> SKY_STONE_BRICK_SLAB_DOUBLE = SKY_STONE_BRICK_SLABS.dbl;
    private static final SlabDefinitions SKY_STONE_SMALL_BRICK_SLABS = createSlab("Sky Stone Small Brick Slab",
        AEBlockIds.SKY_STONE_SMALL_BRICK_SLAB, MapColor.BLACK, 50.0F, 150.0F);
    public static final BlockDefinition<AEHalfSlabBlock> SKY_STONE_SMALL_BRICK_SLAB = SKY_STONE_SMALL_BRICK_SLABS.half;
    public static final BlockDefinition<AEDoubleSlabBlock> SKY_STONE_SMALL_BRICK_SLAB_DOUBLE = SKY_STONE_SMALL_BRICK_SLABS.dbl;
    private static final SlabDefinitions FLUIX_SLABS = createSlab("Fluix Slab", AEBlockIds.FLUIX_SLAB,
        MapColor.PURPLE, 1.5F, 6.0F);
    public static final BlockDefinition<AEHalfSlabBlock> FLUIX_SLAB = FLUIX_SLABS.half;
    public static final BlockDefinition<AEDoubleSlabBlock> FLUIX_SLAB_DOUBLE = FLUIX_SLABS.dbl;
    private static final SlabDefinitions QUARTZ_SLABS = createSlab("Certus Quartz Slab", AEBlockIds.QUARTZ_SLAB,
        MapColor.QUARTZ, 1.5F, 6.0F);
    public static final BlockDefinition<AEHalfSlabBlock> QUARTZ_SLAB = QUARTZ_SLABS.half;
    public static final BlockDefinition<AEDoubleSlabBlock> QUARTZ_SLAB_DOUBLE = QUARTZ_SLABS.dbl;
    private static final SlabDefinitions CUT_QUARTZ_SLABS = createSlab("Cut Certus Quartz Slab",
        AEBlockIds.CUT_QUARTZ_SLAB, MapColor.QUARTZ, 1.5F, 6.0F);
    public static final BlockDefinition<AEHalfSlabBlock> CUT_QUARTZ_SLAB = CUT_QUARTZ_SLABS.half;
    public static final BlockDefinition<AEDoubleSlabBlock> CUT_QUARTZ_SLAB_DOUBLE = CUT_QUARTZ_SLABS.dbl;
    private static final SlabDefinitions SMOOTH_QUARTZ_SLABS = createSlab("Smooth Certus Quartz Slab",
        AEBlockIds.SMOOTH_QUARTZ_SLAB, MapColor.QUARTZ, 1.5F, 6.0F);
    public static final BlockDefinition<AEHalfSlabBlock> SMOOTH_QUARTZ_SLAB = SMOOTH_QUARTZ_SLABS.half;
    public static final BlockDefinition<AEDoubleSlabBlock> SMOOTH_QUARTZ_SLAB_DOUBLE = SMOOTH_QUARTZ_SLABS.dbl;
    private static final SlabDefinitions QUARTZ_BRICK_SLABS = createSlab("Certus Quartz Brick Slab",
        AEBlockIds.QUARTZ_BRICK_SLAB, MapColor.QUARTZ, 1.5F, 6.0F);
    public static final BlockDefinition<AEHalfSlabBlock> QUARTZ_BRICK_SLAB = QUARTZ_BRICK_SLABS.half;
    public static final BlockDefinition<AEDoubleSlabBlock> QUARTZ_BRICK_SLAB_DOUBLE = QUARTZ_BRICK_SLABS.dbl;
    private static final SlabDefinitions CHISELED_QUARTZ_SLABS = createSlab("Chiseled Certus Quartz Slab",
        AEBlockIds.CHISELED_QUARTZ_SLAB, MapColor.QUARTZ, 1.5F, 6.0F);
    public static final BlockDefinition<AEHalfSlabBlock> CHISELED_QUARTZ_SLAB = CHISELED_QUARTZ_SLABS.half;
    public static final BlockDefinition<AEDoubleSlabBlock> CHISELED_QUARTZ_SLAB_DOUBLE = CHISELED_QUARTZ_SLABS.dbl;
    private static final SlabDefinitions QUARTZ_PILLAR_SLABS = createSlab("Certus Quartz Pillar Slab",
        AEBlockIds.QUARTZ_PILLAR_SLAB, MapColor.QUARTZ, 1.5F, 6.0F);
    public static final BlockDefinition<AEHalfSlabBlock> QUARTZ_PILLAR_SLAB = QUARTZ_PILLAR_SLABS.half;
    public static final BlockDefinition<AEDoubleSlabBlock> QUARTZ_PILLAR_SLAB_DOUBLE = QUARTZ_PILLAR_SLABS.dbl;
    private static final BlockDefinition<?>[] ALL = {
        CABLE_BUS,
        CONTROLLER,
        ENERGY_CELL,
        DENSE_ENERGY_CELL,
        CREATIVE_ENERGY_CELL,
        ENERGY_ACCEPTOR,
        WIRELESS_ACCESS_POINT,
        CRYSTAL_RESONANCE_GENERATOR,
        ME_CHEST,
        DRIVE,
        CELL_WORKBENCH,
        CONDENSER,
        QUARTZ_BLOCK,
        CUT_QUARTZ_BLOCK,
        SMOOTH_QUARTZ_BLOCK,
        QUARTZ_BRICKS,
        QUARTZ_PILLAR,
        CHISELED_QUARTZ_BLOCK,
        FLUIX_BLOCK,
        ENTRO_BLOCK,
        MACHINE_FRAME,
        QUARTZ_GLASS,
        QUARTZ_VIBRANT_GLASS,
        QUARTZ_FIXTURE,
        LIGHT_DETECTOR,
        FLAWLESS_BUDDING_QUARTZ,
        FLAWED_BUDDING_QUARTZ,
        CHIPPED_BUDDING_QUARTZ,
        DAMAGED_BUDDING_QUARTZ,
        SMALL_QUARTZ_BUD,
        MEDIUM_QUARTZ_BUD,
        LARGE_QUARTZ_BUD,
        QUARTZ_CLUSTER,
        ENTRO_BUDDING_FULLY,
        ENTRO_BUDDING_MOSTLY,
        ENTRO_BUDDING_HALF,
        ENTRO_BUDDING_HARDLY,
        ENTRO_CLUSTER_SMALL,
        ENTRO_CLUSTER_MEDIUM,
        ENTRO_CLUSTER_LARGE,
        ENTRO_CLUSTER,
        SKY_STONE_BLOCK,
        SMOOTH_SKY_STONE_BLOCK,
        SKY_STONE_BRICK,
        SKY_STONE_SMALL_BRICK,
        SKY_STONE_STAIRS,
        SMOOTH_SKY_STONE_STAIRS,
        SKY_STONE_BRICK_STAIRS,
        SKY_STONE_SMALL_BRICK_STAIRS,
        FLUIX_STAIRS,
        QUARTZ_STAIRS,
        CUT_QUARTZ_STAIRS,
        SMOOTH_QUARTZ_STAIRS,
        QUARTZ_BRICK_STAIRS,
        CHISELED_QUARTZ_STAIRS,
        QUARTZ_PILLAR_STAIRS,
        SKY_STONE_WALL,
        SMOOTH_SKY_STONE_WALL,
        SKY_STONE_BRICK_WALL,
        SKY_STONE_SMALL_BRICK_WALL,
        FLUIX_WALL,
        QUARTZ_WALL,
        CUT_QUARTZ_WALL,
        SMOOTH_QUARTZ_WALL,
        QUARTZ_BRICK_WALL,
        CHISELED_QUARTZ_WALL,
        QUARTZ_PILLAR_WALL,
        SKY_STONE_SLAB,
        SKY_STONE_SLAB_DOUBLE,
        SMOOTH_SKY_STONE_SLAB,
        SMOOTH_SKY_STONE_SLAB_DOUBLE,
        SKY_STONE_BRICK_SLAB,
        SKY_STONE_BRICK_SLAB_DOUBLE,
        SKY_STONE_SMALL_BRICK_SLAB,
        SKY_STONE_SMALL_BRICK_SLAB_DOUBLE,
        FLUIX_SLAB,
        FLUIX_SLAB_DOUBLE,
        QUARTZ_SLAB,
        QUARTZ_SLAB_DOUBLE,
        CUT_QUARTZ_SLAB,
        CUT_QUARTZ_SLAB_DOUBLE,
        SMOOTH_QUARTZ_SLAB,
        SMOOTH_QUARTZ_SLAB_DOUBLE,
        QUARTZ_BRICK_SLAB,
        QUARTZ_BRICK_SLAB_DOUBLE,
        CHISELED_QUARTZ_SLAB,
        CHISELED_QUARTZ_SLAB_DOUBLE,
        QUARTZ_PILLAR_SLAB,
        QUARTZ_PILLAR_SLAB_DOUBLE,
        MYSTERIOUS_CUBE,
        NOT_SO_MYSTERIOUS_CUBE,
        SKY_STONE_CHEST,
        SMOOTH_SKY_STONE_CHEST,
        SKY_STONE_TANK,
        CHARGER,
        CRANK,
        VIBRATION_CHAMBER,
        GROWTH_ACCELERATOR,
        INSCRIBER,
        CRYSTAL_ASSEMBLER,
        CRYSTAL_FIXER,
        INGREDIENT_BUFFER,
        CANER,
        PATTERN_PROVIDER,
        REQUESTER,
        CRAFTING_UNIT,
        CRAFTING_ACCELERATOR,
        CRAFTING_ACCELERATOR_4X,
        CRAFTING_STORAGE_1K,
        CRAFTING_STORAGE_4K,
        CRAFTING_STORAGE_16K,
        CRAFTING_STORAGE_64K,
        CRAFTING_STORAGE_256K,
        CRAFTING_MONITOR,
        MOLECULAR_ASSEMBLER,
        INTERFACE,
        IO_PORT,
        QUANTUM_RING,
        QUANTUM_LINK,
        SPATIAL_PYLON,
        SPATIAL_IO_PORT,
        SPATIAL_ANCHOR,
        TINY_TNT,
        MATRIX_FRAME,
        PAINT,
        DEBUG_ENERGY_GEN,
        DEBUG_ITEM_GEN,
        DEBUG_CUBE_GEN,
        DEBUG_PHANTOM_NODE
    };

    private AEBlocks() {
    }

    private static SlabDefinitions createSlab(String englishName, ResourceLocation id,
                                              MapColor mapColor, float hardness, float resistance) {
        return new SlabDefinitions(englishName, id, mapColor, hardness, resistance);
    }

    public static BlockDefinition<?>[] all() {
        return ALL.clone();
    }

    public static void register(RegistryEvent.Register<Block> event) {
        for (BlockDefinition<?> definition : ALL) {
            event.getRegistry().register(definition.block());
        }
    }

    private static final class SlabDefinitions {
        private final BlockDefinition<AEHalfSlabBlock> half;
        private final BlockDefinition<AEDoubleSlabBlock> dbl;

        private SlabDefinitions(String englishName, ResourceLocation id,
                                MapColor mapColor, float hardness, float resistance) {
            AEHalfSlabBlock halfBlock = new AEHalfSlabBlock(Material.ROCK, mapColor, null, hardness, resistance);
            AEDoubleSlabBlock doubleBlock = new AEDoubleSlabBlock(Material.ROCK, mapColor, null, hardness, resistance);
            halfBlock.setItemBlock(halfBlock);
            doubleBlock.setItemBlock(halfBlock);
            this.dbl = new BlockDefinition<>(AppEng.makeId(id.getPath() + "_double"), doubleBlock, ignored -> null);
            this.half = new BlockDefinition<>(englishName, id, halfBlock,
                block -> new AESlabItemBlock(block, halfBlock, doubleBlock));
        }
    }
}
