package ae2.cellterminal.internal;

/**
 * Internal constants for the Cell Terminal temporary cell inventory.
 * <p>
 * Keeping the slot count outside the public host API avoids making GUI/container implementation details part of the
 * external Cell Terminal extension contract.
 */
public final class CellTerminalTempCells {
    /**
     * Number of temporary cell slots owned by wired and wireless Cell Terminal hosts.
     */
    public static final int SLOT_COUNT = 16;

    private CellTerminalTempCells() {
    }
}
