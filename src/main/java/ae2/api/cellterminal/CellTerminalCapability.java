package ae2.api.cellterminal;

/**
 * Public capability flags exposed by Cell Terminal targets.
 * <p>
 * Callers use these flags to decide which pages and actions are available before they invoke the more specific target
 * interface methods. The enum is intentionally descriptive so third-party target providers can expose the same behavior
 * without depending on native AE2 implementation classes.
 */
public enum CellTerminalCapability {
    /**
     * The target can provide a content snapshot for display or action previews.
     */
    CONTENT_PREVIEW,

    /**
     * The target can insert, eject or replace mounted storage cells.
     */
    CELL_SLOT_WRITE,

    /**
     * The target exposes readable and writable partition/filter slots.
     */
    PARTITION_WRITE,

    /**
     * The cell slot supports populating its partition/filter directly from the currently stored content.
     */
    AUTO_PARTITION_FROM_CONTENT,

    /**
     * The target exposes readable and writable text-based partition/filter expressions.
     */
    TEXT_PARTITION_WRITE,

    /**
     * The target exposes partition/filter slots whose configured amount is part of the filter.
     */
    PRECISE_PARTITION_WRITE,

    /**
     * The target exposes readable and writable upgrade slots.
     */
    UPGRADE_WRITE,

    /**
     * The cell slot can safely participate in unique-type resource reallocation.
     * <p>
     * Implementations should expose this only when the mounted cell supports exact simulated insertion, exact
     * extraction and persistent partition changes without destroying, creating or hiding resources during rollback.
     */
    SAFE_UNIQUE_TYPE_REALLOCATION,

    /**
     * The target exposes readable and writable priority.
     */
    PRIORITY_WRITE,

    /**
     * The target exposes readable and writable IO or filtering mode settings.
     */
    IO_FILTER_MODE_WRITE,

    /**
     * The target can resolve or load a reachable subnet.
     */
    SUBNET_RESOLVE
}
