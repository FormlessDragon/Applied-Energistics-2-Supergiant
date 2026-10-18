package ae2.container.implementations;

import ae2.api.networking.IGrid;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.storage.ITerminalHost;
import ae2.container.AEBaseContainer;
import ae2.container.PendingGridFills;
import ae2.core.network.clientbound.CraftingTreeTransferAccumulator;
import ae2.integration.data.LiteCraftTreeNode;
import net.minecraft.entity.player.InventoryPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Future;

public class ContainerCraftingTree extends AEBaseContainer {
    private static final long TRANSFER_TIMEOUT_NANOS = 30_000_000_000L;

    private Future<ICraftingPlan> job = null;
    private IGrid calculationGrid;
    @Nullable
    private PendingGridFills.Order pendingFillsOrder;
    private final CraftingTreeTransferAccumulator transfer = new CraftingTreeTransferAccumulator();
    private ClientStatus clientStatus = ClientStatus.LOADING;
    private LiteCraftTreeNode clientRoot;
    private String clientError;
    private int clientRevision;
    private long clientLoadStartedNanos = System.nanoTime();

    public enum ClientStatus {LOADING, SUCCESS, ERROR}

    public ContainerCraftingTree(final InventoryPlayer ip, final ITerminalHost te) {
        super(ip, te);
    }

    public Future<ICraftingPlan> getJob() {
        return job;
    }

    public void setJob(final Future<ICraftingPlan> job) {
        setJob(job, null);
    }

    public void setJob(final Future<ICraftingPlan> job, IGrid source) {
        setJob(job, source, null);
    }

    public void setJob(final Future<ICraftingPlan> job, IGrid source, @Nullable PendingGridFills.Order order) {
        this.job = job;
        this.calculationGrid = source;
        this.pendingFillsOrder = order;
    }

    @Nullable
    public PendingGridFills.Order getPendingFillsOrder() {
        return this.pendingFillsOrder;
    }

    public IGrid getCalculationGrid() {
        return this.calculationGrid;
    }

    public CraftingTreeTransferAccumulator getTransfer() {
        return transfer;
    }

    public ClientStatus getClientStatus() {
        return clientStatus;
    }

    public LiteCraftTreeNode getClientRoot() {
        return clientRoot;
    }

    public String getClientError() {
        return clientError;
    }

    public int getClientRevision() {
        return clientRevision;
    }

    public void setClientRoot(LiteCraftTreeNode root) {
        this.clientRoot = root;
        this.clientError = null;
        this.clientStatus = ClientStatus.SUCCESS;
        this.clientRevision++;
    }

    public void setClientError(String error) {
        this.clientRoot = null;
        this.clientError = error;
        this.clientStatus = ClientStatus.ERROR;
        this.clientRevision++;
    }

    public void setClientLoading() {
        this.clientRoot = null;
        this.clientError = null;
        this.clientStatus = ClientStatus.LOADING;
        this.clientLoadStartedNanos = System.nanoTime();
        this.clientRevision++;
    }

    public void checkClientTransferTimeout() {
        long now = System.nanoTime();
        if (clientStatus == ClientStatus.LOADING && (transfer.timeout(now, TRANSFER_TIMEOUT_NANOS)
            || !transfer.isActive() && now - clientLoadStartedNanos >= TRANSFER_TIMEOUT_NANOS)) {
            setClientError("timeout");
        }
    }

}
