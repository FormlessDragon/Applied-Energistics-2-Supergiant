package ae2.cellterminal.server;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Result of a server-side subnet navigation or restore action.
 *
 * @param status  Action outcome.
 * @param handle  Subnet handle involved in the action when available.
 * @param message Diagnostic detail.
 */
public record CellTerminalSubnetActionResult(CellTerminalActionStatus status,
                                             @Nullable CellTerminalSubnetHandle handle,
                                             String message) {
    public CellTerminalSubnetActionResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(message, "message");
    }
}
