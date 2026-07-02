package ae2.cellterminal.server;

/**
 * Supported Cell Terminal network-tool backend operations.
 */
public enum CellTerminalNetworkToolOperation {
    /**
     * Redistributes unique content types across selected cell partitions.
     */
    UNIQUE_TYPE_REALLOCATION,

    /**
     * Partitions each selected cell from its own contents.
     */
    PARTITION_CELLS_BY_CONTENT,

    /**
     * Partitions each selected storage bus from its visible external contents.
     */
    PARTITION_STORAGE_BUSES_BY_CONTENT
}
