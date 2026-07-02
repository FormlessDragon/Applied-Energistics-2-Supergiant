package ae2.cellterminal.server;

import ae2.api.cellterminal.CellTerminalTargetLocator;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Per-target failure detail returned by preview and execute actions.
 *
 * @param stableTargetId Stable target id involved in the failure when available.
 * @param locator        Target locator involved in the failure when available.
 * @param slotIndex      Cell slot index for cell actions, or {@code -1} for target-level actions.
 * @param reason         Machine-readable reason string.
 * @param message        Human-readable diagnostic detail.
 */
public record CellTerminalActionFailure(@Nullable String stableTargetId, @Nullable CellTerminalTargetLocator locator,
                                        int slotIndex, String reason, String message) {
    public CellTerminalActionFailure {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(message, "message");
    }

    /**
     * Creates a target-level failure.
     *
     * @param handle  Target handle.
     * @param reason  Machine-readable reason string.
     * @param message Human-readable diagnostic detail.
     * @return Failure detail.
     */
    public static CellTerminalActionFailure target(CellTerminalTargetHandle handle, String reason, String message) {
        Objects.requireNonNull(handle, "handle");
        return new CellTerminalActionFailure(handle.stableTargetId(), handle.locator(), -1, reason, message);
    }

    /**
     * Creates a cell-slot failure.
     *
     * @param handle  Cell slot handle.
     * @param reason  Machine-readable reason string.
     * @param message Human-readable diagnostic detail.
     * @return Failure detail.
     */
    public static CellTerminalActionFailure cellSlot(CellTerminalCellSlotHandle handle, String reason, String message) {
        Objects.requireNonNull(handle, "handle");
        return new CellTerminalActionFailure(
            handle.stableTargetId(),
            handle.locator(),
            handle.slotIndex(),
            reason,
            message);
    }
}
