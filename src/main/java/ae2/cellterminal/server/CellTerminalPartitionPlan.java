package ae2.cellterminal.server;

import ae2.api.cellterminal.CellTerminalTargetLocator;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable partition write plan produced by a network-tool preview.
 *
 * @param operation               Operation that produced this plan.
 * @param stableTargetId          Stable target id expected during execute.
 * @param locator                 Locator expected during execute.
 * @param slotIndex               Cell slot index, or {@code -1} for storage-bus target plans.
 * @param expectedContentRevision Content revision observed during preview.
 * @param expectedCapacity        Partition capacity observed during preview.
 * @param baselinePartitionSlots  Partition/filter inventory observed during preview before the write.
 * @param partitionSlots          Slot-ordered partition entries to write.
 * @param resourceMovements       Resources this plan moves out of the source cell during execute.
 */
public record CellTerminalPartitionPlan(CellTerminalNetworkToolOperation operation, String stableTargetId,
                                        CellTerminalTargetLocator locator, int slotIndex,
                                        String expectedContentRevision, int expectedCapacity,
                                        List<@Nullable GenericStack> baselinePartitionSlots,
                                        List<@Nullable GenericStack> partitionSlots,
                                        List<ResourceMovement> resourceMovements) {
    public CellTerminalPartitionPlan(CellTerminalNetworkToolOperation operation, String stableTargetId,
                                     CellTerminalTargetLocator locator, int slotIndex,
                                     String expectedContentRevision, int expectedCapacity,
                                     List<@Nullable GenericStack> baselinePartitionSlots,
                                     List<@Nullable GenericStack> partitionSlots) {
        this(
            operation,
            stableTargetId,
            locator,
            slotIndex,
            expectedContentRevision,
            expectedCapacity,
            baselinePartitionSlots,
            partitionSlots,
            List.of());
    }

    public CellTerminalPartitionPlan {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(stableTargetId, "stableTargetId");
        Objects.requireNonNull(locator, "locator");
        Objects.requireNonNull(expectedContentRevision, "expectedContentRevision");
        if (slotIndex < -1) {
            throw new IllegalArgumentException("slotIndex must be >= -1");
        }
        if (expectedCapacity < 0) {
            throw new IllegalArgumentException("expectedCapacity must be >= 0");
        }
        baselinePartitionSlots = immutableNullableCopy(baselinePartitionSlots, "baselinePartitionSlots");
        partitionSlots = immutableNullableCopy(partitionSlots);
        resourceMovements = List.copyOf(Objects.requireNonNull(resourceMovements, "resourceMovements"));
    }

    private static List<@Nullable GenericStack> immutableNullableCopy(List<@Nullable GenericStack> slots) {
        return immutableNullableCopy(slots, "partitionSlots");
    }

    private static List<@Nullable GenericStack> immutableNullableCopy(List<@Nullable GenericStack> slots, String name) {
        Objects.requireNonNull(slots, name);
        return Collections.unmodifiableList(new ArrayList<>(slots));
    }

    /**
     * Returns whether this plan writes a cell slot.
     *
     * @return {@code true} for cell-slot plans.
     */
    public boolean isCellSlotPlan() {
        return this.slotIndex >= 0;
    }

    /**
     * Returns a target handle for this plan.
     *
     * @return Target handle.
     */
    public CellTerminalTargetHandle targetHandle() {
        return new CellTerminalTargetHandle(stableTargetId, locator);
    }

    /**
     * Returns a cell slot handle for this plan.
     *
     * @return Cell slot handle.
     */
    public CellTerminalCellSlotHandle cellSlotHandle() {
        if (!isCellSlotPlan()) {
            throw new IllegalStateException("Partition plan does not target a cell slot");
        }
        return new CellTerminalCellSlotHandle(stableTargetId, locator, slotIndex);
    }

    /**
     * Source-to-target resource movement produced by unique type reallocation.
     *
     * @param targetStableTargetId Stable id of the destination cell target.
     * @param targetLocator        Locator of the destination cell target.
     * @param targetSlotIndex      Slot index of the destination cell.
     * @param what                 Resource key to move.
     * @param amount               Amount to move.
     */
    public record ResourceMovement(String targetStableTargetId,
                                   CellTerminalTargetLocator targetLocator,
                                   int targetSlotIndex,
                                   AEKey what,
                                   long amount) {
        public ResourceMovement {
            Objects.requireNonNull(targetStableTargetId, "targetStableTargetId");
            Objects.requireNonNull(targetLocator, "targetLocator");
            Objects.requireNonNull(what, "what");
            if (targetSlotIndex < 0) {
                throw new IllegalArgumentException("targetSlotIndex must be >= 0");
            }
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be > 0");
            }
        }

        /**
         * Returns a cell slot handle for the movement destination.
         *
         * @return Destination handle.
         */
        public CellTerminalCellSlotHandle targetCellSlotHandle() {
            return new CellTerminalCellSlotHandle(targetStableTargetId, targetLocator, targetSlotIndex);
        }
    }
}
