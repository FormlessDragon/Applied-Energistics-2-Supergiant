package ae2.cellterminal.server;

import ae2.api.cellterminal.CellTerminalBusTarget;
import ae2.api.cellterminal.CellTerminalCellSlotTarget;
import ae2.api.cellterminal.CellTerminalStorageTarget;
import ae2.api.cellterminal.CellTerminalSubnetTarget;
import ae2.api.cellterminal.CellTerminalTarget;
import ae2.cellterminal.internal.CellTerminalRegistry;
import ae2.cellterminal.internal.CellTerminalWriteSupport;

import java.util.List;
import java.util.Objects;

/**
 * GUI-independent resolver for stable Cell Terminal target handles.
 */
public final class CellTerminalTargetAccess implements CellTerminalTargetLookup {
    private static void requireStableTarget(CellTerminalTargetHandle handle, CellTerminalTarget liveTarget) {
        Objects.requireNonNull(liveTarget, "liveTarget");
        if (!handle.stableTargetId().equals(liveTarget.stableTargetId())) {
            CellTerminalWriteSupport.fail(
                "Cell Terminal target stableTargetId mismatch. requested=%s, resolved=%s, locator=%s",
                handle.stableTargetId(),
                liveTarget.stableTargetId(),
                handle.locator());
        }
        if (!handle.locator().equals(liveTarget.locator())) {
            CellTerminalWriteSupport.fail(
                "Cell Terminal target locator changed during resolve. requested=%s, resolved=%s, target=%s",
                handle.locator(),
                liveTarget.locator(),
                handle.stableTargetId());
        }
    }

    /**
     * Resolves and validates a storage target handle.
     *
     * @param handle Stable target handle from a previous scan.
     * @return Live target wrapper.
     */
    public CellTerminalStorageTarget resolveStorage(CellTerminalTargetHandle handle) {
        Objects.requireNonNull(handle, "handle");
        CellTerminalStorageTarget liveTarget =
            CellTerminalRegistry.getStorageTargetResolver(handle.locator()).resolveStorageTarget(handle.locator());
        requireStableTarget(handle, liveTarget);
        return liveTarget;
    }

    /**
     * Resolves and validates a storage bus handle.
     *
     * @param handle Stable target handle from a previous scan.
     * @return Live storage-bus target wrapper.
     */
    public CellTerminalBusTarget resolveStorageBus(CellTerminalTargetHandle handle) {
        Objects.requireNonNull(handle, "handle");
        CellTerminalBusTarget liveTarget =
            CellTerminalRegistry.getBusTargetResolver(handle.locator()).resolveBusTarget(handle.locator());
        requireStableTarget(handle, liveTarget);
        return liveTarget;
    }

    /**
     * Resolves and validates a cell slot handle.
     *
     * @param handle Stable slot handle from a previous scan.
     * @return Live cell slot target wrapper.
     */
    public CellTerminalCellSlotTarget resolveCellSlot(CellTerminalCellSlotHandle handle) {
        Objects.requireNonNull(handle, "handle");
        CellTerminalStorageTarget target = resolveStorage(handle.owner());
        List<? extends CellTerminalCellSlotTarget> slots = target.getCellSlots();
        if (handle.slotIndex() >= slots.size()) {
            throw new IllegalStateException("Cell Terminal cell slot no longer exists: " + handle);
        }
        return slots.get(handle.slotIndex());
    }

    /**
     * Resolves and validates a subnet target handle.
     *
     * @param handle Stable subnet handle from a previous scan.
     * @return Live subnet target wrapper.
     */
    public CellTerminalSubnetTarget resolveSubnet(CellTerminalSubnetHandle handle) {
        Objects.requireNonNull(handle, "handle");
        CellTerminalSubnetTarget liveTarget = CellTerminalRegistry.getSubnetTargetResolver(handle.locator())
                                                                  .resolveSubnetTarget(handle.stableTargetId(), handle.locator());
        requireStableTarget(new CellTerminalTargetHandle(handle.stableTargetId(), handle.locator()), liveTarget);
        if (!handle.subnetId().equals(liveTarget.subnetId())) {
            CellTerminalWriteSupport.fail(
                "Cell Terminal subnet id mismatch. requested=%s, resolved=%s, locator=%s",
                handle.subnetId(),
                liveTarget.subnetId(),
                handle.locator());
        }
        return liveTarget;
    }
}
