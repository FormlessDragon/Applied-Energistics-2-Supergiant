package ae2.core.gui;

import ae2.api.cellterminal.CellTerminalContainerHost;
import ae2.api.implementations.guiobjects.IGuiItem;
import ae2.api.implementations.guiobjects.IPortableTerminal;
import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.api.implementations.items.WirelessTerminalDefinition;
import ae2.api.storage.ITerminalHost;
import ae2.api.util.ICustomName;
import ae2.client.gui.implementations.GuiAdvancedIOBus;
import ae2.client.gui.implementations.GuiAdvancedMemoryCard;
import ae2.client.gui.implementations.GuiAnnihilationPlane;
import ae2.client.gui.implementations.GuiCaner;
import ae2.client.gui.implementations.GuiCellRestriction;
import ae2.client.gui.implementations.GuiCellTerminal;
import ae2.client.gui.implementations.GuiCellWorkbench;
import ae2.client.gui.implementations.GuiCondenser;
import ae2.client.gui.implementations.GuiConfigModifier;
import ae2.client.gui.implementations.GuiCrystalAssembler;
import ae2.client.gui.implementations.GuiDrive;
import ae2.client.gui.implementations.GuiEnergyLevelEmitter;
import ae2.client.gui.implementations.GuiFormationPlane;
import ae2.client.gui.implementations.GuiIOBus;
import ae2.client.gui.implementations.GuiIOPort;
import ae2.client.gui.implementations.GuiImportExportBus;
import ae2.client.gui.implementations.GuiIngredientBuffer;
import ae2.client.gui.implementations.GuiInscriber;
import ae2.client.gui.implementations.GuiInterface;
import ae2.client.gui.implementations.GuiMEChest;
import ae2.client.gui.implementations.GuiModFilterBus;
import ae2.client.gui.implementations.GuiModStorageBus;
import ae2.client.gui.implementations.GuiMolecularAssembler;
import ae2.client.gui.implementations.GuiNetworkAnalyser;
import ae2.client.gui.implementations.GuiODFilterBus;
import ae2.client.gui.implementations.GuiODStorageBus;
import ae2.client.gui.implementations.GuiPatternModifier;
import ae2.client.gui.implementations.GuiPatternProvider;
import ae2.client.gui.implementations.GuiPortableCellPickupFilter;
import ae2.client.gui.implementations.GuiPortableVoidCell;
import ae2.client.gui.implementations.GuiPreciseStorageBus;
import ae2.client.gui.implementations.GuiQNB;
import ae2.client.gui.implementations.GuiQuartzKnife;
import ae2.client.gui.implementations.GuiRenamer;
import ae2.client.gui.implementations.GuiRequester;
import ae2.client.gui.implementations.GuiSkyChest;
import ae2.client.gui.implementations.GuiSpatialAnchor;
import ae2.client.gui.implementations.GuiSpatialIOPort;
import ae2.client.gui.implementations.GuiSpecialPreciseExportBus;
import ae2.client.gui.implementations.GuiStockExportBus;
import ae2.client.gui.implementations.GuiStorageBus;
import ae2.client.gui.implementations.GuiStorageLevelEmitter;
import ae2.client.gui.implementations.GuiThresholdExportBus;
import ae2.client.gui.implementations.GuiThresholdLevelEmitter;
import ae2.client.gui.implementations.GuiTickAnalyser;
import ae2.client.gui.implementations.GuiVibrationChamber;
import ae2.client.gui.implementations.GuiVoidCell;
import ae2.client.gui.implementations.GuiWirelessAccessPoint;
import ae2.client.gui.me.common.GuiMEStorage;
import ae2.client.gui.me.crafting.GuiCraftingCPU;
import ae2.client.gui.me.crafting.GuiCraftingTree;
import ae2.client.gui.me.items.GuiCraftingTerm;
import ae2.client.gui.me.items.GuiPatternEncodingTerm;
import ae2.client.gui.me.networktool.GuiNetworkStatus;
import ae2.client.gui.me.networktool.GuiNetworkTool;
import ae2.client.gui.me.patternaccess.GuiPatternAccessTerm;
import ae2.client.gui.me.requester.GuiRequesterTerm;
import ae2.client.gui.networking.GuiControllerStatus;
import ae2.client.gui.style.GuiStyleManager;
import ae2.container.AEBaseContainer;
import ae2.container.GuiIds;
import ae2.container.implementations.ContainerAdvancedIOBus;
import ae2.container.implementations.ContainerAdvancedMemoryCard;
import ae2.container.implementations.ContainerAnnihilationPlane;
import ae2.container.implementations.ContainerCaner;
import ae2.container.implementations.ContainerCellRestriction;
import ae2.container.implementations.ContainerCellTerminal;
import ae2.container.implementations.ContainerCellWorkbench;
import ae2.container.implementations.ContainerCondenser;
import ae2.container.implementations.ContainerConfigModifier;
import ae2.container.implementations.ContainerCraftConfirm;
import ae2.container.implementations.ContainerCraftingCPU;
import ae2.container.implementations.ContainerCraftingTree;
import ae2.container.implementations.ContainerCrystalAssembler;
import ae2.container.implementations.ContainerDrive;
import ae2.container.implementations.ContainerEnergyLevelEmitter;
import ae2.container.implementations.ContainerFormationPlane;
import ae2.container.implementations.ContainerIOBus;
import ae2.container.implementations.ContainerIOPort;
import ae2.container.implementations.ContainerIngredientBuffer;
import ae2.container.implementations.ContainerInscriber;
import ae2.container.implementations.ContainerInterface;
import ae2.container.implementations.ContainerMEChest;
import ae2.container.implementations.ContainerModFilterBus;
import ae2.container.implementations.ContainerModStorageBus;
import ae2.container.implementations.ContainerMolecularAssembler;
import ae2.container.implementations.ContainerNetworkAnalyser;
import ae2.container.implementations.ContainerNetworkStatus;
import ae2.container.implementations.ContainerNetworkTool;
import ae2.container.implementations.ContainerODFilterBus;
import ae2.container.implementations.ContainerODStorageBus;
import ae2.container.implementations.ContainerPatternAccessTerm;
import ae2.container.implementations.ContainerPatternModifier;
import ae2.container.implementations.ContainerPatternProvider;
import ae2.container.implementations.ContainerPortableCellPickupFilter;
import ae2.container.implementations.ContainerPortableVoidCell;
import ae2.container.implementations.ContainerQNB;
import ae2.container.implementations.ContainerQuartzKnife;
import ae2.container.implementations.ContainerRenamer;
import ae2.container.implementations.ContainerRequester;
import ae2.container.implementations.ContainerRequesterTerm;
import ae2.container.implementations.ContainerSkyChest;
import ae2.container.implementations.ContainerSpatialAnchor;
import ae2.container.implementations.ContainerSpatialIOPort;
import ae2.container.implementations.ContainerStockExportBus;
import ae2.container.implementations.ContainerStorageBus;
import ae2.container.implementations.ContainerStorageLevelEmitter;
import ae2.container.implementations.ContainerThresholdExportBus;
import ae2.container.implementations.ContainerThresholdLevelEmitter;
import ae2.container.implementations.ContainerTickAnalyser;
import ae2.container.implementations.ContainerVibrationChamber;
import ae2.container.implementations.ContainerVoidCell;
import ae2.container.implementations.ContainerWirelessAccessPoint;
import ae2.container.me.common.ContainerMEStorage;
import ae2.container.me.items.ContainerBasicCellChest;
import ae2.container.me.items.ContainerCraftingTerm;
import ae2.container.me.items.ContainerPatternEncodingTerm;
import ae2.container.me.items.ContainerWirelessCraftingTerm;
import ae2.container.networking.ContainerControllerStatus;
import ae2.core.gui.locator.GuiHostLocator;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.core.gui.locator.PartLocator;
import ae2.helpers.ICellWorkbenchHost;
import ae2.helpers.WirelessCraftingTerminalGuiHost;
import ae2.helpers.WirelessPatternAccessTerminalGuiHost;
import ae2.helpers.WirelessPatternEncodingTerminalGuiHost;
import ae2.helpers.WirelessRequesterTerminalGuiHost;
import ae2.helpers.WirelessTerminalGuiHost;
import ae2.items.contents.AdvancedMemoryCardGuiHost;
import ae2.items.contents.ConfigModifierGuiHost;
import ae2.items.contents.NetworkToolGuiHost;
import ae2.items.contents.PatternModifierGuiHost;
import ae2.items.contents.PortableVoidCellGuiHost;
import ae2.items.contents.VoidCellGuiHost;
import ae2.items.tools.powered.PortableItemCellAutoPickup;
import ae2.items.tools.powered.WirelessTerminalRegistry;
import ae2.items.tools.powered.WirelessUniversalTerminalItem;
import ae2.parts.AEBasePart;
import ae2.parts.automation.AdvancedIOBusPart;
import ae2.parts.automation.AnnihilationPlanePart;
import ae2.parts.automation.EnergyLevelEmitterPart;
import ae2.parts.automation.FormationPlanePart;
import ae2.parts.automation.IOBusPart;
import ae2.parts.automation.ImportBusPart;
import ae2.parts.automation.ImportExportBusPart;
import ae2.parts.automation.StockExportBusPart;
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
import ae2.parts.misc.InterfacePart;
import ae2.parts.reporting.CraftingTerminalPart;
import ae2.parts.reporting.ItemTerminalPart;
import ae2.parts.reporting.PatternAccessTerminalPart;
import ae2.parts.reporting.RequesterTerminalPart;
import ae2.parts.storagebus.StorageBusPart;
import ae2.tile.AEBaseTile;
import ae2.tile.crafting.ICraftingCPUTileEntity;
import ae2.tile.crafting.TileMolecularAssembler;
import ae2.tile.crafting.TilePatternProvider;
import ae2.tile.crafting.TileRequester;
import ae2.tile.misc.TileCaner;
import ae2.tile.misc.TileCellWorkbench;
import ae2.tile.misc.TileCondenser;
import ae2.tile.misc.TileCrystalAssembler;
import ae2.tile.misc.TileIngredientBuffer;
import ae2.tile.misc.TileInscriber;
import ae2.tile.misc.TileInterface;
import ae2.tile.misc.TileVibrationChamber;
import ae2.tile.networking.TileController;
import ae2.tile.networking.TileWirelessAccessPoint;
import ae2.tile.qnb.TileQuantumBridge;
import ae2.tile.spatial.TileSpatialAnchor;
import ae2.tile.spatial.TileSpatialIOPort;
import ae2.tile.storage.TileDrive;
import ae2.tile.storage.TileIOPort;
import ae2.tile.storage.TileMEChest;
import ae2.tile.storage.TileSkyChest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IWorldNameable;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class AEGuiHandler implements IGuiHandler {

    private static boolean isItemGui(GuiIds.GuiKey bridge) {
        return bridge == GuiIds.GuiKey.QUARTZ_KNIFE
            || bridge == GuiIds.GuiKey.NETWORK_TOOL
            || bridge == GuiIds.GuiKey.NETWORK_ANALYSER
            || bridge == GuiIds.GuiKey.TICK_ANALYSER
            || bridge == GuiIds.GuiKey.CONFIG_MODIFIER
            || bridge == GuiIds.GuiKey.PATTERN_MODIFIER
            || bridge == GuiIds.GuiKey.ADVANCED_MEMORY_CARD
            || bridge == GuiIds.GuiKey.NETWORK_STATUS
            || bridge == GuiIds.GuiKey.PORTABLE_ITEM_CELL
            || bridge == GuiIds.GuiKey.PORTABLE_CELL_PICKUP_FILTER
            || bridge == GuiIds.GuiKey.PORTABLE_CELL_WORKBENCH
            || bridge == GuiIds.GuiKey.VOID_CELL
            || bridge == GuiIds.GuiKey.PORTABLE_VOID_CELL
            || bridge == GuiIds.GuiKey.PORTABLE_FLUID_CELL
            || bridge == GuiIds.GuiKey.WIRELESS_TERMINAL
            || bridge == GuiIds.GuiKey.WIRELESS_CELL_TERMINAL
            || bridge == GuiIds.GuiKey.WIRELESS_CRAFTING_TERMINAL
            || bridge == GuiIds.GuiKey.WIRELESS_PATTERN_ENCODING_TERMINAL
            || bridge == GuiIds.GuiKey.WIRELESS_PATTERN_ACCESS_TERMINAL
            || bridge == GuiIds.GuiKey.WIRELESS_REQUESTER_TERMINAL
            || bridge == GuiIds.GuiKey.WIRELESS_TERMINAL_DYNAMIC;
    }

    private static boolean isPartGui(GuiIds.GuiKey bridge) {
        return bridge == GuiIds.GuiKey.IMPORT_BUS
            || bridge == GuiIds.GuiKey.EXPORT_BUS
            || bridge == GuiIds.GuiKey.STORAGE_BUS
            || bridge == GuiIds.GuiKey.OD_EXPORT_BUS
            || bridge == GuiIds.GuiKey.MOD_EXPORT_BUS
            || bridge == GuiIds.GuiKey.PRECISE_EXPORT_BUS
            || bridge == GuiIds.GuiKey.THRESHOLD_EXPORT_BUS
            || bridge == GuiIds.GuiKey.STOCK_EXPORT_BUS
            || bridge == GuiIds.GuiKey.IMPORT_EXPORT_BUS
            || bridge == GuiIds.GuiKey.ADVANCED_IO_BUS
            || bridge == GuiIds.GuiKey.OD_STORAGE_BUS
            || bridge == GuiIds.GuiKey.MOD_STORAGE_BUS
            || bridge == GuiIds.GuiKey.PRECISE_STORAGE_BUS
            || bridge == GuiIds.GuiKey.FORMATION_PLANE
            || bridge == GuiIds.GuiKey.ANNIHILATION_PLANE
            || bridge == GuiIds.GuiKey.ENERGY_LEVEL_EMITTER
            || bridge == GuiIds.GuiKey.STORAGE_LEVEL_EMITTER
            || bridge == GuiIds.GuiKey.THRESHOLD_LEVEL_EMITTER
            || bridge == GuiIds.GuiKey.ME_STORAGE_TERMINAL
            || bridge == GuiIds.GuiKey.CELL_TERMINAL
            || bridge == GuiIds.GuiKey.CRAFTING_TERMINAL
            || bridge == GuiIds.GuiKey.PATTERN_ENCODING_TERMINAL
            || bridge == GuiIds.GuiKey.PATTERN_ACCESS_TERMINAL
            || bridge == GuiIds.GuiKey.REQUESTER_TERMINAL;
    }

    private static @Nullable PartLocator partLocator(int x, int y, int z) {
        int side = y >> 8;
        if (side == EnumFacing.VALUES.length) {
            return new PartLocator(new BlockPos(x, y & 255, z), null);
        }
        if (side < 0 || side > EnumFacing.VALUES.length) {
            return null;
        }
        return new PartLocator(new BlockPos(x, y & 255, z), EnumFacing.VALUES[side]);
    }

    private static <H, C extends AEBaseContainer> @Nullable C createPartContainer(EntityPlayer player, GuiHostLocator locator,
                                                                                  int guiId, Class<H> hostType, Function<H, C> factory) {
        if (locator == null) {
            return null;
        }
        H host = locator.locate(player, hostType);
        if (host == null) {
            return null;
        }
        return initContainer(factory.apply(host), locator, guiId);
    }

    private static <C extends AEBaseContainer> C initTileContainer(C container, TileEntity te, int guiId) {
        return initContainer(container, GuiHostLocators.forTile(te), guiId);
    }

    private static <C extends AEBaseContainer> C initContainer(C container, GuiHostLocator locator, int guiId) {
        container.setLocator(locator);
        container.setReturnedFromSubScreen(GuiIds.isReturnedFromSubScreen(guiId));
        container.setGuiTitle(getDefaultGuiTitle(container.getTarget()));
        return PatternContainerGuiReturnContext.initializeContainer(container);
    }

    private static @Nullable ITextComponent getDefaultGuiTitle(Object host) {
        if (host instanceof IWorldNameable nameable) {
            if (nameable.hasCustomName()) {
                return nameable.getDisplayName();
            }
        }
        if (host instanceof AEBaseTile tile) {
            if (tile.hasCustomName()) {
                return customTitle(tile.getCustomName());
            }
        }
        if (host instanceof AEBasePart part) {
            if (part.hasCustomName()) {
                return customTitle(part.getCustomName());
            }
        }
        return null;
    }

    private static @Nullable ITextComponent customTitle(@Nullable String customName) {
        return customName == null || customName.isEmpty() ? null : new TextComponentString(customName);
    }

    private static @Nullable RayTraceResult unpackItemUseHitResult(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y & 255, z);
        int sideIndex = (y >> 16) & 0x7;
        if (sideIndex >= EnumFacing.VALUES.length) {
            return null;
        }
        int hitX = (y >> 19) & 0xF;
        int hitY = (y >> 23) & 0xF;
        int hitZ = (y >> 27) & 0xF;
        return GuiHostLocators.createItemUseHitResult(pos, EnumFacing.VALUES[sideIndex],
            hitX / 15.0f, hitY / 15.0f, hitZ / 15.0f);
    }

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (player == null || world == null) {
            return null;
        }
        GuiIds.GuiKey bridge = GuiIds.GuiKey.fromId(ID);
        if (bridge == null) {
            return null;
        }
        TileEntity te = isItemGui(bridge) || isPartGui(bridge) ? null : world.getTileEntity(new BlockPos(x, y, z));

        switch (bridge) {
            case CONTROLLER_STATUS -> {
                if (te instanceof TileController) {
                    return initTileContainer(new ContainerControllerStatus(player.inventory, (TileController) te),
                        te, ID);
                }
            }
            case ME_CHEST -> {
                if (te instanceof TileMEChest) {
                    return initTileContainer(new ContainerMEChest(player.inventory, (TileMEChest) te),
                        te, ID);
                }
            }
            case BASIC_CELL_CHEST -> {
                if (te instanceof TileMEChest) {
                    return initTileContainer(new ContainerBasicCellChest(player.inventory, (TileMEChest) te),
                        te, ID);
                }
            }
            case DRIVE -> {
                if (te instanceof TileDrive) {
                    return initTileContainer(new ContainerDrive(player.inventory, (TileDrive) te), te, ID);
                }
            }
            case CELL_WORKBENCH -> {
                if (te instanceof TileCellWorkbench) {
                    return initTileContainer(new ContainerCellWorkbench(player.inventory, (TileCellWorkbench) te), te,
                        ID);
                }
            }
            case CELL_RESTRICTION -> {
                if (te instanceof TileCellWorkbench) {
                    return initTileContainer(new ContainerCellRestriction(player.inventory, (TileCellWorkbench) te), te,
                        ID);
                }
            }
            case CONDENSER -> {
                if (te instanceof TileCondenser) {
                    return initTileContainer(new ContainerCondenser(player.inventory, (TileCondenser) te), te, ID);
                }
            }
            case SKY_CHEST -> {
                if (te instanceof TileSkyChest) {
                    return initTileContainer(new ContainerSkyChest(player.inventory, (TileSkyChest) te), te, ID);
                }
            }
            case INSCRIBER -> {
                if (te instanceof TileInscriber) {
                    return initTileContainer(new ContainerInscriber(player.inventory, (TileInscriber) te), te, ID);
                }
            }
            case CRYSTAL_ASSEMBLER -> {
                if (te instanceof TileCrystalAssembler) {
                    return initTileContainer(new ContainerCrystalAssembler(player.inventory,
                        (TileCrystalAssembler) te), te, ID);
                }
            }
            case INGREDIENT_BUFFER -> {
                if (te instanceof TileIngredientBuffer) {
                    return initTileContainer(new ContainerIngredientBuffer(player.inventory,
                        (TileIngredientBuffer) te), te, ID);
                }
            }
            case CANER -> {
                if (te instanceof TileCaner) {
                    return initTileContainer(new ContainerCaner(player.inventory, (TileCaner) te), te, ID);
                }
            }
            case IO_PORT -> {
                if (te instanceof TileIOPort) {
                    return initTileContainer(new ContainerIOPort(player.inventory, (TileIOPort) te), te, ID);
                }
            }
            case MOLECULAR_ASSEMBLER -> {
                if (te instanceof TileMolecularAssembler) {
                    return initTileContainer(
                        new ContainerMolecularAssembler(player.inventory, (TileMolecularAssembler) te), te, ID);
                }
            }
            case VIBRATION_CHAMBER -> {
                if (te instanceof TileVibrationChamber) {
                    return initTileContainer(
                        new ContainerVibrationChamber(player.inventory, (TileVibrationChamber) te), te, ID);
                }
            }
            case QNB -> {
                if (te instanceof TileQuantumBridge) {
                    return initTileContainer(new ContainerQNB(player.inventory, (TileQuantumBridge) te), te, ID);
                }
            }
            case WIRELESS_ACCESS_POINT -> {
                if (te instanceof TileWirelessAccessPoint) {
                    return initTileContainer(new ContainerWirelessAccessPoint(player.inventory,
                        (TileWirelessAccessPoint) te), te, ID);
                }
            }
            case SPATIAL_IO_PORT -> {
                if (te instanceof TileSpatialIOPort) {
                    return initTileContainer(new ContainerSpatialIOPort(player.inventory, (TileSpatialIOPort) te), te,
                        ID);
                }
            }
            case SPATIAL_ANCHOR -> {
                if (te instanceof TileSpatialAnchor) {
                    return initTileContainer(new ContainerSpatialAnchor(player.inventory, (TileSpatialAnchor) te), te,
                        ID);
                }
            }
            case INTERFACE -> {
                if (te instanceof TileInterface) {
                    return initTileContainer(new ContainerInterface(player.inventory, (TileInterface) te),
                        te, ID);
                }
                return createPartContainer(player, partLocator(x, y, z), ID, InterfacePart.class,
                    host -> new ContainerInterface(player.inventory, host));
            }
            case PATTERN_PROVIDER -> {
                if (te instanceof TilePatternProvider) {
                    return initTileContainer(new ContainerPatternProvider(player.inventory, (TilePatternProvider) te),
                        te, ID);
                }
                return createPartContainer(player, partLocator(x, y, z), ID, PatternProviderPart.class,
                    host -> new ContainerPatternProvider(player.inventory, host));
            }
            case REQUESTER -> {
                if (te instanceof TileRequester requester) {
                    return initTileContainer(new ContainerRequester(player.inventory, requester), te, ID);
                }
            }
            case CRAFTING_CPU -> {
                if (te instanceof ICraftingCPUTileEntity craftingCpu) {
                    return initTileContainer(new ContainerCraftingCPU(player.inventory, craftingCpu),
                        te, ID);
                }
            }
            case CRAFTING_TREE -> {
                if (player.openContainer instanceof ContainerCraftConfirm confirm
                    && confirm.getTarget() instanceof ITerminalHost terminalHost) {
                    ContainerCraftingTree container = new ContainerCraftingTree(player.inventory, terminalHost);
                    container.setLocator(confirm.getLocator());
                    return container;
                }
            }
            case RENAMER -> {
                ContainerRenamer renamer = createPartContainer(player, partLocator(x, y, z), ID, ICustomName.class,
                    host -> new ContainerRenamer(player.inventory, host));
                if (renamer != null) {
                    return renamer;
                }
                if (te instanceof ICustomName customName) {
                    return initTileContainer(new ContainerRenamer(player.inventory, customName), te, ID);
                }
            }
            case IMPORT_BUS -> {
                return createPartContainer(player, partLocator(x, y, z), ID, ImportBusPart.class,
                    host -> new ContainerIOBus(player.inventory, host));
            }
            case EXPORT_BUS -> {
                return createPartContainer(player, partLocator(x, y, z), ID, IOBusPart.class,
                    host -> new ContainerIOBus(player.inventory, host));
            }
            case STORAGE_BUS -> {
                return createPartContainer(player, partLocator(x, y, z), ID, StorageBusPart.class,
                    host -> new ContainerStorageBus(player.inventory, host));
            }
            case OD_EXPORT_BUS -> {
                return createPartContainer(player, partLocator(x, y, z), ID, ODExportBusPart.class,
                    host -> new ContainerODFilterBus<>(player.inventory, host));
            }
            case MOD_EXPORT_BUS -> {
                return createPartContainer(player, partLocator(x, y, z), ID, ModExportBusPart.class,
                    host -> new ContainerModFilterBus<>(player.inventory, host));
            }
            case PRECISE_EXPORT_BUS -> {
                return createPartContainer(player, partLocator(x, y, z), ID, PreciseExportBusPart.class,
                    host -> new ContainerIOBus(player.inventory, host));
            }
            case THRESHOLD_EXPORT_BUS -> {
                return createPartContainer(player, partLocator(x, y, z), ID, ThresholdExportBusPart.class,
                    host -> new ContainerThresholdExportBus(player.inventory, host));
            }
            case STOCK_EXPORT_BUS -> {
                return createPartContainer(player, partLocator(x, y, z), ID, StockExportBusPart.class,
                    host -> new ContainerStockExportBus(player.inventory, host));
            }
            case IMPORT_EXPORT_BUS -> {
                return createPartContainer(player, partLocator(x, y, z), ID, ImportExportBusPart.class,
                    host -> new ContainerIOBus(player.inventory, host));
            }
            case ADVANCED_IO_BUS -> {
                return createPartContainer(player, partLocator(x, y, z), ID, AdvancedIOBusPart.class,
                    host -> new ContainerAdvancedIOBus(player.inventory, host));
            }
            case OD_STORAGE_BUS -> {
                return createPartContainer(player, partLocator(x, y, z), ID, ODStorageBusPart.class,
                    host -> new ContainerODStorageBus(player.inventory, host));
            }
            case MOD_STORAGE_BUS -> {
                return createPartContainer(player, partLocator(x, y, z), ID, ModStorageBusPart.class,
                    host -> new ContainerModStorageBus(player.inventory, host));
            }
            case PRECISE_STORAGE_BUS -> {
                return createPartContainer(player, partLocator(x, y, z), ID, PreciseStorageBusPart.class,
                    host -> new ContainerStorageBus(player.inventory, host));
            }
            case FORMATION_PLANE -> {
                return createPartContainer(player, partLocator(x, y, z), ID, FormationPlanePart.class,
                    host -> new ContainerFormationPlane(player.inventory, host));
            }
            case ANNIHILATION_PLANE -> {
                return createPartContainer(player, partLocator(x, y, z), ID, AnnihilationPlanePart.class,
                    host -> new ContainerAnnihilationPlane(player.inventory, host));
            }
            case ENERGY_LEVEL_EMITTER -> {
                return createPartContainer(player, partLocator(x, y, z), ID, EnergyLevelEmitterPart.class,
                    host -> new ContainerEnergyLevelEmitter(player.inventory, host));
            }
            case STORAGE_LEVEL_EMITTER -> {
                return createPartContainer(player, partLocator(x, y, z), ID, StorageLevelEmitterPart.class,
                    host -> new ContainerStorageLevelEmitter(player.inventory, host));
            }
            case THRESHOLD_LEVEL_EMITTER -> {
                return createPartContainer(player, partLocator(x, y, z), ID, ThresholdLevelEmitterPart.class,
                    host -> new ContainerThresholdLevelEmitter(player.inventory, host));
            }
            case ME_STORAGE_TERMINAL -> {
                return createPartContainer(player, partLocator(x, y, z), ID, ItemTerminalPart.class,
                    host -> new ContainerMEStorage(GuiIds.GuiKey.ME_STORAGE_TERMINAL, player.inventory, host));
            }
            case CELL_TERMINAL -> {
                return createPartContainer(player, partLocator(x, y, z), ID, CellTerminalContainerHost.class,
                    host -> new ContainerCellTerminal(player.inventory, host));
            }
            case CRAFTING_TERMINAL -> {
                return createPartContainer(player, partLocator(x, y, z), ID, CraftingTerminalPart.class,
                    host -> new ContainerCraftingTerm(player.inventory, host));
            }
            case PATTERN_ENCODING_TERMINAL -> {
                return createPartContainer(player, partLocator(x, y, z), ID, PatternEncodingTerminalPart.class,
                    host -> new ContainerPatternEncodingTerm(player.inventory, host));
            }
            case PATTERN_ACCESS_TERMINAL -> {
                return createPartContainer(player, partLocator(x, y, z), ID, PatternAccessTerminalPart.class,
                    host -> new ContainerPatternAccessTerm(player.inventory, host));
            }
            case REQUESTER_TERMINAL -> {
                return createPartContainer(player, partLocator(x, y, z), ID, RequesterTerminalPart.class,
                    host -> new ContainerRequesterTerm(player.inventory, host));
            }
            case QUARTZ_KNIFE -> {
                return createQuartzKnifeContainer(player, x, ID);
            }
            case NETWORK_TOOL -> {
                return createNetworkToolContainer(player, x, ID);
            }
            case NETWORK_ANALYSER -> {
                return createNetworkAnalyserContainer(player, x, ID);
            }
            case TICK_ANALYSER -> {
                return createTickAnalyserContainer(player, x, ID);
            }
            case CONFIG_MODIFIER -> {
                return createConfigModifierContainer(player, x, ID);
            }
            case PATTERN_MODIFIER -> {
                return createPatternModifierContainer(player, x, y, z, ID);
            }
            case ADVANCED_MEMORY_CARD -> {
                return createAdvancedMemoryCardContainer(player, x, y, z, ID);
            }
            case NETWORK_STATUS -> {
                return createNetworkStatusContainer(player, y >> 8, new BlockPos(x, y & 255, z), ID);
            }
            case PORTABLE_ITEM_CELL -> {
                return createPortableItemCellContainer(player, x, ID);
            }
            case PORTABLE_CELL_PICKUP_FILTER -> {
                return createPortableCellPickupFilterContainer(player, x, ID);
            }
            case PORTABLE_CELL_WORKBENCH -> {
                return createPortableCellWorkbenchContainer(player, x, ID);
            }
            case PORTABLE_VOID_CELL -> {
                return createPortableVoidCellContainer(player, x, ID);
            }
            case VOID_CELL -> {
                return createVoidCellContainer(player, x, ID);
            }
            case PORTABLE_FLUID_CELL -> {
                return createPortableFluidCellContainer(player, x, ID);
            }
            case WIRELESS_TERMINAL -> {
                return createWirelessTerminalContainer(player, x, ID);
            }
            case WIRELESS_CELL_TERMINAL -> {
                return createWirelessCellTerminalContainer(player, x, ID);
            }
            case WIRELESS_CRAFTING_TERMINAL -> {
                return createWirelessCraftingTerminalContainer(player, x, ID);
            }
            case WIRELESS_PATTERN_ENCODING_TERMINAL -> {
                return createWirelessPatternEncodingTerminalContainer(player, x, ID);
            }
            case WIRELESS_PATTERN_ACCESS_TERMINAL -> {
                return createWirelessPatternAccessTerminalContainer(player, x, ID);
            }
            case WIRELESS_REQUESTER_TERMINAL -> {
                return createWirelessRequesterTerminalContainer(player, x, ID);
            }
            case WIRELESS_TERMINAL_DYNAMIC -> {
                return createDynamicWirelessTerminalContainer(player, x, y, ID);
            }
        }
        return null;
    }

    private @Nullable ContainerQuartzKnife createQuartzKnifeContainer(EntityPlayer player, int slot, int guiId) {
        ItemGuiHostLocator locator = GuiHostLocators.forInventorySlot(slot);
        ItemGuiHost<?> host = createItemGuiHost(player, locator);
        if (host == null) {
            return null;
        }

        return initContainer(new ContainerQuartzKnife(player.inventory, host), locator, guiId);
    }

    private @Nullable ContainerNetworkTool createNetworkToolContainer(EntityPlayer player, int slot, int guiId) {
        ItemGuiHostLocator locator = GuiHostLocators.forInventorySlot(slot);
        NetworkToolGuiHost<?> host = createNetworkToolGuiHost(player, locator);
        if (host == null) {
            return null;
        }

        return initContainer(new ContainerNetworkTool(player.inventory, host), locator, guiId);
    }

    private @Nullable ContainerNetworkAnalyser createNetworkAnalyserContainer(EntityPlayer player, int slot, int guiId) {
        ItemGuiHostLocator locator = GuiHostLocators.forInventorySlot(slot);
        ItemGuiHost<?> host = createItemGuiHost(player, locator, GuiIds.GuiKey.NETWORK_ANALYSER);
        if (host == null) {
            return null;
        }

        return initContainer(new ContainerNetworkAnalyser(player.inventory, host), locator, guiId);
    }

    private @Nullable ContainerTickAnalyser createTickAnalyserContainer(EntityPlayer player, int slot, int guiId) {
        ItemGuiHostLocator locator = GuiHostLocators.forInventorySlot(slot);
        ItemGuiHost<?> host = createItemGuiHost(player, locator, GuiIds.GuiKey.TICK_ANALYSER);
        if (host == null) {
            return null;
        }

        return initContainer(new ContainerTickAnalyser(player.inventory, host), locator, guiId);
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (player == null || world == null) {
            return null;
        }
        GuiIds.GuiKey bridge = GuiIds.GuiKey.fromId(ID);
        if (bridge == null) {
            return null;
        }
        TileEntity te = isItemGui(bridge) || isPartGui(bridge) ? null : world.getTileEntity(new BlockPos(x, y, z));

        switch (bridge) {
            case CONTROLLER_STATUS -> {
                if (te instanceof TileController) {
                    ContainerControllerStatus container = initTileContainer(new ContainerControllerStatus(
                        player.inventory, (TileController) te), te, ID);
                    return new GuiControllerStatus(container, player.inventory);
                }
            }
            case ME_CHEST -> {
                if (te instanceof TileMEChest) {
                    ContainerMEChest container = initTileContainer(new ContainerMEChest(player.inventory,
                        (TileMEChest) te), te, ID);
                    return new GuiMEChest(container, player.inventory);
                }
            }
            case BASIC_CELL_CHEST -> {
                if (te instanceof TileMEChest) {
                    ContainerBasicCellChest container = initTileContainer(new ContainerBasicCellChest(player.inventory,
                        (TileMEChest) te), te, ID);
                    return new GuiMEStorage<>(container, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/terminals/terminal.json"));
                }
            }
            case DRIVE -> {
                if (te instanceof TileDrive) {
                    ContainerDrive container = initTileContainer(new ContainerDrive(player.inventory, (TileDrive) te),
                        te, ID);
                    return new GuiDrive(container, player.inventory, customTitle(((AEBaseTile) te).getCustomName()),
                        GuiStyleManager.loadStyleDoc("/screens/drive.json"));
                }
            }
            case CELL_WORKBENCH -> {
                if (te instanceof TileCellWorkbench) {
                    ContainerCellWorkbench container = initTileContainer(new ContainerCellWorkbench(player.inventory,
                        (TileCellWorkbench) te), te, ID);
                    return new GuiCellWorkbench(container, player.inventory,
                        customTitle(((AEBaseTile) te).getCustomName()),
                        GuiStyleManager.loadStyleDoc("/screens/cell_workbench.json"));
                }
            }
            case CELL_RESTRICTION -> {
                if (te instanceof TileCellWorkbench) {
                    ContainerCellRestriction container = initTileContainer(new ContainerCellRestriction(player.inventory,
                        (TileCellWorkbench) te), te, ID);
                    return new GuiCellRestriction(container, player.inventory,
                        GuiStyleManager.loadStyleDoc("/screens/cell_restriction.json"));
                }
            }
            case CONDENSER -> {
                if (te instanceof TileCondenser) {
                    ContainerCondenser container = initTileContainer(new ContainerCondenser(player.inventory,
                        (TileCondenser) te), te, ID);
                    return new GuiCondenser(container, player.inventory,
                        customTitle(((AEBaseTile) te).getCustomName()),
                        GuiStyleManager.loadStyleDoc("/screens/condenser.json"));
                }
            }
            case SKY_CHEST -> {
                if (te instanceof TileSkyChest) {
                    ContainerSkyChest container = initTileContainer(new ContainerSkyChest(player.inventory,
                        (TileSkyChest) te), te, ID);
                    return new GuiSkyChest(container, player.inventory,
                        customTitle(((AEBaseTile) te).getCustomName()),
                        GuiStyleManager.loadStyleDoc("/screens/sky_chest.json"));
                }
            }
            case INSCRIBER -> {
                if (te instanceof TileInscriber) {
                    ContainerInscriber container = initTileContainer(new ContainerInscriber(player.inventory,
                        (TileInscriber) te), te, ID);
                    return new GuiInscriber(container, player.inventory,
                        customTitle(((AEBaseTile) te).getCustomName()),
                        GuiStyleManager.loadStyleDoc("/screens/inscriber.json"));
                }
            }
            case CRYSTAL_ASSEMBLER -> {
                if (te instanceof TileCrystalAssembler) {
                    ContainerCrystalAssembler container = initTileContainer(new ContainerCrystalAssembler(
                        player.inventory, (TileCrystalAssembler) te), te, ID);
                    return new GuiCrystalAssembler(container, player.inventory,
                        customTitle(((AEBaseTile) te).getCustomName()),
                        GuiStyleManager.loadStyleDoc("/screens/crystal_assembler.json"));
                }
            }
            case INGREDIENT_BUFFER -> {
                if (te instanceof TileIngredientBuffer) {
                    ContainerIngredientBuffer container = initTileContainer(new ContainerIngredientBuffer(
                        player.inventory, (TileIngredientBuffer) te), te, ID);
                    return new GuiIngredientBuffer(container, player.inventory,
                        customTitle(((AEBaseTile) te).getCustomName()),
                        GuiStyleManager.loadStyleDoc("/screens/ingredient_buffer.json"));
                }
            }
            case CANER -> {
                if (te instanceof TileCaner) {
                    ContainerCaner container = initTileContainer(new ContainerCaner(player.inventory,
                        (TileCaner) te), te, ID);
                    return new GuiCaner(container, player.inventory,
                        customTitle(((AEBaseTile) te).getCustomName()),
                        GuiStyleManager.loadStyleDoc("/screens/caner.json"));
                }
            }
            case IO_PORT -> {
                if (te instanceof TileIOPort) {
                    ContainerIOPort container = initTileContainer(new ContainerIOPort(player.inventory,
                        (TileIOPort) te), te, ID);
                    return new GuiIOPort(container, player.inventory,
                        customTitle(((AEBaseTile) te).getCustomName()),
                        GuiStyleManager.loadStyleDoc("/screens/io_port.json"));
                }
            }
            case MOLECULAR_ASSEMBLER -> {
                if (te instanceof TileMolecularAssembler) {
                    ContainerMolecularAssembler container = initTileContainer(
                        new ContainerMolecularAssembler(player.inventory, (TileMolecularAssembler) te), te, ID);
                    return new GuiMolecularAssembler(container, player.inventory,
                        customTitle(((AEBaseTile) te).getCustomName()),
                        GuiStyleManager.loadStyleDoc("/screens/molecular_assembler.json"));
                }
            }
            case VIBRATION_CHAMBER -> {
                if (te instanceof TileVibrationChamber) {
                    ContainerVibrationChamber container = initTileContainer(new ContainerVibrationChamber(
                        player.inventory, (TileVibrationChamber) te), te, ID);
                    return new GuiVibrationChamber(container, player.inventory,
                        customTitle(((AEBaseTile) te).getCustomName()),
                        GuiStyleManager.loadStyleDoc("/screens/vibration_chamber.json"));
                }
            }
            case QNB -> {
                if (te instanceof TileQuantumBridge) {
                    ContainerQNB container = initTileContainer(new ContainerQNB(player.inventory,
                        (TileQuantumBridge) te), te, ID);
                    return new GuiQNB(container, player.inventory,
                        customTitle(((AEBaseTile) te).getCustomName()),
                        GuiStyleManager.loadStyleDoc("/screens/qnb.json"));
                }
            }
            case WIRELESS_ACCESS_POINT -> {
                if (te instanceof TileWirelessAccessPoint) {
                    ContainerWirelessAccessPoint container = initTileContainer(new ContainerWirelessAccessPoint(
                        player.inventory, (TileWirelessAccessPoint) te), te, ID);
                    return new GuiWirelessAccessPoint(container, player.inventory,
                        customTitle(((AEBaseTile) te).getCustomName()),
                        GuiStyleManager.loadStyleDoc("/screens/wireless_access_point.json"));
                }
            }
            case SPATIAL_IO_PORT -> {
                if (te instanceof TileSpatialIOPort) {
                    ContainerSpatialIOPort container = initTileContainer(new ContainerSpatialIOPort(player.inventory,
                        (TileSpatialIOPort) te), te, ID);
                    return new GuiSpatialIOPort(container, player.inventory,
                        customTitle(((AEBaseTile) te).getCustomName()),
                        GuiStyleManager.loadStyleDoc("/screens/spatial_io_port.json"));
                }
            }
            case SPATIAL_ANCHOR -> {
                if (te instanceof TileSpatialAnchor) {
                    ContainerSpatialAnchor container = initTileContainer(new ContainerSpatialAnchor(player.inventory,
                        (TileSpatialAnchor) te), te, ID);
                    return new GuiSpatialAnchor(container, player.inventory,
                        customTitle(((AEBaseTile) te).getCustomName()),
                        GuiStyleManager.loadStyleDoc("/screens/spatial_anchor.json"));
                }
            }
            case INTERFACE -> {
                if (te instanceof TileInterface) {
                    ContainerInterface container = initTileContainer(new ContainerInterface(player.inventory,
                        (TileInterface) te), te, ID);
                    return new GuiInterface(container, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/interface.json"));
                }
                ContainerInterface interfacePartContainer = createPartContainer(player,
                    partLocator(x, y, z), ID,
                    InterfacePart.class, host -> new ContainerInterface(player.inventory, host));
                if (interfacePartContainer != null) {
                    return new GuiInterface(interfacePartContainer, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/interface.json"));
                }
                return null;
            }
            case PATTERN_PROVIDER -> {
                if (te instanceof TilePatternProvider) {
                    ContainerPatternProvider container = initTileContainer(new ContainerPatternProvider(
                        player.inventory, (TilePatternProvider) te), te, ID);
                    return new GuiPatternProvider(container, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/pattern_provider.json"));
                }
                ContainerPatternProvider patternProviderPartContainer = createPartContainer(player,
                    partLocator(x, y, z), ID,
                    PatternProviderPart.class, host -> new ContainerPatternProvider(player.inventory, host));
                if (patternProviderPartContainer != null) {
                    return new GuiPatternProvider(patternProviderPartContainer, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/pattern_provider.json"));
                }
                return null;
            }
            case REQUESTER -> {
                if (te instanceof TileRequester requester) {
                    ContainerRequester container = initTileContainer(new ContainerRequester(player.inventory,
                        requester), te, ID);
                    return new GuiRequester(container, player.inventory, requester.getRequesterName(),
                        GuiStyleManager.loadStyleDoc("/screens/requester.json"));
                }
            }
            case CRAFTING_CPU -> {
                if (te instanceof ICraftingCPUTileEntity craftingUnit) {
                    ContainerCraftingCPU container = initTileContainer(new ContainerCraftingCPU(player.inventory,
                        craftingUnit), te, ID);
                    return new GuiCraftingCPU<>(container, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/crafting_cpu.json"));
                }
            }
            case CRAFTING_TREE -> {
                if (player.openContainer instanceof ContainerCraftConfirm confirm
                    && confirm.getTarget() instanceof ITerminalHost terminalHost) {
                    ContainerCraftingTree container = new ContainerCraftingTree(player.inventory, terminalHost);
                    container.setLocator(confirm.getLocator());
                    return new GuiCraftingTree(container, player.inventory);
                }
            }
            case RENAMER -> {
                ContainerRenamer renamer = createPartContainer(player, partLocator(x, y, z), ID, ICustomName.class,
                    host -> new ContainerRenamer(player.inventory, host));
                if (renamer == null && te instanceof ICustomName customName) {
                    renamer = initTileContainer(new ContainerRenamer(player.inventory, customName), te, ID);
                }
                return renamer == null ? null : new GuiRenamer(renamer, player.inventory,
                    GuiStyleManager.loadStyleDoc("/screens/renamer.json"));
            }
            case IMPORT_BUS -> {
                ContainerIOBus importBusContainer = createPartContainer(player,
                    partLocator(x, y, z), ID,
                    ImportBusPart.class, host -> new ContainerIOBus(player.inventory, host));
                if (importBusContainer != null) {
                    return new GuiIOBus(importBusContainer, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/import_bus.json"));
                }
                return null;
            }
            case EXPORT_BUS -> {
                ContainerIOBus exportBusContainer = createPartContainer(player,
                    partLocator(x, y, z), ID,
                    IOBusPart.class, host -> new ContainerIOBus(player.inventory, host));
                if (exportBusContainer != null) {
                    return new GuiIOBus(exportBusContainer, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/export_bus.json"));
                }
                return null;
            }
            case STORAGE_BUS -> {
                ContainerStorageBus storageBusContainer = createPartContainer(player,
                    partLocator(x, y, z), ID,
                    StorageBusPart.class, host -> new ContainerStorageBus(player.inventory, host));
                if (storageBusContainer != null) {
                    return new GuiStorageBus(storageBusContainer, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/storage_bus.json"));
                }
                return null;
            }
            case OD_EXPORT_BUS -> {
                ContainerODFilterBus<?> odContainer = createPartContainer(player, partLocator(x, y, z), ID,
                    ODExportBusPart.class, host -> new ContainerODFilterBus<>(player.inventory, host));
                return odContainer == null ? null : new GuiODFilterBus(odContainer, player.inventory, null,
                    GuiStyleManager.loadStyleDoc("/screens/od_export_bus.json"));
            }
            case MOD_EXPORT_BUS -> {
                ContainerModFilterBus<?> modContainer = createPartContainer(player, partLocator(x, y, z), ID,
                    ModExportBusPart.class, host -> new ContainerModFilterBus<>(player.inventory, host));
                return modContainer == null ? null : new GuiModFilterBus(modContainer, player.inventory, null,
                    GuiStyleManager.loadStyleDoc("/screens/mod_export_bus.json"));
            }
            case PRECISE_EXPORT_BUS -> {
                ContainerIOBus preciseContainer = createPartContainer(player, partLocator(x, y, z), ID,
                    PreciseExportBusPart.class, host -> new ContainerIOBus(player.inventory, host));
                return preciseContainer == null ? null : new GuiSpecialPreciseExportBus(preciseContainer,
                    player.inventory, null, GuiStyleManager.loadStyleDoc("/screens/precise_export_bus.json"));
            }
            case THRESHOLD_EXPORT_BUS -> {
                ContainerThresholdExportBus thresholdContainer = createPartContainer(player, partLocator(x, y, z), ID,
                    ThresholdExportBusPart.class, host -> new ContainerThresholdExportBus(player.inventory, host));
                return thresholdContainer == null ? null : new GuiThresholdExportBus(thresholdContainer,
                    player.inventory, null, GuiStyleManager.loadStyleDoc("/screens/threshold_export_bus.json"));
            }
            case STOCK_EXPORT_BUS -> {
                ContainerStockExportBus stockContainer = createPartContainer(player, partLocator(x, y, z), ID,
                    StockExportBusPart.class, host -> new ContainerStockExportBus(player.inventory, host));
                return stockContainer == null ? null : new GuiStockExportBus<>(stockContainer, player.inventory, null,
                    GuiStyleManager.loadStyleDoc("/screens/stock_export_bus.json"));
            }
            case IMPORT_EXPORT_BUS -> {
                ContainerIOBus importExportContainer = createPartContainer(player, partLocator(x, y, z), ID,
                    ImportExportBusPart.class, host -> new ContainerIOBus(player.inventory, host));
                return importExportContainer == null ? null : new GuiImportExportBus(importExportContainer, player.inventory,
                    null, GuiStyleManager.loadStyleDoc("/screens/import_export_bus.json"));
            }
            case ADVANCED_IO_BUS -> {
                ContainerAdvancedIOBus advancedContainer = createPartContainer(player, partLocator(x, y, z), ID,
                    AdvancedIOBusPart.class, host -> new ContainerAdvancedIOBus(player.inventory, host));
                return advancedContainer == null ? null : new GuiAdvancedIOBus(advancedContainer, player.inventory,
                    null, GuiStyleManager.loadStyleDoc("/screens/advanced_io_bus.json"));
            }
            case OD_STORAGE_BUS -> {
                ContainerODStorageBus odContainer = createPartContainer(player, partLocator(x, y, z), ID,
                    ODStorageBusPart.class, host -> new ContainerODStorageBus(player.inventory, host));
                return odContainer == null ? null : new GuiODStorageBus(odContainer, player.inventory, null,
                    GuiStyleManager.loadStyleDoc("/screens/od_storage_bus.json"));
            }
            case MOD_STORAGE_BUS -> {
                ContainerModStorageBus modContainer = createPartContainer(player, partLocator(x, y, z), ID,
                    ModStorageBusPart.class, host -> new ContainerModStorageBus(player.inventory, host));
                return modContainer == null ? null : new GuiModStorageBus(modContainer, player.inventory, null,
                    GuiStyleManager.loadStyleDoc("/screens/mod_storage_bus.json"));
            }
            case PRECISE_STORAGE_BUS -> {
                ContainerStorageBus preciseStorageContainer = createPartContainer(player, partLocator(x, y, z), ID,
                    PreciseStorageBusPart.class, host -> new ContainerStorageBus(player.inventory, host));
                return preciseStorageContainer == null ? null : new GuiPreciseStorageBus(preciseStorageContainer,
                    player.inventory, null, GuiStyleManager.loadStyleDoc("/screens/precise_storage_bus.json"));
            }
            case FORMATION_PLANE -> {
                ContainerFormationPlane formationPlaneContainer = createPartContainer(player,
                    partLocator(x, y, z), ID,
                    FormationPlanePart.class, host -> new ContainerFormationPlane(player.inventory, host));
                if (formationPlaneContainer != null) {
                    return new GuiFormationPlane(formationPlaneContainer, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/formation_plane.json"));
                }
                return null;
            }
            case ANNIHILATION_PLANE -> {
                ContainerAnnihilationPlane container = createPartContainer(player, partLocator(x, y, z), ID,
                    AnnihilationPlanePart.class, host -> new ContainerAnnihilationPlane(player.inventory, host));
                return container == null ? null : new GuiAnnihilationPlane(container, player.inventory, null,
                    GuiStyleManager.loadStyleDoc("/screens/annihilation_plane.json"));
            }
            case ENERGY_LEVEL_EMITTER -> {
                ContainerEnergyLevelEmitter energyLevelEmitterContainer = createPartContainer(player,
                    partLocator(x, y, z), ID,
                    EnergyLevelEmitterPart.class,
                    host -> new ContainerEnergyLevelEmitter(player.inventory, host, host.getReportingValue()));
                if (energyLevelEmitterContainer != null) {
                    return new GuiEnergyLevelEmitter(energyLevelEmitterContainer, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/energy_level_emitter.json"));
                }
                return null;
            }
            case STORAGE_LEVEL_EMITTER -> {
                ContainerStorageLevelEmitter storageLevelEmitterContainer = createPartContainer(player,
                    partLocator(x, y, z), ID,
                    StorageLevelEmitterPart.class,
                    host -> new ContainerStorageLevelEmitter(player.inventory, host,
                        host.getConfig().getStack(0), host.getReportingValue()));
                if (storageLevelEmitterContainer != null) {
                    return new GuiStorageLevelEmitter(storageLevelEmitterContainer, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/level_emitter.json"));
                }
                return null;
            }
            case THRESHOLD_LEVEL_EMITTER -> {
                ContainerThresholdLevelEmitter container = createPartContainer(player, partLocator(x, y, z), ID,
                    ThresholdLevelEmitterPart.class,
                    host -> new ContainerThresholdLevelEmitter(player.inventory, host, host.getConfig().getStack(0),
                        host.getUpperValue(), host.getLowerValue()));
                return container == null ? null : new GuiThresholdLevelEmitter(container, player.inventory, null,
                    GuiStyleManager.loadStyleDoc("/screens/threshold_level_emitter.json"));
            }
            case ME_STORAGE_TERMINAL -> {
                ContainerMEStorage storageTerminalContainer = createPartContainer(player,
                    partLocator(x, y, z), ID,
                    ItemTerminalPart.class,
                    host -> new ContainerMEStorage(GuiIds.GuiKey.ME_STORAGE_TERMINAL, player.inventory, host));
                if (storageTerminalContainer != null) {
                    return new GuiMEStorage<>(storageTerminalContainer, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/terminals/terminal.json"));
                }
                return null;
            }
            case CELL_TERMINAL -> {
                ContainerCellTerminal cellTerminalContainer = createPartContainer(player,
                    partLocator(x, y, z), ID,
                    CellTerminalContainerHost.class, host -> new ContainerCellTerminal(player.inventory, host));
                return cellTerminalContainer == null ? null : new GuiCellTerminal(cellTerminalContainer, player.inventory);
            }
            case CRAFTING_TERMINAL -> {
                ContainerCraftingTerm craftingTerminalContainer = createPartContainer(player,
                    partLocator(x, y, z), ID,
                    CraftingTerminalPart.class, host -> new ContainerCraftingTerm(player.inventory, host));
                if (craftingTerminalContainer != null) {
                    return new GuiCraftingTerm(craftingTerminalContainer, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/terminals/crafting_terminal.json"));
                }
                return null;
            }
            case PATTERN_ENCODING_TERMINAL -> {
                ContainerPatternEncodingTerm patternEncodingTerminalContainer = createPartContainer(player,
                    partLocator(x, y, z), ID,
                    PatternEncodingTerminalPart.class,
                    host -> new ContainerPatternEncodingTerm(player.inventory, host));
                if (patternEncodingTerminalContainer != null) {
                    return new GuiPatternEncodingTerm(patternEncodingTerminalContainer, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/terminals/pattern_encoding_terminal.json"));
                }
                return null;
            }
            case PATTERN_ACCESS_TERMINAL -> {
                ContainerPatternAccessTerm patternAccessTerminalContainer = createPartContainer(player,
                    partLocator(x, y, z), ID,
                    PatternAccessTerminalPart.class,
                    host -> new ContainerPatternAccessTerm(player.inventory, host));
                if (patternAccessTerminalContainer != null) {
                    return new GuiPatternAccessTerm<>(patternAccessTerminalContainer, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/terminals/pattern_access_terminal.json"));
                }
                return null;
            }
            case REQUESTER_TERMINAL -> {
                ContainerRequesterTerm requesterTerminalContainer = createPartContainer(player,
                    partLocator(x, y, z), ID,
                    RequesterTerminalPart.class,
                    host -> new ContainerRequesterTerm(player.inventory, host));
                if (requesterTerminalContainer != null) {
                    return new GuiRequesterTerm(requesterTerminalContainer, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/terminals/requester_terminal.json"));
                }
                return null;
            }
            case QUARTZ_KNIFE -> {
                ContainerQuartzKnife container = createQuartzKnifeContainer(player, x, ID);
                if (container != null) {
                    return new GuiQuartzKnife(container, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/quartz_knife.json"));
                }
                return null;
            }
            case NETWORK_TOOL -> {
                ContainerNetworkTool networkToolContainer = createNetworkToolContainer(player, x, ID);
                if (networkToolContainer != null) {
                    return new GuiNetworkTool(networkToolContainer, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/network_tool.json"));
                }
                return null;
            }
            case NETWORK_ANALYSER -> {
                ContainerNetworkAnalyser container = createNetworkAnalyserContainer(player, x, ID);
                return container == null ? null : new GuiNetworkAnalyser(container, player.inventory);
            }
            case TICK_ANALYSER -> {
                ContainerTickAnalyser container = createTickAnalyserContainer(player, x, ID);
                return container == null ? null : new GuiTickAnalyser(container, player.inventory);
            }
            case CONFIG_MODIFIER -> {
                ContainerConfigModifier container = createConfigModifierContainer(player, x, ID);
                return container == null ? null : new GuiConfigModifier(container, player.inventory,
                    GuiStyleManager.loadStyleDoc("/screens/config_modifier.json"));
            }
            case PATTERN_MODIFIER -> {
                ContainerPatternModifier container = createPatternModifierContainer(player, x, y, z, ID);
                return container == null ? null : new GuiPatternModifier(container, player.inventory,
                    GuiStyleManager.loadStyleDoc("/screens/pattern_modifier.json"));
            }
            case ADVANCED_MEMORY_CARD -> {
                ContainerAdvancedMemoryCard container = createAdvancedMemoryCardContainer(player, x, y, z, ID);
                return container == null ? null : new GuiAdvancedMemoryCard(container, player.inventory);
            }
            case NETWORK_STATUS -> {
                ContainerNetworkStatus networkStatusContainer = createNetworkStatusContainer(player, y >> 8,
                    new BlockPos(x, y & 255, z), ID);
                if (networkStatusContainer != null) {
                    return new GuiNetworkStatus<>(networkStatusContainer, player.inventory,
                        GuiStyleManager.loadStyleDoc("/screens/network_status.json"));
                }
                return null;
            }
            case PORTABLE_ITEM_CELL -> {
                ContainerMEStorage portableItemCellContainer = createPortableItemCellContainer(player, x, ID);
                if (portableItemCellContainer != null) {
                    return new GuiMEStorage<>(portableItemCellContainer, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/terminals/portable_item_cell.json"));
                }
                return null;
            }
            case PORTABLE_CELL_PICKUP_FILTER -> {
                ContainerPortableCellPickupFilter portableCellPickupFilterContainer =
                    createPortableCellPickupFilterContainer(player, x, ID);
                if (portableCellPickupFilterContainer != null) {
                    return new GuiPortableCellPickupFilter(portableCellPickupFilterContainer, player.inventory, null);
                }
                return null;
            }
            case PORTABLE_CELL_WORKBENCH -> {
                ContainerCellWorkbench container = createPortableCellWorkbenchContainer(player, x, ID);
                if (container != null) {
                    return new GuiCellWorkbench(container, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/portable_cell_workbench.json"));
                }
                return null;
            }
            case VOID_CELL -> {
                ContainerVoidCell container = createVoidCellContainer(player, x, ID);
                if (container != null) {
                    return new GuiVoidCell(container, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/void_cell.json"));
                }
                return null;
            }
            case PORTABLE_VOID_CELL -> {
                ContainerPortableVoidCell container = createPortableVoidCellContainer(player, x, ID);
                if (container != null) {
                    return new GuiPortableVoidCell(container, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/portable_void_cell_condenser.json"));
                }
                return null;
            }
            case PORTABLE_FLUID_CELL -> {
                ContainerMEStorage portableFluidCellContainer = createPortableFluidCellContainer(player, x, ID);
                if (portableFluidCellContainer != null) {
                    return new GuiMEStorage<>(portableFluidCellContainer, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/terminals/portable_fluid_cell.json"));
                }
                return null;
            }
            case WIRELESS_TERMINAL -> {
                ContainerMEStorage wirelessTerminalContainer = createWirelessTerminalContainer(player, x, ID);
                if (wirelessTerminalContainer != null) {
                    return new GuiMEStorage<>(wirelessTerminalContainer, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/terminals/wireless_terminal.json"));
                }
                return null;
            }
            case WIRELESS_CELL_TERMINAL -> {
                ContainerCellTerminal wirelessCellTerminalContainer = createWirelessCellTerminalContainer(player, x, ID);
                return wirelessCellTerminalContainer == null ? null
                    : new GuiCellTerminal(wirelessCellTerminalContainer, player.inventory);
            }
            case WIRELESS_CRAFTING_TERMINAL -> {
                ContainerWirelessCraftingTerm wirelessCraftingTerminalContainer =
                    createWirelessCraftingTerminalContainer(player, x, ID);
                if (wirelessCraftingTerminalContainer != null) {
                    return new GuiCraftingTerm(wirelessCraftingTerminalContainer, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/terminals/crafting_terminal.json"));
                }
                return null;
            }
            case WIRELESS_PATTERN_ENCODING_TERMINAL -> {
                ContainerPatternEncodingTerm wirelessPatternEncodingTerminalContainer =
                    createWirelessPatternEncodingTerminalContainer(player, x, ID);
                if (wirelessPatternEncodingTerminalContainer != null) {
                    return new GuiPatternEncodingTerm(wirelessPatternEncodingTerminalContainer, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/terminals/pattern_encoding_terminal.json"));
                }
                return null;
            }
            case WIRELESS_PATTERN_ACCESS_TERMINAL -> {
                ContainerPatternAccessTerm wirelessPatternAccessTerminalContainer =
                    createWirelessPatternAccessTerminalContainer(player, x, ID);
                if (wirelessPatternAccessTerminalContainer != null) {
                    return new GuiPatternAccessTerm<>(wirelessPatternAccessTerminalContainer, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/terminals/pattern_access_terminal.json"));
                }
                return null;
            }
            case WIRELESS_REQUESTER_TERMINAL -> {
                ContainerRequesterTerm wirelessRequesterTerminalContainer =
                    createWirelessRequesterTerminalContainer(player, x, ID);
                if (wirelessRequesterTerminalContainer != null) {
                    return new GuiRequesterTerm(wirelessRequesterTerminalContainer, player.inventory, null,
                        GuiStyleManager.loadStyleDoc("/screens/terminals/requester_terminal.json"));
                }
                return null;
            }
            case WIRELESS_TERMINAL_DYNAMIC -> {
                AEBaseContainer wirelessTerminalContainer = createDynamicWirelessTerminalContainer(player, x, y, ID);
                if (wirelessTerminalContainer == null) {
                    return null;
                }
                WirelessTerminalDefinition definition = WirelessTerminalRegistry.definitionAtIndex(y);
                return definition == null ? null : definition.screenFactory()
                                                             .create(definition, wirelessTerminalContainer,
                                                                 player.inventory);
            }
        }
        return null;
    }

    private @Nullable ContainerConfigModifier createConfigModifierContainer(EntityPlayer player, int slot, int guiId) {
        ItemGuiHostLocator locator = GuiHostLocators.forInventorySlot(slot);
        ItemGuiHost<?> host = createItemGuiHost(player, locator, GuiIds.GuiKey.CONFIG_MODIFIER);
        if (!(host instanceof ConfigModifierGuiHost configModifierHost)) {
            return null;
        }

        return initContainer(new ContainerConfigModifier(player.inventory, configModifierHost), locator, guiId);
    }

    private @Nullable ContainerPatternModifier createPatternModifierContainer(EntityPlayer player, int x, int y, int z,
                                                                              int guiId) {
        int encodedSlot = (y >> 8) & 0xFF;
        ItemGuiHostLocator locator;
        if (x < 0 && y == 0 && z == 0) {
            locator = GuiHostLocators.forBaubleSlot(-x - 1);
        } else if (encodedSlot > 0) {
            RayTraceResult hitResult = unpackItemUseHitResult(x, y, z);
            if (hitResult == null) {
                return null;
            }
            locator = GuiHostLocators.forInventorySlot(encodedSlot - 1, hitResult);
        } else {
            locator = GuiHostLocators.forInventorySlot(x);
        }
        ItemGuiHost<?> host = createItemGuiHost(player, locator, GuiIds.GuiKey.PATTERN_MODIFIER);
        if (!(host instanceof PatternModifierGuiHost patternModifierHost)) {
            return null;
        }

        return initContainer(new ContainerPatternModifier(player.inventory, patternModifierHost), locator, guiId);
    }

    private @Nullable ContainerNetworkStatus createNetworkStatusContainer(EntityPlayer player, int slot, BlockPos pos, int guiId) {
        ItemGuiHostLocator locator = GuiHostLocators.forInventorySlot(slot,
            GuiHostLocators.createItemUseHitResult(pos, EnumFacing.UP, 0.5f, 0.5f, 0.5f));
        NetworkToolGuiHost<?> host = createNetworkToolGuiHost(player, locator);
        if (host == null || host.getGridHost() == null) {
            return null;
        }

        return initContainer(new ContainerNetworkStatus(player.inventory, host), locator, guiId);
    }

    private @Nullable ContainerAdvancedMemoryCard createAdvancedMemoryCardContainer(EntityPlayer player, int x, int y,
                                                                                    int z, int guiId) {
        int encodedSlot = (y >> 8) & 0xFF;
        if (encodedSlot == 0) {
            return null;
        }
        RayTraceResult hitResult = unpackItemUseHitResult(x, y, z);
        if (hitResult == null) {
            return null;
        }
        ItemGuiHostLocator locator = GuiHostLocators.forInventorySlot(encodedSlot - 1, hitResult);
        ItemGuiHost<?> host = createItemGuiHost(player, locator, GuiIds.GuiKey.ADVANCED_MEMORY_CARD);
        if (!(host instanceof AdvancedMemoryCardGuiHost advancedMemoryCardHost)
            || (advancedMemoryCardHost.getGridHost() == null && advancedMemoryCardHost.getClickedGridNode() == null)) {
            return null;
        }

        return initContainer(new ContainerAdvancedMemoryCard(player.inventory, advancedMemoryCardHost), locator, guiId);
    }

    private @Nullable ContainerMEStorage createPortableItemCellContainer(EntityPlayer player, int slot, int guiId) {
        ItemGuiHostLocator locator = GuiHostLocators.forInventorySlot(slot);
        IPortableTerminal host = createPortableTerminalHost(player, locator);
        if (host == null) {
            return null;
        }

        return initContainer(new ContainerMEStorage(GuiIds.GuiKey.PORTABLE_ITEM_CELL, player.inventory, host),
            locator, guiId);
    }

    private @Nullable ContainerPortableCellPickupFilter createPortableCellPickupFilterContainer(EntityPlayer player,
                                                                                                int slot,
                                                                                                int guiId) {
        ItemGuiHostLocator locator = GuiHostLocators.forInventorySlot(slot);
        ItemGuiHost<?> host = createItemGuiHost(player, locator);
        if (host == null) {
            return null;
        }
        if (!PortableItemCellAutoPickup.isSupported(host.getItemStack())) {
            return null;
        }

        return initContainer(new ContainerPortableCellPickupFilter(player.inventory, host), locator, guiId);
    }

    private @Nullable ContainerPortableVoidCell createPortableVoidCellContainer(EntityPlayer player, int slot,
                                                                                int guiId) {
        ItemGuiHostLocator locator = GuiHostLocators.forInventorySlot(slot);
        ItemGuiHost<?> host = createItemGuiHost(player, locator, GuiIds.GuiKey.PORTABLE_VOID_CELL);
        if (!(host instanceof PortableVoidCellGuiHost portableVoidCellHost)) {
            return null;
        }

        return initContainer(new ContainerPortableVoidCell(player.inventory, portableVoidCellHost), locator, guiId);
    }

    private @Nullable ContainerVoidCell createVoidCellContainer(EntityPlayer player, int slot, int guiId) {
        ItemGuiHostLocator locator = GuiHostLocators.forInventorySlot(slot);
        ItemGuiHost<?> host = createItemGuiHost(player, locator, GuiIds.GuiKey.VOID_CELL);
        if (!(host instanceof VoidCellGuiHost voidCellHost)) {
            return null;
        }

        return initContainer(new ContainerVoidCell(player.inventory, voidCellHost), locator, guiId);
    }

    private @Nullable ContainerCellWorkbench createPortableCellWorkbenchContainer(EntityPlayer player, int slot,
                                                                                  int guiId) {
        ItemGuiHostLocator locator = GuiHostLocators.forInventorySlot(slot);
        ItemGuiHost<?> host = createItemGuiHost(player, locator, GuiIds.GuiKey.PORTABLE_CELL_WORKBENCH);
        if (!(host instanceof ICellWorkbenchHost cellWorkbenchHost)) {
            return null;
        }

        return initContainer(new ContainerCellWorkbench(player.inventory, cellWorkbenchHost), locator, guiId);
    }

    private @Nullable ContainerMEStorage createPortableFluidCellContainer(EntityPlayer player, int slot, int guiId) {
        ItemGuiHostLocator locator = GuiHostLocators.forInventorySlot(slot);
        IPortableTerminal host = createPortableTerminalHost(player, locator);
        if (host == null) {
            return null;
        }

        return initContainer(new ContainerMEStorage(GuiIds.GuiKey.PORTABLE_FLUID_CELL, player.inventory, host),
            locator, guiId);
    }

    private @Nullable AEBaseContainer createDynamicWirelessTerminalContainer(EntityPlayer player, int slot,
                                                                             int terminalIndex, int guiId) {
        WirelessTerminalDefinition definition = WirelessTerminalRegistry.definitionAtIndex(terminalIndex);
        if (definition == null) {
            return null;
        }

        ItemGuiHostLocator locator = GuiHostLocators.forInventorySlot(slot);
        ItemGuiHost<?> host = createItemGuiHost(player, locator, definition);
        if (!(host instanceof WirelessTerminalGuiHost<?> wirelessHost)) {
            return null;
        }

        AEBaseContainer container = definition.containerFactory().create(definition, player.inventory, wirelessHost);
        return container == null ? null : initContainer(container, locator, guiId);
    }

    private @Nullable ContainerMEStorage createWirelessTerminalContainer(EntityPlayer player, int slot, int guiId) {
        ItemGuiHostLocator locator = GuiHostLocators.forInventorySlot(slot);
        IPortableTerminal host = createPortableTerminalHost(player, locator, GuiIds.GuiKey.WIRELESS_TERMINAL);
        if (host == null) {
            return null;
        }

        return initContainer(new ContainerMEStorage(GuiIds.GuiKey.WIRELESS_TERMINAL, player.inventory, host),
            locator, guiId);
    }

    private @Nullable ContainerCellTerminal createWirelessCellTerminalContainer(EntityPlayer player, int slot,
                                                                                int guiId) {
        ItemGuiHostLocator locator = GuiHostLocators.forInventorySlot(slot);
        ItemGuiHost<?> host = createItemGuiHost(player, locator, GuiIds.GuiKey.WIRELESS_CELL_TERMINAL);
        if (!(host instanceof CellTerminalContainerHost cellTerminalHost)) {
            return null;
        }

        return initContainer(new ContainerCellTerminal(player.inventory, cellTerminalHost), locator, guiId);
    }

    private @Nullable ContainerWirelessCraftingTerm createWirelessCraftingTerminalContainer(EntityPlayer player, int slot,
                                                                                            int guiId) {
        ItemGuiHostLocator locator = GuiHostLocators.forInventorySlot(slot);
        ItemGuiHost<?> host = createItemGuiHost(player, locator, GuiIds.GuiKey.WIRELESS_CRAFTING_TERMINAL);
        if (!(host instanceof WirelessCraftingTerminalGuiHost wirelessHost)) {
            return null;
        }

        return initContainer(new ContainerWirelessCraftingTerm(player.inventory, wirelessHost), locator, guiId);
    }

    private @Nullable ContainerPatternEncodingTerm createWirelessPatternEncodingTerminalContainer(EntityPlayer player,
                                                                                                  int slot, int guiId) {
        ItemGuiHostLocator locator = GuiHostLocators.forInventorySlot(slot);
        ItemGuiHost<?> host = createItemGuiHost(player, locator, GuiIds.GuiKey.WIRELESS_PATTERN_ENCODING_TERMINAL);
        if (!(host instanceof WirelessPatternEncodingTerminalGuiHost wirelessHost)) {
            return null;
        }

        return initContainer(new ContainerPatternEncodingTerm(GuiIds.GuiKey.WIRELESS_PATTERN_ENCODING_TERMINAL,
            player.inventory, wirelessHost, true), locator, guiId);
    }

    private @Nullable ContainerPatternAccessTerm createWirelessPatternAccessTerminalContainer(EntityPlayer player,
                                                                                              int slot, int guiId) {
        ItemGuiHostLocator locator = GuiHostLocators.forInventorySlot(slot);
        ItemGuiHost<?> host = createItemGuiHost(player, locator, GuiIds.GuiKey.WIRELESS_PATTERN_ACCESS_TERMINAL);
        if (!(host instanceof WirelessPatternAccessTerminalGuiHost wirelessHost)) {
            return null;
        }

        return initContainer(new ContainerPatternAccessTerm(player.inventory, wirelessHost), locator, guiId);
    }

    private @Nullable ContainerRequesterTerm createWirelessRequesterTerminalContainer(EntityPlayer player,
                                                                                      int slot, int guiId) {
        ItemGuiHostLocator locator = GuiHostLocators.forInventorySlot(slot);
        ItemGuiHost<?> host = createItemGuiHost(player, locator, GuiIds.GuiKey.WIRELESS_REQUESTER_TERMINAL);
        if (!(host instanceof WirelessRequesterTerminalGuiHost wirelessHost)) {
            return null;
        }

        return initContainer(new ContainerRequesterTerm(player.inventory, wirelessHost), locator, guiId);
    }

    private @Nullable IPortableTerminal createPortableTerminalHost(EntityPlayer player, ItemGuiHostLocator locator) {
        return createPortableTerminalHost(player, locator, null);
    }

    private @Nullable IPortableTerminal createPortableTerminalHost(EntityPlayer player, ItemGuiHostLocator locator,
                                                                   GuiIds.GuiKey requestedGui) {
        ItemGuiHost<?> host = createItemGuiHost(player, locator, requestedGui);
        if (host instanceof IPortableTerminal portableTerminal) {
            return portableTerminal;
        }
        return null;
    }

    private @Nullable NetworkToolGuiHost<?> createNetworkToolGuiHost(EntityPlayer player, ItemGuiHostLocator locator) {
        ItemGuiHost<?> host = createItemGuiHost(player, locator);
        if (host instanceof NetworkToolGuiHost<?> networkToolGuiHost) {
            return networkToolGuiHost;
        }
        return null;
    }

    private @Nullable ItemGuiHost<?> createItemGuiHost(EntityPlayer player, ItemGuiHostLocator locator) {
        return createItemGuiHost(player, locator, (GuiIds.GuiKey) null);
    }

    private @Nullable ItemGuiHost<?> createItemGuiHost(EntityPlayer player, ItemGuiHostLocator locator,
                                                       GuiIds.GuiKey requestedGui) {
        Integer slot = locator.getPlayerInventorySlot();
        if (slot != null && (slot < 0 || slot >= player.inventory.getSizeInventory())) {
            return null;
        }

        ItemStack stack = locator.locateItem(player);
        if (stack.isEmpty() || !(stack.getItem() instanceof IGuiItem guiItem)) {
            return null;
        }

        selectUniversalTerminalForGui(stack, requestedGui);
        return guiItem.getGuiHost(player, locator, locator.hitResult());
    }

    private @Nullable ItemGuiHost<?> createItemGuiHost(EntityPlayer player, ItemGuiHostLocator locator,
                                                       WirelessTerminalDefinition requestedDefinition) {
        Integer slot = locator.getPlayerInventorySlot();
        if (slot != null && (slot < 0 || slot >= player.inventory.getSizeInventory())) {
            return null;
        }

        ItemStack stack = locator.locateItem(player);
        if (stack.isEmpty() || !(stack.getItem() instanceof IGuiItem guiItem)) {
            return null;
        }

        if (!selectTerminalForDefinition(stack, requestedDefinition)) {
            return null;
        }
        return guiItem.getGuiHost(player, locator, locator.hitResult());
    }

    private void selectUniversalTerminalForGui(ItemStack stack, GuiIds.GuiKey requestedGui) {
        if (requestedGui == null || !(stack.getItem() instanceof WirelessUniversalTerminalItem universalTerminal)) {
            return;
        }
        WirelessTerminalRegistry.allDefinitions()
                                .stream()
                                .filter(definition -> definition.item().getLegacyGuiKey(stack) == requestedGui)
                                .findFirst()
                                .ifPresent(definition -> universalTerminal.selectTerminal(stack, definition.id()));
    }

    private boolean selectTerminalForDefinition(ItemStack stack, WirelessTerminalDefinition requestedDefinition) {
        if (stack.getItem() instanceof WirelessUniversalTerminalItem universalTerminal) {
            return universalTerminal.hasTerminal(stack, requestedDefinition.item())
                && universalTerminal.selectTerminal(stack, requestedDefinition.id());
        }
        return stack.getItem() == requestedDefinition.item();
    }
}
