package ae2.cellterminal.server;

import ae2.api.cellterminal.CellTerminalSubnetTarget;
import ae2.api.networking.IGrid;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.UUID;

public final class CellTerminalSession {
    private static final String SUBNET_RESTORE_NO_LAST_LOADED =
        "gui.ae2.CellTerminal.subnet.restore.no_last_loaded";
    private static final String SUBNET_RESTORE_SUCCESS =
        "gui.ae2.CellTerminal.subnet.restore.restored";
    private static final String SUBNET_RESTORE_NOT_VISIBLE =
        "gui.ae2.CellTerminal.subnet.restore.not_visible";
    private final IGrid mainGrid;
    private final String sessionId = UUID.randomUUID().toString();
    private final ArrayDeque<IGrid> subnetStack = new ArrayDeque<>();
    private final CellTerminalSubnetLedger subnetLedger;
    private IGrid effectiveGrid;
    private long contextSequence;
    private long cacheRevision;
    private String currentContextId;
    private PendingAction pendingAction;

    public CellTerminalSession(IGrid mainGrid) {
        this(mainGrid, new CellTerminalSubnetLedger());
    }

    public CellTerminalSession(IGrid mainGrid, CellTerminalSubnetLedger subnetLedger) {
        this.mainGrid = Objects.requireNonNull(mainGrid, "mainGrid");
        this.subnetLedger = Objects.requireNonNull(subnetLedger, "subnetLedger");
        this.effectiveGrid = mainGrid;
    }

    /**
     * Returns the root ME grid for this Cell Terminal session.
     *
     * @return Main grid.
     */
    public IGrid getMainGrid() {
        return this.mainGrid;
    }

    /**
     * Returns the grid currently being scanned by the Cell Terminal.
     *
     * @return Effective grid.
     */
    public IGrid getEffectiveGrid() {
        return this.effectiveGrid;
    }

    /**
     * Returns a new context id for the current effective grid.
     *
     * @return Context id.
     */
    public String nextContextId() {
        return this.sessionId + ":" + (++this.contextSequence);
    }

    /**
     * Returns the stable context id for the current effective grid.
     * <p>
     * Repeated scans of the same effective grid use one id so client-side packets can reject stale network contexts
     * without treating ordinary refreshes as subnet switches.
     *
     * @return Current effective-grid context id.
     */
    public String getCurrentContextId() {
        if (this.currentContextId == null) {
            this.currentContextId = nextContextId();
        }
        return this.currentContextId;
    }

    /**
     * Returns the current cache invalidation revision.
     *
     * @return Cache revision.
     */
    public long getCacheRevision() {
        return this.cacheRevision;
    }

    /**
     * Returns the persistent subnet metadata ledger owned by this session.
     *
     * @return Session subnet ledger.
     */
    public CellTerminalSubnetLedger getSubnetLedger() {
        return this.subnetLedger;
    }

    /**
     * Marks cached target and snapshot data as stale.
     */
    public void markCacheStale() {
        this.cacheRevision++;
    }

    public void loadSubnet(CellTerminalSubnetTarget subnetTarget, UUID playerId) {
        Objects.requireNonNull(subnetTarget, "subnetTarget");
        Objects.requireNonNull(playerId, "playerId");
        this.subnetStack.push(this.effectiveGrid);
        this.effectiveGrid = subnetTarget.resolveSubnet();
        this.currentContextId = nextContextId();
        this.subnetLedger.markLastLoaded(playerId, CellTerminalSubnetHandle.fromTarget(subnetTarget));
        markCacheStale();
    }

    /**
     * Returns from the current subnet to its parent grid.
     *
     * @return {@code true} when a parent grid existed.
     */
    public boolean returnToParentGrid() {
        if (this.subnetStack.isEmpty()) {
            return false;
        }
        this.effectiveGrid = this.subnetStack.pop();
        this.currentContextId = nextContextId();
        markCacheStale();
        return true;
    }

    /**
     * Registers the only pending action token accepted by the next execute call for this session.
     *
     * @param operation     Operation represented by the pending action.
     * @param contextId     Context id produced by preview.
     * @param planSignature Deterministic signature produced by preview.
     * @param token         Token returned to the caller.
     */
    public void registerPendingAction(CellTerminalNetworkToolOperation operation, String contextId,
                                      String planSignature, CellTerminalActionToken token) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(planSignature, "planSignature");
        Objects.requireNonNull(token, "token");
        this.pendingAction = new PendingAction(operation, contextId, planSignature, this.cacheRevision, token);
    }

    /**
     * Validates and consumes the pending action for this session.
     *
     * @param operation     Expected operation.
     * @param contextId     Expected context id.
     * @param planSignature Expected plan signature.
     * @param token         Token supplied by execute.
     */
    public void consumePendingAction(CellTerminalNetworkToolOperation operation, String contextId,
                                     String planSignature, CellTerminalActionToken token) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(planSignature, "planSignature");
        Objects.requireNonNull(token, "token");
        PendingAction pending = this.pendingAction;
        if (pending == null) {
            throw new IllegalStateException("Cell Terminal action has no pending preview");
        }
        if (pending.operation() != operation) {
            throw new IllegalArgumentException("Cell Terminal action operation mismatch. expected="
                + pending.operation() + ", actual=" + operation);
        }
        if (!pending.contextId().equals(contextId)) {
            throw new IllegalArgumentException("Cell Terminal action context mismatch");
        }
        if (!pending.planSignature().equals(planSignature)) {
            throw new IllegalArgumentException("Cell Terminal action signature mismatch");
        }
        if (pending.cacheRevision() != this.cacheRevision) {
            throw new IllegalStateException("Cell Terminal action cache revision changed since preview");
        }
        if (!pending.token().equals(token)) {
            throw new IllegalArgumentException("Cell Terminal action token mismatch");
        }
        this.pendingAction = null;
    }

    /**
     * Restores the last loaded subnet from a fresh scan snapshot when it is still visible and resolvable.
     *
     * @param snapshot Latest scan snapshot for the current effective grid.
     * @return Restore result for logging and future UI feedback.
     */
    public CellTerminalSubnetActionResult restoreLastLoadedSubnet(CellTerminalSnapshot snapshot, UUID playerId,
                                                                  CellTerminalTargetAccess targetAccess) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(targetAccess, "targetAccess");
        CellTerminalSubnetHandle handle = this.subnetLedger.getLastLoadedHandle(playerId);
        if (handle == null) {
            return new CellTerminalSubnetActionResult(
                CellTerminalActionStatus.FAILURE,
                null,
                SUBNET_RESTORE_NO_LAST_LOADED);
        }
        for (var subnet : snapshot.subnetTargets()) {
            if (handle.subnetId().equals(subnet.subnetId())) {
                CellTerminalSubnetHandle currentHandle = CellTerminalSubnetHandle.fromTarget(subnet);
                loadSubnet(targetAccess.resolveSubnet(currentHandle), playerId);
                return new CellTerminalSubnetActionResult(
                    CellTerminalActionStatus.SUCCESS,
                    currentHandle,
                    SUBNET_RESTORE_SUCCESS);
            }
        }
        return new CellTerminalSubnetActionResult(
            CellTerminalActionStatus.FAILURE,
            handle,
            SUBNET_RESTORE_NOT_VISIBLE);
    }

    private record PendingAction(CellTerminalNetworkToolOperation operation, String contextId, String planSignature,
                                 long cacheRevision, CellTerminalActionToken token) {
    }
}
