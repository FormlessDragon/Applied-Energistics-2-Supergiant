package ae2.cellterminal.internal;

import ae2.api.cellterminal.CellTerminalBusTarget;
import ae2.api.cellterminal.CellTerminalBusTargetResolver;
import ae2.api.cellterminal.CellTerminalScanner;
import ae2.api.cellterminal.CellTerminalStorageTarget;
import ae2.api.cellterminal.CellTerminalStorageTargetResolver;
import ae2.api.cellterminal.CellTerminalSubnetTarget;
import ae2.api.cellterminal.CellTerminalSubnetTargetResolver;
import ae2.api.cellterminal.CellTerminalTarget;
import ae2.api.cellterminal.CellTerminalTargetLocator;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.util.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CellTerminalRegistry {
    private static final Map<ResourceLocation, CellTerminalScanner.Storage> STORAGE_SCANNERS =
        new Object2ObjectLinkedOpenHashMap<>();
    private static final Map<ResourceLocation, CellTerminalScanner.Bus> STORAGE_BUS_SCANNERS =
        new Object2ObjectLinkedOpenHashMap<>();
    private static final Map<ResourceLocation, CellTerminalScanner.Subnet> SUBNET_SCANNERS =
        new Object2ObjectLinkedOpenHashMap<>();
    private static final Map<ResourceLocation, CellTerminalStorageTargetResolver> STORAGE_TARGET_RESOLVERS =
        new Object2ObjectLinkedOpenHashMap<>();
    private static final Map<ResourceLocation, CellTerminalBusTargetResolver> BUS_TARGET_RESOLVERS =
        new Object2ObjectLinkedOpenHashMap<>();
    private static final Map<ResourceLocation, CellTerminalSubnetTargetResolver> SUBNET_TARGET_RESOLVERS =
        new Object2ObjectLinkedOpenHashMap<>();

    private static List<CellTerminalScanner.Storage> storageScannerSnapshot = List.of();
    private static List<CellTerminalScanner.Bus> storageBusScannerSnapshot = List.of();
    private static List<CellTerminalScanner.Subnet> subnetScannerSnapshot = List.of();

    private CellTerminalRegistry() {
    }

    public static synchronized void registerStorageScanner(CellTerminalScanner.Storage scanner) {
        storageScannerSnapshot = register("storage scanner", scanner, STORAGE_SCANNERS);
    }

    public static synchronized void registerStorageBusScanner(CellTerminalScanner.Bus scanner) {
        storageBusScannerSnapshot = register("storage bus scanner", scanner, STORAGE_BUS_SCANNERS);
    }

    public static synchronized void registerSubnetScanner(CellTerminalScanner.Subnet scanner) {
        subnetScannerSnapshot = register("subnet scanner", scanner, SUBNET_SCANNERS);
    }

    public static synchronized void registerStorageTargetResolver(CellTerminalStorageTargetResolver resolver) {
        Objects.requireNonNull(resolver, "storage target resolver");
        ResourceLocation kindId = Objects.requireNonNull(resolver.getKindId(), "storage target resolver kind id");
        if (kindId.getPath().isEmpty()) {
            throw new IllegalArgumentException("Cannot register storage target resolver with an empty kind id: " + kindId);
        }
        if (STORAGE_TARGET_RESOLVERS.putIfAbsent(kindId, resolver) != null) {
            throw new IllegalArgumentException("Duplicate storage target resolver registration: " + kindId);
        }
    }

    public static synchronized void registerBusTargetResolver(CellTerminalBusTargetResolver resolver) {
        Objects.requireNonNull(resolver, "bus target resolver");
        ResourceLocation kindId = Objects.requireNonNull(resolver.getKindId(), "bus target resolver kind id");
        if (kindId.getPath().isEmpty()) {
            throw new IllegalArgumentException("Cannot register bus target resolver with an empty kind id: " + kindId);
        }
        if (BUS_TARGET_RESOLVERS.putIfAbsent(kindId, resolver) != null) {
            throw new IllegalArgumentException("Duplicate bus target resolver registration: " + kindId);
        }
    }

    public static synchronized void registerSubnetTargetResolver(CellTerminalSubnetTargetResolver resolver) {
        Objects.requireNonNull(resolver, "subnet target resolver");
        ResourceLocation kindId = Objects.requireNonNull(resolver.getKindId(), "subnet target resolver kind id");
        if (kindId.getPath().isEmpty()) {
            throw new IllegalArgumentException("Cannot register subnet target resolver with an empty kind id: " + kindId);
        }
        if (SUBNET_TARGET_RESOLVERS.putIfAbsent(kindId, resolver) != null) {
            throw new IllegalArgumentException("Duplicate subnet target resolver registration: " + kindId);
        }
    }

    public static synchronized List<CellTerminalScanner.Storage> getStorageScanners() {
        return storageScannerSnapshot;
    }

    public static synchronized List<CellTerminalScanner.Bus> getStorageBusScanners() {
        return storageBusScannerSnapshot;
    }

    public static synchronized List<CellTerminalScanner.Subnet> getSubnetScanners() {
        return subnetScannerSnapshot;
    }

    public static synchronized CellTerminalStorageTargetResolver getStorageTargetResolver(CellTerminalTargetLocator locator) {
        Objects.requireNonNull(locator, "locator");
        CellTerminalStorageTargetResolver resolver = STORAGE_TARGET_RESOLVERS.get(locator.kindId());
        if (resolver == null) {
            CellTerminalWriteSupport.fail("Missing Cell Terminal storage target resolver for locator: %s", locator);
        }
        return resolver;
    }

    public static synchronized CellTerminalBusTargetResolver getBusTargetResolver(CellTerminalTargetLocator locator) {
        Objects.requireNonNull(locator, "locator");
        CellTerminalBusTargetResolver resolver = BUS_TARGET_RESOLVERS.get(locator.kindId());
        if (resolver == null) {
            CellTerminalWriteSupport.fail("Missing Cell Terminal bus target resolver for locator: %s", locator);
        }
        return resolver;
    }

    public static synchronized CellTerminalSubnetTargetResolver getSubnetTargetResolver(CellTerminalTargetLocator locator) {
        Objects.requireNonNull(locator, "locator");
        CellTerminalSubnetTargetResolver resolver = SUBNET_TARGET_RESOLVERS.get(locator.kindId());
        if (resolver == null) {
            CellTerminalWriteSupport.fail("Missing Cell Terminal subnet target resolver for locator: %s", locator);
        }
        return resolver;
    }

    public static CellTerminalStorageTarget resolveStorageTarget(String stableTargetId,
                                                                 CellTerminalTargetLocator locator) {
        Objects.requireNonNull(stableTargetId, "stableTargetId");
        Objects.requireNonNull(locator, "locator");
        CellTerminalStorageTarget liveTarget = getStorageTargetResolver(locator).resolveStorageTarget(locator);
        requireStableTarget("storage target", stableTargetId, locator, liveTarget);
        return liveTarget;
    }

    public static CellTerminalBusTarget resolveBusTarget(String stableTargetId, CellTerminalTargetLocator locator) {
        Objects.requireNonNull(stableTargetId, "stableTargetId");
        Objects.requireNonNull(locator, "locator");
        CellTerminalBusTarget liveTarget = getBusTargetResolver(locator).resolveBusTarget(locator);
        requireStableTarget("bus target", stableTargetId, locator, liveTarget);
        return liveTarget;
    }

    public static CellTerminalSubnetTarget resolveSubnetTarget(String stableTargetId, String subnetId,
                                                               CellTerminalTargetLocator locator) {
        Objects.requireNonNull(stableTargetId, "stableTargetId");
        Objects.requireNonNull(subnetId, "subnetId");
        Objects.requireNonNull(locator, "locator");
        CellTerminalSubnetTarget liveTarget =
            getSubnetTargetResolver(locator).resolveSubnetTarget(stableTargetId, locator);
        requireStableTarget("subnet target", stableTargetId, locator, liveTarget);
        if (!subnetId.equals(liveTarget.subnetId())) {
            CellTerminalWriteSupport.fail(
                "Cell Terminal subnet id mismatch. requested=%s, resolved=%s, locator=%s",
                subnetId,
                liveTarget.subnetId(),
                locator);
        }
        return liveTarget;
    }

    private static <T extends CellTerminalScanner> List<T> register(String registryName, T scanner,
                                                                    Map<ResourceLocation, T> registry) {
        Objects.requireNonNull(scanner, registryName);

        ResourceLocation id = Objects.requireNonNull(scanner.getId(), registryName + " id");
        if (id.getPath().isEmpty()) {
            throw new IllegalArgumentException("Cannot register " + registryName + " with an empty path: " + id);
        }

        if (registry.putIfAbsent(id, scanner) != null) {
            throw new IllegalArgumentException("Duplicate " + registryName + " registration: " + id);
        }

        return List.copyOf(registry.values());
    }

    private static void requireStableTarget(String targetName, String stableTargetId, CellTerminalTargetLocator locator,
                                            CellTerminalTarget liveTarget) {
        if (liveTarget == null) {
            CellTerminalWriteSupport.fail("Cell Terminal %s resolver returned null. stableTargetId=%s, locator=%s",
                targetName, stableTargetId, locator);
        }
        Objects.requireNonNull(liveTarget, "liveTarget");
        if (!stableTargetId.equals(liveTarget.stableTargetId())) {
            CellTerminalWriteSupport.fail(
                "Cell Terminal %s stableTargetId mismatch. requested=%s, resolved=%s, locator=%s",
                targetName,
                stableTargetId,
                liveTarget.stableTargetId(),
                locator);
        }
        if (!locator.equals(liveTarget.locator())) {
            CellTerminalWriteSupport.fail(
                "Cell Terminal %s locator changed during resolve. requested=%s, resolved=%s, target=%s",
                targetName,
                locator,
                liveTarget.locator(),
                stableTargetId);
        }
    }
}
