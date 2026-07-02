package ae2.cellterminal.server;

import java.util.List;
import java.util.Objects;

/**
 * Preview result for a Cell Terminal network-tool backend operation.
 *
 * @param operation         Operation represented by this preview.
 * @param contextId         Effective Cell Terminal context id.
 * @param token             Confirmation token required by execute.
 * @param planSignature     Deterministic signature of the planned writes.
 * @param uniqueTypeSummary Available cell and unique type counts for unique-type reallocation previews.
 * @param targetBreakdown   Aggregated target labels for mass partition previews.
 * @param plans             Planned per-target partition writes.
 * @param failures          Targets excluded from the plan with reasons.
 * @param invalidatedView   Whether the preview caused the session cache to be marked stale.
 */
public record CellTerminalNetworkToolPreview(CellTerminalNetworkToolOperation operation, String contextId,
                                             CellTerminalActionToken token, String planSignature,
                                             UniqueTypeSummary uniqueTypeSummary,
                                             List<TargetBreakdown> targetBreakdown,
                                             List<CellTerminalPartitionPlan> plans,
                                             List<CellTerminalActionFailure> failures,
                                             boolean invalidatedView) {
    public CellTerminalNetworkToolPreview {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(planSignature, "planSignature");
        targetBreakdown = List.copyOf(Objects.requireNonNull(targetBreakdown, "targetBreakdown"));
        plans = List.copyOf(Objects.requireNonNull(plans, "plans"));
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
    }

    public record UniqueTypeSummary(int availableCellCount,
                                    int uniqueTypeCount,
                                    List<TypeBreakdown> breakdown) {
        public UniqueTypeSummary {
            if (availableCellCount < 0) {
                throw new IllegalArgumentException("availableCellCount must be >= 0");
            }
            if (uniqueTypeCount < 0) {
                throw new IllegalArgumentException("uniqueTypeCount must be >= 0");
            }
            breakdown = List.copyOf(Objects.requireNonNull(breakdown, "breakdown"));
        }
    }

    public record TypeBreakdown(String typeId, int availableCellCount, int uniqueTypeCount) {
        public TypeBreakdown {
            Objects.requireNonNull(typeId, "typeId");
            if (availableCellCount < 0) {
                throw new IllegalArgumentException("availableCellCount must be >= 0");
            }
            if (uniqueTypeCount < 0) {
                throw new IllegalArgumentException("uniqueTypeCount must be >= 0");
            }
        }
    }

    public record TargetBreakdown(String label, int count) {
        public TargetBreakdown {
            Objects.requireNonNull(label, "label");
            if (count < 0) {
                throw new IllegalArgumentException("count must be >= 0");
            }
        }
    }
}
