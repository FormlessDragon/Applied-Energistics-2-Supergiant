package ae2.api.cellterminal;

import net.minecraft.util.ResourceLocation;

/**
 * Resolves a previously scanned storage-bus style target locator back to a live target.
 * <p>
 * External storage-bus implementations register this resolver so Cell Terminal server actions can re-resolve the
 * current world object before mutating filters, upgrades, priority or IO settings.
 */
public interface CellTerminalBusTargetResolver {
    /**
     * Returns the locator kind handled by this resolver.
     *
     * @return The stable locator kind id.
     */
    ResourceLocation getKindId();

    /**
     * Resolves the locator to a current live storage-bus target.
     *
     * @param locator The locator captured during scanning.
     * @return The live target.
     */
    CellTerminalBusTarget resolveBusTarget(CellTerminalTargetLocator locator);
}
