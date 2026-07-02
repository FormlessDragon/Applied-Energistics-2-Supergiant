package ae2.api.cellterminal;

import net.minecraft.util.ResourceLocation;

/**
 * Resolves a previously scanned storage target locator back to a live target.
 * <p>
 * Scanners expose immutable target snapshots to the Cell Terminal. Any later write action must resolve the current
 * world object again through this interface so external storage devices can participate without depending on AE2
 * native drive, chest or storage-bus classes.
 */
public interface CellTerminalStorageTargetResolver {
    /**
     * Returns the locator kind handled by this resolver.
     *
     * @return The stable locator kind id.
     */
    ResourceLocation getKindId();

    /**
     * Resolves the locator to a current live storage target.
     *
     * @param locator The locator captured during scanning.
     * @return The live target.
     */
    CellTerminalStorageTarget resolveStorageTarget(CellTerminalTargetLocator locator);
}
