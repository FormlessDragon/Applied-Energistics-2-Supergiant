package ae2.api.cellterminal;

import net.minecraft.util.ResourceLocation;

/**
 * Resolves a previously scanned subnet target locator back to a live subnet target.
 * <p>
 * External subnet bridges register this resolver so Cell Terminal can validate a live subnet anchor before loading or
 * mutating shared subnet metadata.
 */
public interface CellTerminalSubnetTargetResolver {
    /**
     * Returns the locator kind handled by this resolver.
     *
     * @return The stable locator kind id.
     */
    ResourceLocation getKindId();

    /**
     * Resolves the locator to a current live subnet target.
     *
     * @param stableTargetId Stable target id captured during scanning.
     * @param locator        Locator captured during scanning.
     * @return The live target.
     */
    CellTerminalSubnetTarget resolveSubnetTarget(String stableTargetId, CellTerminalTargetLocator locator);
}
