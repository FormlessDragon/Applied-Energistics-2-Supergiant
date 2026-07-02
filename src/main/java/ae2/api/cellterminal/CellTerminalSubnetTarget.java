package ae2.api.cellterminal;

import ae2.api.networking.IGrid;

import java.util.List;

/**
 * Read-only target that describes one subnet reachable from the currently active Cell Terminal network.
 * <p>
 * Subnet targets expose the stable identity, display metadata and connection endpoints required by future subnet
 * overview, switching and editing flows. Resolving a subnet asks the implementation to validate the live anchor before
 * returning the current subnet grid.
 */
public non-sealed interface CellTerminalSubnetTarget extends CellTerminalTarget {
    /**
     * Returns the stable subnet identifier used by GUI state, player preferences and future switch actions.
     *
     * @return The stable subnet identifier.
     */
    String subnetId();

    /**
     * Returns the ordered list of connections through which this subnet is reachable.
     *
     * @return The currently visible subnet connections with their flow direction.
     */
    List<CellTerminalSubnetConnection> getConnections();

    /**
     * Resolves and validates the current subnet grid for this target.
     *
     * @return The resolved subnet grid.
     */
    IGrid resolveSubnet();
}
