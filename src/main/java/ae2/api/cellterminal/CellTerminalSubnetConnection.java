package ae2.api.cellterminal;

import java.util.Objects;

/**
 * One connection through which a subnet is reachable from the active Cell Terminal network.
 * <p>
 * Each connection wraps the storage-bus target that bridges the two grids together with the direction the storage flows
 * relative to the main network. {@code outbound} is {@code true} when the storage bus lives on the main network and
 * exposes a subnet behind an interface, and {@code false} when the storage bus lives on the subnet and feeds back into
 * a main-network interface.
 */
public record CellTerminalSubnetConnection(CellTerminalBusTarget target, boolean outbound) {
    public CellTerminalSubnetConnection {
        Objects.requireNonNull(target, "target");
    }
}
