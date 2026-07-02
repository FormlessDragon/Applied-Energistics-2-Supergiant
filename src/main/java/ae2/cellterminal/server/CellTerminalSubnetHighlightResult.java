package ae2.cellterminal.server;

import java.util.Objects;

/**
 * Server-side feedback describing the latest subnet highlight request outcome.
 *
 * @param handle  Target subnet handle.
 * @param status  Request outcome.
 * @param message Diagnostic feedback for logs and future UI messaging.
 */
public record CellTerminalSubnetHighlightResult(CellTerminalSubnetHandle handle,
                                                CellTerminalActionStatus status,
                                                String message) {
    public CellTerminalSubnetHighlightResult {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(message, "message");
    }
}
