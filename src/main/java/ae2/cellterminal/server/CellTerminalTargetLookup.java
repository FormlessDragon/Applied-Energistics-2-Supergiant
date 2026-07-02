package ae2.cellterminal.server;

import ae2.api.cellterminal.CellTerminalBusTarget;
import ae2.api.cellterminal.CellTerminalCellSlotTarget;
import ae2.api.cellterminal.CellTerminalStorageTarget;

/**
 * Resolves stable Cell Terminal handles into live writable targets.
 * <p>
 * Network-tool backends depend on this interface so preview and execute logic can be tested against deterministic
 * in-memory targets while production keeps using live world resolution.
 */
public interface CellTerminalTargetLookup {
    /**
     * Resolves a storage target handle.
     *
     * @param handle Stable target handle from a previous scan.
     * @return Live storage target.
     */
    CellTerminalStorageTarget resolveStorage(CellTerminalTargetHandle handle);

    /**
     * Resolves a storage-bus target handle.
     *
     * @param handle Stable target handle from a previous scan.
     * @return Live storage-bus target.
     */
    CellTerminalBusTarget resolveStorageBus(CellTerminalTargetHandle handle);

    /**
     * Resolves a cell-slot handle.
     *
     * @param handle Stable cell-slot handle from a previous scan.
     * @return Live cell-slot target.
     */
    CellTerminalCellSlotTarget resolveCellSlot(CellTerminalCellSlotHandle handle);
}
