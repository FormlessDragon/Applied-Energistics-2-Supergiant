package ae2.api.cellterminal;

import java.util.List;

/**
 * Storage target for anything that the Cell Terminal can list as a direct storage owner.
 * <p>
 * Implementations may expose mounted cell slots, content previews and priority writes. Native implementations re-resolve
 * the live world object before every write to keep stale snapshots from mutating a replaced block entity.
 * <p>
 * CellTerminalBusTarget extends this interface, so it's permitted transitively.
 */
public non-sealed interface CellTerminalStorageTarget extends CellTerminalTarget {
    /**
     * Returns the priority currently assigned to this storage target.
     *
     * @return The target priority.
     */
    int getPriority();

    /**
     * Writes the target priority.
     *
     * @param priority The new priority.
     */
    void setPriority(int priority);

    /**
     * Returns the current number of cell slots owned by this storage target.
     *
     * @return The total number of cell slots.
     */
    int getCellSlotCount();

    /**
     * Returns the current number of mounted cells inside this storage target.
     *
     * @return The current number of mounted cells.
     */
    int getMountedCellCount();

    /**
     * Returns the current cell slots exposed by this storage target.
     * <p>
     * The returned list is read-only and ordered by the underlying native slot indices.
     *
     * @return The currently exposed cell slot targets.
     */
    List<? extends CellTerminalCellSlotTarget> getCellSlots();

    /**
     * Returns a deterministic content preview aggregated from readable mounted cells.
     *
     * @return The current target content snapshot.
     */
    CellTerminalContentSnapshot previewContent();
}
