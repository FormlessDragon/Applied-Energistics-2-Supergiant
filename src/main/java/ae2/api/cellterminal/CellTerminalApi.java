package ae2.api.cellterminal;

import ae2.cellterminal.internal.CellTerminalRegistry;

import java.util.List;

/**
 * Public registration and lookup entry point for Cell Terminal scanning.
 * <p>
 * Registration happens during common setup and server-side scan orchestration reads the immutable snapshots exposed by
 * the corresponding accessors.
 */
public final class CellTerminalApi {
    private CellTerminalApi() {
    }

    /**
     * Registers a direct storage scanner.
     *
     * @param scanner The scanner to register.
     */
    public static void registerStorageScanner(CellTerminalScanner.Storage scanner) {
        CellTerminalRegistry.registerStorageScanner(scanner);
    }

    /**
     * Registers a storage-bus scanner.
     *
     * @param scanner The scanner to register.
     */
    public static void registerStorageBusScanner(CellTerminalScanner.Bus scanner) {
        CellTerminalRegistry.registerStorageBusScanner(scanner);
    }

    /**
     * Registers a subnet scanner.
     *
     * @param scanner The scanner to register.
     */
    public static void registerSubnetScanner(CellTerminalScanner.Subnet scanner) {
        CellTerminalRegistry.registerSubnetScanner(scanner);
    }

    /**
     * Registers a live resolver for one storage target locator kind.
     * <p>
     * External scanners that expose writable targets must register the matching resolver so server-side GUI actions can
     * re-resolve the current world object before mutating it.
     *
     * @param resolver The resolver to register.
     */
    public static void registerStorageTargetResolver(CellTerminalStorageTargetResolver resolver) {
        CellTerminalRegistry.registerStorageTargetResolver(resolver);
    }

    /**
     * Registers a live resolver for one storage-bus target locator kind.
     * <p>
     * External scanners that expose writable bus targets must register the matching resolver so server-side GUI actions
     * can re-resolve the current world object before mutating it.
     *
     * @param resolver The resolver to register.
     */
    public static void registerBusTargetResolver(CellTerminalBusTargetResolver resolver) {
        CellTerminalRegistry.registerBusTargetResolver(resolver);
    }

    /**
     * Registers a live resolver for one subnet target locator kind.
     * <p>
     * External subnet scanners must register the matching resolver so subnet load and shared subnet metadata writes can
     * validate the current live anchor.
     *
     * @param resolver The resolver to register.
     */
    public static void registerSubnetTargetResolver(CellTerminalSubnetTargetResolver resolver) {
        CellTerminalRegistry.registerSubnetTargetResolver(resolver);
    }

    /**
     * Returns the live storage target resolver registered for the locator kind.
     * <p>
     * External Cell Terminal integrations use this accessor when they need the same fail-fast resolver lookup used by AE2
     * server actions while keeping their code on the public {@code ae2.api.cellterminal} surface. The locator kind selects
     * the resolver that can re-resolve a previously scanned storage target.
     *
     * @param locator The locator captured during scanning.
     * @return The resolver registered for the locator kind.
     */
    public static CellTerminalStorageTargetResolver getStorageTargetResolver(CellTerminalTargetLocator locator) {
        return CellTerminalRegistry.getStorageTargetResolver(locator);
    }

    /**
     * Returns the live storage-bus target resolver registered for the locator kind.
     * <p>
     * External Cell Terminal integrations use this accessor to reuse AE2's resolver registry for writable storage-bus
     * targets without depending on internal server packages. The locator kind selects the resolver that can re-resolve the
     * currently live bus target.
     *
     * @param locator The locator captured during scanning.
     * @return The resolver registered for the locator kind.
     */
    public static CellTerminalBusTargetResolver getBusTargetResolver(CellTerminalTargetLocator locator) {
        return CellTerminalRegistry.getBusTargetResolver(locator);
    }

    /**
     * Returns the live subnet target resolver registered for the locator kind.
     * <p>
     * External Cell Terminal integrations use this accessor to validate or load subnet targets through the same public
     * resolver contracts that AE2 uses for server-side subnet actions. The locator kind selects the resolver that owns the
     * live subnet anchor.
     *
     * @param locator The locator captured during scanning.
     * @return The resolver registered for the locator kind.
     */
    public static CellTerminalSubnetTargetResolver getSubnetTargetResolver(CellTerminalTargetLocator locator) {
        return CellTerminalRegistry.getSubnetTargetResolver(locator);
    }

    /**
     * Resolves and validates a live storage target from a stable target id and locator captured during scanning.
     * <p>
     * This is the public fail-fast facade for mods that need to perform Cell Terminal writes or reads against the current
     * world object. The resolved target must report the same stable id and locator, which prevents stale scan snapshots
     * from mutating a replaced block, part or external device.
     *
     * @param stableTargetId Stable target id captured during scanning.
     * @param locator        Locator captured during scanning.
     * @return The validated live storage target.
     */
    public static CellTerminalStorageTarget resolveStorageTarget(String stableTargetId,
                                                                 CellTerminalTargetLocator locator) {
        return CellTerminalRegistry.resolveStorageTarget(stableTargetId, locator);
    }

    /**
     * Resolves and validates a live storage-bus target from a stable target id and locator captured during scanning.
     * <p>
     * This is the public fail-fast facade for mods that need to reuse the storage-bus resolver path exposed by Cell
     * Terminal. The resolved target must report the same stable id and locator so bus partition, upgrade and mode writes
     * apply only to the current matching live object.
     *
     * @param stableTargetId Stable target id captured during scanning.
     * @param locator        Locator captured during scanning.
     * @return The validated live storage-bus target.
     */
    public static CellTerminalBusTarget resolveBusTarget(String stableTargetId, CellTerminalTargetLocator locator) {
        return CellTerminalRegistry.resolveBusTarget(stableTargetId, locator);
    }

    /**
     * Resolves and validates a live subnet target from stable ids and a locator captured during scanning.
     * <p>
     * This is the public fail-fast facade for mods that need to load or inspect the current subnet target through Cell
     * Terminal's resolver registry. The resolved target must report the same stable target id, subnet id and locator so
     * shared subnet state is attached to the intended live anchor.
     *
     * @param stableTargetId Stable target id captured during scanning.
     * @param subnetId       Stable subnet id captured during scanning.
     * @param locator        Locator captured during scanning.
     * @return The validated live subnet target.
     */
    public static CellTerminalSubnetTarget resolveSubnetTarget(String stableTargetId, String subnetId,
                                                               CellTerminalTargetLocator locator) {
        return CellTerminalRegistry.resolveSubnetTarget(stableTargetId, subnetId, locator);
    }

    /**
     * Returns the immutable snapshot of registered direct storage scanners.
     *
     * @return The current direct storage scanners.
     */
    public static List<CellTerminalScanner.Storage> getStorageScanners() {
        return CellTerminalRegistry.getStorageScanners();
    }

    /**
     * Returns the immutable snapshot of registered storage-bus scanners.
     *
     * @return The current storage-bus scanners.
     */
    public static List<CellTerminalScanner.Bus> getStorageBusScanners() {
        return CellTerminalRegistry.getStorageBusScanners();
    }

    /**
     * Returns the immutable snapshot of registered subnet scanners.
     *
     * @return The current subnet scanners.
     */
    public static List<CellTerminalScanner.Subnet> getSubnetScanners() {
        return CellTerminalRegistry.getSubnetScanners();
    }

}
