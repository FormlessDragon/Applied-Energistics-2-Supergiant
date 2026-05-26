package appeng.core.gui;

import appeng.api.implementations.guiobjects.IGuiItem;
import appeng.api.implementations.guiobjects.IPortableTerminal;
import appeng.api.implementations.guiobjects.ItemGuiHost;
import appeng.api.storage.ITerminalHost;
import appeng.client.ctl.gui.GuiCraftingTree;
import appeng.client.gui.implementations.GuiCellWorkbench;
import appeng.client.gui.implementations.GuiCondenser;
import appeng.client.gui.implementations.GuiDrive;
import appeng.client.gui.implementations.GuiEnergyLevelEmitter;
import appeng.client.gui.implementations.GuiFormationPlane;
import appeng.client.gui.implementations.GuiIOBus;
import appeng.client.gui.implementations.GuiIOPort;
import appeng.client.gui.implementations.GuiInscriber;
import appeng.client.gui.implementations.GuiInterface;
import appeng.client.gui.implementations.GuiMEChest;
import appeng.client.gui.implementations.GuiMolecularAssembler;
import appeng.client.gui.implementations.GuiPatternProvider;
import appeng.client.gui.implementations.GuiQNB;
import appeng.client.gui.implementations.GuiQuartzKnife;
import appeng.client.gui.implementations.GuiSkyChest;
import appeng.client.gui.implementations.GuiSpatialAnchor;
import appeng.client.gui.implementations.GuiSpatialIOPort;
import appeng.client.gui.implementations.GuiStorageBus;
import appeng.client.gui.implementations.GuiStorageLevelEmitter;
import appeng.client.gui.implementations.GuiVibrationChamber;
import appeng.client.gui.implementations.GuiWirelessAccessPoint;
import appeng.client.gui.me.common.GuiMEStorage;
import appeng.client.gui.me.crafting.GuiCraftingCPU;
import appeng.client.gui.me.items.GuiCraftingTerm;
import appeng.client.gui.me.items.GuiPatternEncodingTerm;
import appeng.client.gui.me.networktool.GuiNetworkStatus;
import appeng.client.gui.me.networktool.GuiNetworkTool;
import appeng.client.gui.me.patternaccess.GuiPatternAccessTerm;
import appeng.client.gui.networking.GuiControllerStatus;
import appeng.client.gui.style.GuiStyleManager;
import appeng.container.AEBaseContainer;
import appeng.container.GuiIds;
import appeng.container.implementations.ContainerCellWorkbench;
import appeng.container.implementations.ContainerCondenser;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.container.implementations.ContainerCraftingCPU;
import appeng.container.implementations.ContainerCraftingTree;
import appeng.container.implementations.ContainerDrive;
import appeng.container.implementations.ContainerEnergyLevelEmitter;
import appeng.container.implementations.ContainerFormationPlane;
import appeng.container.implementations.ContainerIOBus;
import appeng.container.implementations.ContainerIOPort;
import appeng.container.implementations.ContainerInscriber;
import appeng.container.implementations.ContainerInterface;
import appeng.container.implementations.ContainerMEChest;
import appeng.container.implementations.ContainerMolecularAssembler;
import appeng.container.implementations.ContainerNetworkStatus;
import appeng.container.implementations.ContainerNetworkTool;
import appeng.container.implementations.ContainerPatternAccessTerm;
import appeng.container.implementations.ContainerPatternProvider;
import appeng.container.implementations.ContainerQNB;
import appeng.container.implementations.ContainerQuartzKnife;
import appeng.container.implementations.ContainerSkyChest;
import appeng.container.implementations.ContainerSpatialAnchor;
import appeng.container.implementations.ContainerSpatialIOPort;
import appeng.container.implementations.ContainerStorageBus;
import appeng.container.implementations.ContainerStorageLevelEmitter;
import appeng.container.implementations.ContainerVibrationChamber;
import appeng.container.implementations.ContainerWirelessAccessPoint;
import appeng.container.me.common.ContainerMEStorage;
import appeng.container.me.items.ContainerBasicCellChest;
import appeng.container.me.items.ContainerCraftingTerm;
import appeng.container.me.items.ContainerPatternEncodingTerm;
import appeng.container.me.items.ContainerWirelessCraftingTerm;
import appeng.container.networking.ContainerControllerStatus;
import appeng.core.gui.locator.GuiHostLocator;
import appeng.core.gui.locator.GuiHostLocators;
import appeng.core.gui.locator.ItemGuiHostLocator;
import appeng.core.gui.locator.PartLocator;
import appeng.helpers.WirelessCraftingTerminalGuiHost;
import appeng.helpers.WirelessPatternAccessTerminalGuiHost;
import appeng.helpers.WirelessPatternEncodingTerminalGuiHost;
import appeng.items.contents.NetworkToolGuiHost;
import appeng.items.tools.powered.WirelessTerminalRegistry;
import appeng.items.tools.powered.WirelessUniversalTerminalItem;
import appeng.parts.AEBasePart;
import appeng.parts.automation.EnergyLevelEmitterPart;
import appeng.parts.automation.ExportBusPart;
import appeng.parts.automation.FormationPlanePart;
import appeng.parts.automation.ImportBusPart;
import appeng.parts.automation.StorageLevelEmitterPart;
import appeng.parts.crafting.PatternProviderPart;
import appeng.parts.encoding.PatternEncodingTerminalPart;
import appeng.parts.misc.InterfacePart;
import appeng.parts.reporting.CraftingTerminalPart;
import appeng.parts.reporting.ItemTerminalPart;
import appeng.parts.reporting.PatternAccessTerminalPart;
import appeng.parts.storagebus.StorageBusPart;
import appeng.tile.AEBaseTile;
import appeng.tile.crafting.TileCraftingUnit;
import appeng.tile.crafting.TileMolecularAssembler;
import appeng.tile.crafting.TilePatternProvider;
import appeng.tile.misc.TileCellWorkbench;
import appeng.tile.misc.TileCondenser;
import appeng.tile.misc.TileInscriber;
import appeng.tile.misc.TileInterface;
import appeng.tile.misc.TileVibrationChamber;
import appeng.tile.networking.TileController;
import appeng.tile.networking.TileWirelessAccessPoint;
import appeng.tile.qnb.TileQuantumBridge;
import appeng.tile.spatial.TileSpatialAnchor;
import appeng.tile.spatial.TileSpatialIOPort;
import appeng.tile.storage.TileDrive;
import appeng.tile.storage.TileIOPort;
import appeng.tile.storage.TileMEChest;
import appeng.tile.storage.TileSkyChest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

public class AEGuiHandler implements IGuiHandler {

    private static boolean isItemGui(GuiIds.GuiKey bridge) {
        return bridge == GuiIds.GuiKey.QUARTZ_KNIFE
            || bridge == GuiIds.GuiKey.NETWORK_TOOL
            || bridge == GuiIds.GuiKey.NETWORK_STATUS
            || bridge == GuiIds.GuiKey.PORTABLE_ITEM_CELL
            || bridge == GuiIds.GuiKey.PORTABLE_FLUID_CELL
            || bridge == GuiIds.GuiKey.WIRELESS_TERMINAL
            || bridge == GuiIds.GuiKey.WIRELESS_CRAFTING_TERMINAL
            || bridge == GuiIds.GuiKey.WIRELESS_PATTERN_ENCODING_TERMINAL
            || bridge == GuiIds.GuiKey.WIRELESS_PATTERN_ACCESS_TERMINAL;
    }

    private static boolean isPartGui(GuiIds.GuiKey bridge) {
        return bridge == GuiIds.GuiKey.IMPORT_BUS
            || bridge == GuiIds.GuiKey.EXPORT_BUS
            || bridge == GuiIds.GuiKey.STORAGE_BUS
            || bridge == GuiIds.GuiKey.FORMATION_PLANE
            || bridge == GuiIds.GuiKey.ENERGY_LEVEL_EMITTER
            || bridge == GuiIds.GuiKey.STORAGE_LEVEL_EMITTER
            || bridge == GuiIds.GuiKey.ME_STORAGE_TERMINAL
            || bridge == GuiIds.GuiKey.CRAFTING_TERMINAL
            || bridge == GuiIds.GuiKey.PATTERN_ENCODING_TERMINAL
            || bridge == GuiIds.GuiKey.PATTERN_ACCESS_TERMINAL;
    }

    private static @Nullable PartLocator partLocator(int x, int y, int z) {
        int side = y >> 8;
        if (side < 0 || side >= EnumFacing.VALUES.length) {
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
        return container;
    }

    private static @Nullable ITextComponent getDefaultGuiTitle(Object host) {
        if (host instanceof net.minecraft.world.IWorldNameable nameable) {
            if (nameable.hasCustomName()) {
                return nameable.getDisplayName();
            }
        }
        if (host instanceof AEBaseTile tile) {
            if (tile.hasCustomName()) {
                return tile.getCustomName();
            }
        }
        if (host instanceof AEBasePart part) {
            if (part.hasCustomName()) {
                return part.getCustomName();
            }
        }
        return null;
    }

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        GuiIds.GuiKey bridge = GuiIds.GuiKey.fromId(ID);
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
            case CRAFTING_CPU -> {
                if (te instanceof TileCraftingUnit) {
                    return initTileContainer(new ContainerCraftingCPU(player.inventory, (TileCraftingUnit) te),
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
            case IMPORT_BUS -> {
                return createPartContainer(player, partLocator(x, y, z), ID, ImportBusPart.class,
                    host -> new ContainerIOBus(player.inventory, host));
            }
            case EXPORT_BUS -> {
                return createPartContainer(player, partLocator(x, y, z), ID, ExportBusPart.class,
                    host -> new ContainerIOBus(player.inventory, host));
            }
            case STORAGE_BUS -> {
                return createPartContainer(player, partLocator(x, y, z), ID, StorageBusPart.class,
                    host -> new ContainerStorageBus(player.inventory, host));
            }
            case FORMATION_PLANE -> {
                return createPartContainer(player, partLocator(x, y, z), ID, FormationPlanePart.class,
                    host -> new ContainerFormationPlane(player.inventory, host));
            }
            case ENERGY_LEVEL_EMITTER -> {
                return createPartContainer(player, partLocator(x, y, z), ID, EnergyLevelEmitterPart.class,
                    host -> new ContainerEnergyLevelEmitter(player.inventory, host));
            }
            case STORAGE_LEVEL_EMITTER -> {
                return createPartContainer(player, partLocator(x, y, z), ID, StorageLevelEmitterPart.class,
                    host -> new ContainerStorageLevelEmitter(player.inventory, host));
            }
            case ME_STORAGE_TERMINAL -> {
                return createPartContainer(player, partLocator(x, y, z), ID, ItemTerminalPart.class,
                    host -> new ContainerMEStorage(GuiIds.GuiKey.ME_STORAGE_TERMINAL, player.inventory, host));
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
            case QUARTZ_KNIFE -> {
                return createQuartzKnifeContainer(player, x, ID);
            }
            case NETWORK_TOOL -> {
                return createNetworkToolContainer(player, x, ID);
            }
            case NETWORK_STATUS -> {
                return createNetworkStatusContainer(player, y >> 8, new BlockPos(x, y & 255, z), ID);
            }
            case PORTABLE_ITEM_CELL -> {
                return createPortableItemCellContainer(player, x, ID);
            }
            case PORTABLE_FLUID_CELL -> {
                return createPortableFluidCellContainer(player, x, ID);
            }
            case WIRELESS_TERMINAL -> {
                return createWirelessTerminalContainer(player, x, ID);
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
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
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
                    return new GuiDrive(container, player.inventory, ((appeng.tile.AEBaseTile) te).getCustomName(),
                        GuiStyleManager.loadStyleDoc("/screens/drive.json"));
                }
            }
            case CELL_WORKBENCH -> {
                if (te instanceof TileCellWorkbench) {
                    ContainerCellWorkbench container = initTileContainer(new ContainerCellWorkbench(player.inventory,
                        (TileCellWorkbench) te), te, ID);
                    return new GuiCellWorkbench(container, player.inventory,
                        ((appeng.tile.AEBaseTile) te).getCustomName(),
                        GuiStyleManager.loadStyleDoc("/screens/cell_workbench.json"));
                }
            }
            case CONDENSER -> {
                if (te instanceof TileCondenser) {
                    ContainerCondenser container = initTileContainer(new ContainerCondenser(player.inventory,
                        (TileCondenser) te), te, ID);
                    return new GuiCondenser(container, player.inventory,
                        ((appeng.tile.AEBaseTile) te).getCustomName(),
                        GuiStyleManager.loadStyleDoc("/screens/condenser.json"));
                }
            }
            case SKY_CHEST -> {
                if (te instanceof TileSkyChest) {
                    ContainerSkyChest container = initTileContainer(new ContainerSkyChest(player.inventory,
                        (TileSkyChest) te), te, ID);
                    return new GuiSkyChest(container, player.inventory,
                        ((appeng.tile.AEBaseTile) te).getCustomName(),
                        GuiStyleManager.loadStyleDoc("/screens/sky_chest.json"));
                }
            }
            case INSCRIBER -> {
                if (te instanceof TileInscriber) {
                    ContainerInscriber container = initTileContainer(new ContainerInscriber(player.inventory,
                        (TileInscriber) te), te, ID);
                    return new GuiInscriber(container, player.inventory,
                        ((appeng.tile.AEBaseTile) te).getCustomName(),
                        GuiStyleManager.loadStyleDoc("/screens/inscriber.json"));
                }
            }
            case IO_PORT -> {
                if (te instanceof TileIOPort) {
                    ContainerIOPort container = initTileContainer(new ContainerIOPort(player.inventory,
                        (TileIOPort) te), te, ID);
                    return new GuiIOPort(container, player.inventory,
                        ((appeng.tile.AEBaseTile) te).getCustomName(),
                        GuiStyleManager.loadStyleDoc("/screens/io_port.json"));
                }
            }
            case MOLECULAR_ASSEMBLER -> {
                if (te instanceof TileMolecularAssembler) {
                    ContainerMolecularAssembler container = initTileContainer(
                        new ContainerMolecularAssembler(player.inventory, (TileMolecularAssembler) te), te, ID);
                    return new GuiMolecularAssembler(container, player.inventory,
                        ((appeng.tile.AEBaseTile) te).getCustomName(),
                        GuiStyleManager.loadStyleDoc("/screens/molecular_assembler.json"));
                }
            }
            case VIBRATION_CHAMBER -> {
                if (te instanceof TileVibrationChamber) {
                    ContainerVibrationChamber container = initTileContainer(new ContainerVibrationChamber(
                        player.inventory, (TileVibrationChamber) te), te, ID);
                    return new GuiVibrationChamber(container, player.inventory,
                        ((appeng.tile.AEBaseTile) te).getCustomName(),
                        GuiStyleManager.loadStyleDoc("/screens/vibration_chamber.json"));
                }
            }
            case QNB -> {
                if (te instanceof TileQuantumBridge) {
                    ContainerQNB container = initTileContainer(new ContainerQNB(player.inventory,
                        (TileQuantumBridge) te), te, ID);
                    return new GuiQNB(container, player.inventory,
                        ((appeng.tile.AEBaseTile) te).getCustomName(),
                        GuiStyleManager.loadStyleDoc("/screens/qnb.json"));
                }
            }
            case WIRELESS_ACCESS_POINT -> {
                if (te instanceof TileWirelessAccessPoint) {
                    ContainerWirelessAccessPoint container = initTileContainer(new ContainerWirelessAccessPoint(
                        player.inventory, (TileWirelessAccessPoint) te), te, ID);
                    return new GuiWirelessAccessPoint(container, player.inventory,
                        ((appeng.tile.AEBaseTile) te).getCustomName(),
                        GuiStyleManager.loadStyleDoc("/screens/wireless_access_point.json"));
                }
            }
            case SPATIAL_IO_PORT -> {
                if (te instanceof TileSpatialIOPort) {
                    ContainerSpatialIOPort container = initTileContainer(new ContainerSpatialIOPort(player.inventory,
                        (TileSpatialIOPort) te), te, ID);
                    return new GuiSpatialIOPort(container, player.inventory,
                        ((appeng.tile.AEBaseTile) te).getCustomName(),
                        GuiStyleManager.loadStyleDoc("/screens/spatial_io_port.json"));
                }
            }
            case SPATIAL_ANCHOR -> {
                if (te instanceof TileSpatialAnchor) {
                    ContainerSpatialAnchor container = initTileContainer(new ContainerSpatialAnchor(player.inventory,
                        (TileSpatialAnchor) te), te, ID);
                    return new GuiSpatialAnchor(container, player.inventory,
                        ((appeng.tile.AEBaseTile) te).getCustomName(),
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
            case CRAFTING_CPU -> {
                if (te instanceof TileCraftingUnit craftingUnit) {
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
                    ExportBusPart.class, host -> new ContainerIOBus(player.inventory, host));
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

    private @Nullable ContainerNetworkStatus createNetworkStatusContainer(EntityPlayer player, int slot, BlockPos pos, int guiId) {
        ItemGuiHostLocator locator = GuiHostLocators.forInventorySlot(slot,
            GuiHostLocators.createItemUseHitResult(pos, net.minecraft.util.EnumFacing.UP, 0.5f, 0.5f, 0.5f));
        NetworkToolGuiHost<?> host = createNetworkToolGuiHost(player, locator);
        if (host == null || host.getGridHost() == null) {
            return null;
        }

        return initContainer(new ContainerNetworkStatus(player.inventory, host), locator, guiId);
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

    private @Nullable ContainerMEStorage createPortableFluidCellContainer(EntityPlayer player, int slot, int guiId) {
        ItemGuiHostLocator locator = GuiHostLocators.forInventorySlot(slot);
        IPortableTerminal host = createPortableTerminalHost(player, locator);
        if (host == null) {
            return null;
        }

        return initContainer(new ContainerMEStorage(GuiIds.GuiKey.PORTABLE_FLUID_CELL, player.inventory, host),
            locator, guiId);
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
        return createItemGuiHost(player, locator, null);
    }

    private @Nullable ItemGuiHost<?> createItemGuiHost(EntityPlayer player, ItemGuiHostLocator locator,
                                                       GuiIds.GuiKey requestedGui) {
        Integer slot = locator.getPlayerInventorySlot();
        if (slot == null || slot < 0 || slot >= player.inventory.getSizeInventory()) {
            return null;
        }

        ItemStack stack = player.inventory.getStackInSlot(slot);
        if (stack.isEmpty() || !(stack.getItem() instanceof IGuiItem guiItem)) {
            return null;
        }

        selectUniversalTerminalForGui(stack, requestedGui);
        return guiItem.getGuiHost(player, locator, locator.hitResult());
    }

    private void selectUniversalTerminalForGui(ItemStack stack, GuiIds.GuiKey requestedGui) {
        if (requestedGui == null || !(stack.getItem() instanceof WirelessUniversalTerminalItem universalTerminal)) {
            return;
        }
        WirelessTerminalRegistry.allDefinitions()
                                .stream()
                                .filter(definition -> definition.item().getGuiKey(stack) == requestedGui)
                                .findFirst()
                                .ifPresent(definition -> universalTerminal.selectTerminal(stack, definition.id()));
    }
}
