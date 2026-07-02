package ae2.cellterminal.server;

import java.util.List;
import java.util.Objects;

/**
 * Execute result for a Cell Terminal network-tool backend operation.
 *
 * @param status          Overall action status.
 * @param appliedPlans    Plans that were applied successfully.
 * @param failures        Plans that failed with diagnostic details.
 * @param invalidatedView Whether the execute marked the session cache stale.
 */
public record CellTerminalActionResult(CellTerminalActionStatus status,
                                       List<CellTerminalPartitionPlan> appliedPlans,
                                       List<CellTerminalActionFailure> failures,
                                       boolean invalidatedView) {
    public CellTerminalActionResult {
        Objects.requireNonNull(status, "status");
        appliedPlans = List.copyOf(Objects.requireNonNull(appliedPlans, "appliedPlans"));
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
    }
}
