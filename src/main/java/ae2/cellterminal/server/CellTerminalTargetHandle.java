package ae2.cellterminal.server;

import ae2.api.cellterminal.CellTerminalTargetLocator;

import java.util.Objects;

/**
 * Stable target reference used by GUI-independent server actions.
 *
 * @param stableTargetId Stable target id from the latest scan snapshot.
 * @param locator        World locator used for live target resolution.
 */
public record CellTerminalTargetHandle(String stableTargetId, CellTerminalTargetLocator locator) {
    public CellTerminalTargetHandle {
        stableTargetId = requireText(stableTargetId, "stableTargetId");
        Objects.requireNonNull(locator, "locator");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return value;
    }
}
