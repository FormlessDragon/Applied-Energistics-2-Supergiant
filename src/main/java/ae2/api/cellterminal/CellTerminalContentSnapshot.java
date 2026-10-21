package ae2.api.cellterminal;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

/**
 * Immutable content preview exposed by a Cell Terminal target.
 * <p>
 * The snapshot is intended for display, preview tokens and conservative action planning. It is not a live storage view.
 *
 * @param entries         Ordered content entries.
 * @param contentRevision Stable textual digest for this snapshot.
 */
public record CellTerminalContentSnapshot(List<GenericStack> entries, String contentRevision) {
    private static final CellTerminalContentSnapshot EMPTY =
        new CellTerminalContentSnapshot(List.of(), emptyRevision());

    public CellTerminalContentSnapshot {
        Objects.requireNonNull(entries, "entries");
        // fromCounter transfers its privately built list; external constructor inputs still get copied.
        if (!(entries instanceof OwnedEntries)) {
            entries = List.copyOf(entries);
        }
        contentRevision = Objects.requireNonNull(contentRevision, "contentRevision");
    }

    /**
     * Returns the shared empty snapshot.
     */
    public static CellTerminalContentSnapshot empty() {
        return EMPTY;
    }

    /**
     * Builds a deterministic snapshot from an AE key counter.
     *
     * @param counter The source counter to snapshot.
     * @return A deterministic immutable content snapshot.
     */
    public static CellTerminalContentSnapshot fromCounter(KeyCounter counter) {
        Objects.requireNonNull(counter, "counter");
        ObjectArrayList<SortableEntry> sortableEntries = null;
        var serializedTags = new TagBuffer();
        try (var tagOutput = new DataOutputStream(serializedTags)) {
            for (var entry : counter) {
                long amount = entry.getLongValue();
                if (amount > 0) {
                    if (sortableEntries == null) {
                        sortableEntries = new ObjectArrayList<>();
                    }
                    AEKey key = entry.getKey();
                    int tagStart = serializedTags.size();
                    CompressedStreamTools.write(key.toTag(), tagOutput);
                    sortableEntries.add(new SortableEntry(
                        key,
                        amount,
                        key.getType().getId(),
                        key.getId(),
                        tagStart,
                        serializedTags.size() - tagStart));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode Cell Terminal content snapshot", e);
        }
        if (sortableEntries == null) {
            return EMPTY;
        }
        byte[] tagBytes = serializedTags.buffer();
        sortableEntries.sort((left, right) -> compare(left, right, tagBytes));

        var entries = new ObjectArrayList<GenericStack>(sortableEntries.size());
        for (var entry : sortableEntries) {
            entries.add(new GenericStack(entry.key(), entry.amount()));
        }
        return new CellTerminalContentSnapshot(new OwnedEntries(entries), revision(sortableEntries, tagBytes));
    }

    private static int compare(SortableEntry left, SortableEntry right, byte[] tags) {
        int typeOrder = compareResourceLocationString(left.typeId(), right.typeId());
        if (typeOrder != 0) {
            return typeOrder;
        }

        int keyOrder = compareNullableResourceLocationString(left.keyId(), right.keyId());
        if (keyOrder != 0) {
            return keyOrder;
        }

        int tagOrder = compareBytes(tags, left.tagStart(), left.tagLength(), right.tagStart(), right.tagLength());
        return tagOrder != 0 ? tagOrder : Long.compare(left.amount(), right.amount());
    }

    private static int compareResourceLocationString(ResourceLocation left, ResourceLocation right) {
        String leftNamespace = left.getNamespace();
        String leftPath = left.getPath();
        String rightNamespace = right.getNamespace();
        String rightPath = right.getPath();
        int leftLength = leftNamespace.length() + 1 + leftPath.length();
        int rightLength = rightNamespace.length() + 1 + rightPath.length();

        for (int index = 0, length = Math.min(leftLength, rightLength); index < length; index++) {
            char leftChar = resourceLocationChar(leftNamespace, leftPath, index);
            char rightChar = resourceLocationChar(rightNamespace, rightPath, index);
            if (leftChar != rightChar) {
                return Character.compare(leftChar, rightChar);
            }
        }
        return Integer.compare(leftLength, rightLength);
    }

    private static char resourceLocationChar(String namespace, String path, int index) {
        if (index < namespace.length()) {
            return namespace.charAt(index);
        }
        return index == namespace.length() ? ':' : path.charAt(index - namespace.length() - 1);
    }

    private static int compareNullableResourceLocationString(@Nullable ResourceLocation left,
                                                              @Nullable ResourceLocation right) {
        if (left == null) {
            return right == null ? 0 : -1;
        }
        if (right == null) {
            return 1;
        }
        return compareResourceLocationString(left, right);
    }

    private static int compareBytes(byte[] bytes, int leftStart, int leftLength, int rightStart, int rightLength) {
        int commonLength = Math.min(leftLength, rightLength);
        for (int index = 0; index < commonLength; index++) {
            int leftByte = bytes[leftStart + index] & 0xFF;
            int rightByte = bytes[rightStart + index] & 0xFF;
            if (leftByte != rightByte) {
                return Integer.compare(leftByte, rightByte);
            }
        }
        return Integer.compare(leftLength, rightLength);
    }

    private static String emptyRevision() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required for Cell Terminal content snapshots", e);
        }
    }

    private static String revision(List<SortableEntry> entries, byte[] tags) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var output = new DataOutputStream(new DigestOutputStream(
                OutputStream.nullOutputStream(), digest))) {
                for (var entry : entries) {
                    writeResourceLocation(output, entry.typeId());
                    writeOptionalResourceLocation(output, entry.keyId());
                    output.writeInt(entry.tagLength());
                    output.write(tags, entry.tagStart(), entry.tagLength());
                    output.writeLong(entry.amount());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required for Cell Terminal content snapshots", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to hash Cell Terminal content snapshot", e);
        }
    }

    private static void writeResourceLocation(DataOutputStream output, ResourceLocation location) throws IOException {
        output.writeUTF(location.getNamespace());
        output.writeUTF(location.getPath());
    }

    private static void writeOptionalResourceLocation(DataOutputStream output,
                                                      @Nullable ResourceLocation location) throws IOException {
        output.writeBoolean(location != null);
        if (location != null) {
            writeResourceLocation(output, location);
        }
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
        var uniqueStacks = new ObjectArrayList<GenericStack>(Math.min(limit, entries.size()));
        for (var entry : entries) {
            if (seenKeys.add(entry.what())) {
                uniqueStacks.add(new GenericStack(entry.what(), preserveAmounts ? entry.amount() : 0));
                if (uniqueStacks.size() >= limit) {
                    break;
                }
            }
        }
        return Collections.unmodifiableList(uniqueStacks);
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

    private record SortableEntry(AEKey key, long amount, ResourceLocation typeId, @Nullable ResourceLocation keyId,
                                 int tagStart, int tagLength) {
    }

    private static final class TagBuffer extends ByteArrayOutputStream {
        private byte[] buffer() {
            return this.buf;
        }
    }

    private static final class OwnedEntries extends AbstractList<GenericStack> implements RandomAccess {
        private final List<GenericStack> entries;

        private OwnedEntries(List<GenericStack> entries) {
            this.entries = entries;
        }

        @Override
        public GenericStack get(int index) {
            return this.entries.get(index);
        }

        @Override
        public int size() {
            return this.entries.size();
        }
    }
}
