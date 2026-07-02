package ae2.cellterminal.internal;

import ae2.api.cellterminal.CellTerminalApi;
import ae2.api.cellterminal.CellTerminalBusTarget;
import ae2.api.cellterminal.CellTerminalBusTargetResolver;
import ae2.api.cellterminal.CellTerminalStorageTarget;
import ae2.api.cellterminal.CellTerminalStorageTargetResolver;
import ae2.api.cellterminal.CellTerminalSubnetTarget;
import ae2.api.cellterminal.CellTerminalSubnetTargetResolver;
import ae2.api.cellterminal.CellTerminalTargetLocator;
import net.minecraft.util.ResourceLocation;

public final class InitCellTerminalApi {
    private static boolean initialized;

    private InitCellTerminalApi() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }

        CellTerminalApi.registerStorageScanner(new DriveStorageScanner());
        CellTerminalApi.registerStorageScanner(new MEChestStorageScanner());
        CellTerminalApi.registerStorageBusScanner(new StorageBusScanner());
        CellTerminalApi.registerSubnetScanner(new InterfaceSubnetScanner());
        registerNativeStorageTargetResolver(CellTerminalTargetResolver.DRIVE_KIND);
        registerNativeStorageTargetResolver(CellTerminalTargetResolver.ME_CHEST_KIND);
        registerNativeStorageTargetResolver(CellTerminalTargetResolver.STORAGE_BUS_KIND);
        CellTerminalApi.registerBusTargetResolver(new NativeBusTargetResolver(CellTerminalTargetResolver.STORAGE_BUS_KIND));
        CellTerminalApi.registerSubnetTargetResolver(new NativeSubnetTargetResolver(CellTerminalTargetResolver.INTERFACE_TILE_KIND));
        CellTerminalApi.registerSubnetTargetResolver(new NativeSubnetTargetResolver(CellTerminalTargetResolver.INTERFACE_PART_KIND));

        initialized = true;
    }

    private static void registerNativeStorageTargetResolver(ResourceLocation kindId) {
        CellTerminalApi.registerStorageTargetResolver(new NativeStorageTargetResolver(kindId));
    }

    private record NativeStorageTargetResolver(ResourceLocation kindId) implements CellTerminalStorageTargetResolver {

        @Override
        public ResourceLocation getKindId() {
            return this.kindId;
        }

        @Override
        public CellTerminalStorageTarget resolveStorageTarget(CellTerminalTargetLocator locator) {
            return NativeCellTerminalTargets.resolveStorageTarget(locator);
        }
    }

    private record NativeBusTargetResolver(ResourceLocation kindId) implements CellTerminalBusTargetResolver {

        @Override
        public ResourceLocation getKindId() {
            return this.kindId;
        }

        @Override
        public CellTerminalBusTarget resolveBusTarget(CellTerminalTargetLocator locator) {
            return NativeCellTerminalTargets.resolveStorageBusTarget(locator);
        }
    }

    private record NativeSubnetTargetResolver(ResourceLocation kindId) implements CellTerminalSubnetTargetResolver {

        @Override
        public ResourceLocation getKindId() {
            return this.kindId;
        }

        @Override
        public CellTerminalSubnetTarget resolveSubnetTarget(String stableTargetId, CellTerminalTargetLocator locator) {
            return NativeCellTerminalTargets.resolveSubnetTarget(stableTargetId, locator);
        }
    }
}
