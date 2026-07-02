package ae2.api.cellterminal;

import java.util.Objects;

/**
 * Immutable snapshot of text-based storage-bus partition/filter expressions.
 * <p>
 * Mod storage buses use {@link #primaryExpression()}. Ore dictionary storage buses use
 * {@link #primaryExpression()} for the whitelist and {@link #secondaryExpression()} for the blacklist.
 */
public record CellTerminalBusTextPartitionSnapshot(String primaryExpression, String secondaryExpression) {
    public CellTerminalBusTextPartitionSnapshot {
        primaryExpression = Objects.requireNonNullElse(primaryExpression, "");
        secondaryExpression = Objects.requireNonNullElse(secondaryExpression, "");
    }

    public static CellTerminalBusTextPartitionSnapshot empty() {
        return new CellTerminalBusTextPartitionSnapshot("", "");
    }
}
