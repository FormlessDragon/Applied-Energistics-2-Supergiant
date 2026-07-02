package ae2.cellterminal.server;

/**
 * Overall result status for a GUI-independent Cell Terminal action.
 */
public enum CellTerminalActionStatus {
    /**
     * Every planned target was applied.
     */
    SUCCESS,

    /**
     * At least one target was applied and at least one target failed.
     */
    PARTIAL_FAILURE,

    /**
     * No planned target was applied.
     */
    FAILURE
}
