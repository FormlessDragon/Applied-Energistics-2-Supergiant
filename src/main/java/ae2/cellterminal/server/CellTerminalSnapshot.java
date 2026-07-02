package ae2.cellterminal.server;

import ae2.api.cellterminal.CellTerminalBusTarget;
import ae2.api.cellterminal.CellTerminalStorageTarget;
import ae2.api.cellterminal.CellTerminalSubnetTarget;

import java.util.List;
import java.util.Objects;

/**
 * Immutable GUI-independent scan snapshot for one effective Cell Terminal grid.
 *
 * @param contextId      Session context id that produced this snapshot.
 * @param cacheRevision  Session cache revision observed when the snapshot was created.
 * @param storageTargets Direct storage targets.
 * @param busTargets     Storage-bus targets.
 * @param subnetTargets  Reachable subnet targets.
 */
public record CellTerminalSnapshot(String contextId, long cacheRevision,
                                   List<CellTerminalStorageTarget> storageTargets,
                                   List<CellTerminalBusTarget> busTargets,
                                   List<CellTerminalSubnetTarget> subnetTargets) {
    public CellTerminalSnapshot {
        Objects.requireNonNull(contextId, "contextId");
        storageTargets = List.copyOf(Objects.requireNonNull(storageTargets, "storageTargets"));
        busTargets = List.copyOf(Objects.requireNonNull(busTargets, "busTargets"));
        subnetTargets = List.copyOf(Objects.requireNonNull(subnetTargets, "subnetTargets"));
    }
}
