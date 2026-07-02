package ae2.api.cellterminal;

import ae2.api.stacks.GenericStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable partition/filter inventory snapshot.
 *
 * @param slots Slot-ordered configured keys. Empty slots are represented as {@code null}.
 */
public record CellTerminalPartitionSnapshot(List<@Nullable GenericStack> slots) {
    public CellTerminalPartitionSnapshot {
        slots = immutableNullableCopy(slots);
    }

    private static List<@Nullable GenericStack> immutableNullableCopy(List<@Nullable GenericStack> slots) {
        Objects.requireNonNull(slots, "slots");
        return Collections.unmodifiableList(new ArrayList<>(slots));
    }
}
