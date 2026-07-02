package ae2.api.cellterminal;

/**
 * Describes how a Cell Terminal storage-bus target exposes its partition/filter configuration.
 * <p>
 * Slot based modes are edited through the regular partition slot grid. Text based modes are edited through named text
 * expressions because their native buses do not expose item filter slots.
 */
public enum CellTerminalBusPartitionMode {
    /**
     * A regular type-only storage bus filter inventory.
     */
    SLOTS,

    /**
     * A storage bus filter inventory where each configured stack amount is part of the filter.
     */
    PRECISE_SLOTS,

    /**
     * A single mod-id/name filter expression.
     */
    MOD_EXPRESSION,

    /**
     * A pair of ore dictionary whitelist and blacklist expressions.
     */
    ORE_DICTIONARY_EXPRESSIONS
}
