package ae2.cellterminal.server;

import ae2.api.cellterminal.CellTerminalTargetLocator;

/**
 * Stable reference to one storage cell slot on a scanned storage target.
 *
 * @param stableTargetId Stable id of the owning storage target.
 * @param locator        World locator of the owning storage target.
 * @param slotIndex      Native slot index inside the owning storage target.
 */
public record CellTerminalCellSlotHandle(String stableTargetId, CellTerminalTargetLocator locator, int slotIndex) {
    public CellTerminalCellSlotHandle {
        if (slotIndex < 0) {
            throw new IllegalArgumentException("slotIndex must be >= 0");
        }
        new CellTerminalTargetHandle(stableTargetId, locator);
    }

    /**
     * Returns this slot handle as an owner target handle.
     *
     * @return Owner target handle.
     */
    public CellTerminalTargetHandle owner() {
        return new CellTerminalTargetHandle(stableTargetId, locator);
    }
}
