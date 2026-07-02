package ae2.cellterminal.internal;

import ae2.api.cellterminal.CellTerminalTargetLocator;
import ae2.api.parts.PartHelper;
import ae2.core.AppEng;
import ae2.helpers.InterfaceLogicHost;
import ae2.parts.misc.InterfacePart;
import ae2.parts.storagebus.StorageBusPart;
import ae2.tile.misc.TileInterface;
import ae2.tile.storage.TileDrive;
import ae2.tile.storage.TileMEChest;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class CellTerminalTargetResolver {
    public static final ResourceLocation DRIVE_KIND = AppEng.makeId("cell_terminal/drive");
    public static final ResourceLocation ME_CHEST_KIND = AppEng.makeId("cell_terminal/me_chest");
    public static final ResourceLocation STORAGE_BUS_KIND = AppEng.makeId("cell_terminal/storage_bus");
    public static final ResourceLocation INTERFACE_TILE_KIND = AppEng.makeId("cell_terminal/interface_tile");
    public static final ResourceLocation INTERFACE_PART_KIND = AppEng.makeId("cell_terminal/interface_part");

    private CellTerminalTargetResolver() {
    }

    static @Nullable TileDrive resolveDrive(CellTerminalTargetLocator locator) {
        requireKind(locator, DRIVE_KIND);
        TileEntity tile = getWorld(locator).getTileEntity(locator.pos());
        return tile instanceof TileDrive drive ? drive : null;
    }

    public static TileDrive requireDrive(CellTerminalTargetLocator locator) {
        TileDrive drive = resolveDrive(locator);
        if (drive == null) {
            throw new IllegalStateException("Failed to resolve Cell Terminal drive for locator: " + locator);
        }
        return drive;
    }

    static @Nullable TileMEChest resolveMEChest(CellTerminalTargetLocator locator) {
        requireKind(locator, ME_CHEST_KIND);
        TileEntity tile = getWorld(locator).getTileEntity(locator.pos());
        return tile instanceof TileMEChest chest ? chest : null;
    }

    public static TileMEChest requireMEChest(CellTerminalTargetLocator locator) {
        TileMEChest chest = resolveMEChest(locator);
        if (chest == null) {
            throw new IllegalStateException("Failed to resolve Cell Terminal ME chest for locator: " + locator);
        }
        return chest;
    }

    public static @Nullable StorageBusPart resolveStorageBus(CellTerminalTargetLocator locator) {
        requireKind(locator, STORAGE_BUS_KIND);
        EnumFacing side = Objects.requireNonNull(locator.side(), "Storage bus locator requires side");
        var part = PartHelper.getPart(getWorld(locator), locator.pos(), side);
        return part instanceof StorageBusPart storageBus ? storageBus : null;
    }

    public static StorageBusPart requireStorageBus(CellTerminalTargetLocator locator) {
        StorageBusPart storageBus = resolveStorageBus(locator);
        if (storageBus == null) {
            throw new IllegalStateException("Failed to resolve Cell Terminal storage bus for locator: " + locator);
        }
        return storageBus;
    }

    static @Nullable TileInterface resolveInterfaceTile(CellTerminalTargetLocator locator) {
        requireKind(locator, INTERFACE_TILE_KIND);
        TileEntity tile = getWorld(locator).getTileEntity(locator.pos());
        return tile instanceof TileInterface tileInterface ? tileInterface : null;
    }

    static @Nullable InterfacePart resolveInterfacePart(CellTerminalTargetLocator locator) {
        requireKind(locator, INTERFACE_PART_KIND);
        EnumFacing side = Objects.requireNonNull(locator.side(), "Interface part locator requires side");
        var part = PartHelper.getPart(getWorld(locator), locator.pos(), side);
        return part instanceof InterfacePart interfacePart ? interfacePart : null;
    }

    static InterfaceLogicHost requireInterfaceHost(CellTerminalTargetLocator locator) {
        TileInterface tileInterface = resolveInterfaceTile(locator);
        if (tileInterface != null) {
            return tileInterface;
        }

        InterfacePart interfacePart = resolveInterfacePart(locator);
        if (interfacePart != null) {
            return interfacePart;
        }

        throw new IllegalStateException("Failed to resolve Cell Terminal interface host for locator: " + locator);
    }

    private static WorldServer getWorld(CellTerminalTargetLocator locator) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            throw new IllegalStateException("Cell Terminal target resolution requires a running server");
        }

        WorldServer level = server.getWorld(locator.dimensionId());
        if (level == null) {
            level = DimensionManager.getWorld(locator.dimensionId());
        }
        if (level == null) {
            throw new IllegalStateException("Missing world for Cell Terminal target locator: " + locator);
        }
        return level;
    }

    private static void requireKind(CellTerminalTargetLocator locator, ResourceLocation expectedKind) {
        if (!expectedKind.equals(locator.kindId())) {
            throw new IllegalArgumentException(
                "Expected locator kind " + expectedKind + " but got " + locator.kindId());
        }
    }
}
