package ae2.cellterminal.server;

import ae2.api.cellterminal.CellTerminalApi;
import ae2.api.cellterminal.CellTerminalBusTarget;
import ae2.api.cellterminal.CellTerminalStorageTarget;
import ae2.api.cellterminal.CellTerminalSubnetTarget;
import ae2.api.networking.IGrid;
import ae2.core.AELog;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Objects;

public final class CellTerminalScannerCore {
    public CellTerminalSnapshot scan(CellTerminalSession session) {
        Objects.requireNonNull(session, "session");

        IGrid grid = session.getEffectiveGrid();
        var storages = new ObjectArrayList<CellTerminalStorageTarget>();
        var buses = new ObjectArrayList<CellTerminalBusTarget>();
        var subnets = new ObjectArrayList<CellTerminalSubnetTarget>();

        for (var scanner : CellTerminalApi.getStorageScanners()) {
            try {
                storages.addAll(scanner.scan(grid));
            } catch (Exception e) {
                AELog.warn("Cell Terminal storage scanner %s failed", scanner.getId(), e);
            }
        }
        for (var scanner : CellTerminalApi.getStorageBusScanners()) {
            try {
                buses.addAll(scanner.scan(grid));
            } catch (Exception e) {
                AELog.warn("Cell Terminal bus scanner %s failed", scanner.getId(), e);
            }
        }
        for (var scanner : CellTerminalApi.getSubnetScanners()) {
            try {
                subnets.addAll(scanner.scan(grid));
            } catch (Exception e) {
                AELog.warn("Cell Terminal subnet scanner %s failed", scanner.getId(), e);
            }
        }

        String contextId = session.getCurrentContextId();
        AELog.debug("Cell Terminal scan context=%s storages=%d buses=%d subnets=%d",
            contextId, storages.size(), buses.size(), subnets.size());

        return new CellTerminalSnapshot(
            contextId,
            session.getCacheRevision(),
            storages,
            buses,
            subnets);
    }
}
