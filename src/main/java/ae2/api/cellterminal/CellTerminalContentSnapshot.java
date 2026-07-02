package ae2.api.cellterminal;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Immutable content preview exposed by a Cell Terminal target.
 * <p>
 * The snapshot is intended for display, preview tokens and conservative action planning. It is not a live storage view.
 *
 * @param entries         Ordered content entries.
 * @param contentRevision Stable textual digest for this snapshot.
 */
public record CellTerminalContentSnapshot(List<GenericStack> entries, String contentRevision) {
    public CellTerminalContentSnapshot {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        contentRevision = Objects.requireNonNull(contentRevision, "contentRevision");
    }

    /**
     * Builds a deterministic snapshot from an AE key counter.
     *
     * @param counter The source counter to snapshot.
     * @return A deterministic immutable content snapshot.
     */
    public static CellTerminalContentSnapshot fromCounter(KeyCounter counter) {
        Objects.requireNonNull(counter, "counter");

        var sortableEntries = new ArrayList<SortableEntry>(counter.size());
        for (var entry : counter) {
            if (entry.getLongValue() > 0) {
                AEKey key = entry.getKey();
                sortableEntries.add(new SortableEntry(
                    key,
                    entry.getLongValue(),
                    key.getType().getId().toString(),
                    keyId(key),
                    key.toTagGeneric().toString()));
            }
        }
        sortableEntries.sort(Comparator
            .comparing(SortableEntry::typeId)
            .thenComparing(SortableEntry::keyId)
            .thenComparing(SortableEntry::tag)
            .thenComparingLong(SortableEntry::amount));

        var entries = new ArrayList<GenericStack>(sortableEntries.size());
        for (var entry : sortableEntries) {
            entries.add(new GenericStack(entry.key(), entry.amount()));
        }
        return new CellTerminalContentSnapshot(entries, revision(sortableEntries));
    }

    private static String revision(List<SortableEntry> entries) {
        var builder = new StringBuilder(entries.size() * 48);
        for (var entry : entries) {
            builder.append(entry.typeId())
                   .append('|')
                   .append(entry.keyId())
                   .append('|')
                   .append(entry.tag())
                   .append('|')
                   .append(entry.amount())
                   .append('\n');
        }
        return sha256(builder.toString());
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required for Cell Terminal content snapshots", e);
        }
    }

    private static String keyId(AEKey key) {
        @Nullable
        var id = key.getId();
        return id != null ? id.toString() : "";
    }

    /**
     * Returns the first unique stacks in this snapshot, preserving the deterministic snapshot order.
     *
     * @param limit           Maximum number of stacks to return.
     * @param preserveAmounts Whether the returned stacks should keep the source amounts. When false, stack amounts are
     *                        written as {@code 0} for key-only partition filters.
     * @return Ordered unique stacks.
     */
    public List<GenericStack> firstUniqueStacks(int limit, boolean preserveAmounts) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be >= 0");
        }
        if (limit == 0) {
            return List.of();
        }

        var seenKeys = new ObjectLinkedOpenHashSet<AEKey>(Math.min(limit, entries.size()));
        var uniqueStacks = new ArrayList<GenericStack>(Math.min(limit, entries.size()));
        for (var entry : entries) {
            if (seenKeys.add(entry.what())) {
                uniqueStacks.add(new GenericStack(entry.what(), preserveAmounts ? entry.amount() : 0));
                if (uniqueStacks.size() >= limit) {
                    break;
                }
            }
        }
        return List.copyOf(uniqueStacks);
    }

    /**
     * Counts unique keys in this snapshot while preserving {@link #firstUniqueStacks(int, boolean)} equivalence.
     *
     * @return Number of unique keys.
     */
    public int uniqueKeyCount() {
        var uniqueKeys = new ObjectLinkedOpenHashSet<AEKey>(entries.size());
        for (var entry : entries) {
            uniqueKeys.add(entry.what());
        }
        return uniqueKeys.size();
    }

    private record SortableEntry(AEKey key, long amount, String typeId, String keyId, String tag) {
    }
}
