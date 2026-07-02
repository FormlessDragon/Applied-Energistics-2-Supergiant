package ae2.cellterminal.server;

import java.util.List;

/**
 * GUI-independent backend for Cell Terminal network-tool actions.
 * <p>
 * Every mutating operation has a preview phase that creates a confirmation token and an execute phase that re-scans live
 * targets before applying the planned writes.
 */
public interface CellTerminalNetworkTool {
    /**
     * Previews unique type reallocation across selected cell slots.
     *
     * @param session Active Cell Terminal session.
     * @param slots   Selected cell slots.
     * @return Preview containing confirmation token and per-target plans.
     */
    CellTerminalNetworkToolPreview previewUniqueTypeReallocation(CellTerminalSession session,
                                                                 List<CellTerminalCellSlotHandle> slots);

    /**
     * Executes a unique type reallocation preview.
     *
     * @param session Active Cell Terminal session.
     * @param preview Preview returned by the matching preview call.
     * @param token   Confirmation token returned by the preview.
     * @return Execute result with per-target success and failure details.
     */
    CellTerminalActionResult executeUniqueTypeReallocation(CellTerminalSession session,
                                                           CellTerminalNetworkToolPreview preview,
                                                           CellTerminalActionToken token);

    /**
     * Previews partitioning selected cells from their own contents.
     *
     * @param session Active Cell Terminal session.
     * @param slots   Selected cell slots.
     * @return Preview containing confirmation token and per-target plans.
     */
    CellTerminalNetworkToolPreview previewPartitionCellsByContent(CellTerminalSession session,
                                                                  List<CellTerminalCellSlotHandle> slots);

    /**
     * Executes a cell partition preview.
     *
     * @param session Active Cell Terminal session.
     * @param preview Preview returned by the matching preview call.
     * @param token   Confirmation token returned by the preview.
     * @return Execute result with per-target success and failure details.
     */
    CellTerminalActionResult executePartitionCellsByContent(CellTerminalSession session,
                                                            CellTerminalNetworkToolPreview preview,
                                                            CellTerminalActionToken token);

    /**
     * Previews partitioning selected storage buses from their visible contents.
     *
     * @param session Active Cell Terminal session.
     * @param targets Selected storage buses.
     * @return Preview containing confirmation token and per-target plans.
     */
    CellTerminalNetworkToolPreview previewPartitionStorageBusesByContent(CellTerminalSession session,
                                                                         List<CellTerminalTargetHandle> targets);

    /**
     * Executes a storage-bus partition preview.
     *
     * @param session Active Cell Terminal session.
     * @param preview Preview returned by the matching preview call.
     * @param token   Confirmation token returned by the preview.
     * @return Execute result with per-target success and failure details.
     */
    CellTerminalActionResult executePartitionStorageBusesByContent(CellTerminalSession session,
                                                                   CellTerminalNetworkToolPreview preview,
                                                                   CellTerminalActionToken token);
}
