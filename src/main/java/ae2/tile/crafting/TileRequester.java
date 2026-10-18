package ae2.tile.crafting;

import ae2.api.config.Actionable;
import ae2.api.networking.GridFlags;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.crafting.CalculationStrategy;
import ae2.api.networking.crafting.CraftingSubmitErrorCode;
import ae2.api.networking.crafting.ICraftingForceStartRequester;
import ae2.api.networking.crafting.ICraftingLink;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingRequester;
import ae2.api.networking.crafting.ICraftingSimulationRequester;
import ae2.api.networking.crafting.ICraftingSubmitResult;
import ae2.api.networking.crafting.UnsuitableCpus;
import ae2.api.networking.security.IActionHost;
import ae2.api.networking.security.IActionSource;
import ae2.api.networking.storage.IStorageWatcherNode;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.orientation.BlockOrientation;
import ae2.api.orientation.RelativeSide;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.StorageHelper;
import ae2.core.AEConfig;
import ae2.core.AELog;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEItems;
import ae2.hooks.ticking.TickHandler;
import ae2.items.misc.GenericResourcePackageItem;
import ae2.me.helpers.TileNodeListener;
import ae2.me.service.CraftingService;
import ae2.text.TextComponentItemStack;
import ae2.tile.crafting.requester.BlockedState;
import ae2.tile.crafting.requester.LinkState;
import ae2.tile.crafting.requester.MaterialSubsetCache;
import ae2.tile.crafting.requester.NoPatternState;
import ae2.tile.crafting.requester.PlanState;
import ae2.tile.crafting.requester.RequestHost;
import ae2.tile.crafting.requester.RequestList;
import ae2.tile.crafting.requester.RequestStatus;
import ae2.tile.crafting.requester.RequesterStorageTracker;
import ae2.tile.crafting.requester.StatusState;
import ae2.tile.grid.AENetworkedTile;
import ae2.util.SettingsFrom;
import com.google.common.collect.ImmutableSet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class TileRequester extends AENetworkedTile implements RequestHost, IGridTickable, ICraftingForceStartRequester {

    private static final int MISSING_RETRY_TICKS = 100;
    private static final int CPU_TOO_SMALL_RETRY_TICKS = 100;

    private static final String REQUESTS_TAG = "requests";
    private static final String STORAGE_MANAGER_TAG = "storage_manager";
    private static final String REQUEST_STATUS_TAG = "request_status";
    private static final String SUBMISSIONS_TAG = "request_submissions";
    private static final String MEMORY_CARD_REQUESTS_TAG = "requester_requests";

    private final RequestList requestManager;
    private final RequesterStorageTracker storageManager;
    private final StatusState[] requestStatus;
    private final Submission[] submissions;
    private final long[] requestGeneration;
    private final boolean[] handlingRequest;
    private final long[] missingRetryUntil;
    private final long[] cpuRetryUntil;
    private final MaterialSubsetCache materialSubsets;
    private final IActionSource actionSource = IActionSource.ofMachine(this);
    private final RequesterServices services = new GridRequesterServices();
    private final ICraftingSimulationRequester simulationSource = new RequesterSimulationSource();
    private boolean submittingForceStart;
    private boolean requestsClosed;
    private long lifecycleGeneration;
    private int cancellationDepth;
    private boolean receiptSavePending;
    private boolean savingReceipt;
    private boolean receiptSaveFailureLogged;

    public TileRequester() {
        int requestCount = AEConfig.instance().getRequests();
        this.requestManager = new RequestList(this, requestCount);
        this.storageManager = new RequesterStorageTracker(requestCount, this::clearMissingRetry);
        this.requestStatus = new StatusState[requestCount];
        this.submissions = new Submission[requestCount];
        this.requestGeneration = new long[requestCount];
        this.handlingRequest = new boolean[requestCount];
        this.missingRetryUntil = new long[requestCount];
        this.cpuRetryUntil = new long[requestCount];
        this.materialSubsets = new MaterialSubsetCache(requestCount);

        Arrays.fill(this.requestStatus, StatusState.IDLE);
        this.getMainNode()
            .addService(IGridTickable.class, this)
            .addService(ICraftingRequester.class, this)
            .addService(IStorageWatcherNode.class, this.storageManager)
            .setIdlePowerUsage(AEConfig.instance().getIdleEnergy());
        if (AEConfig.instance().getRequireChannel()) {
            this.getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL);
        }
    }

    @Override
    public ItemStack getItemFromTile() {
        return AEBlocks.REQUESTER.stack();
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, new TileNodeListener<>() {
            @Override
            public void onGridChanged(TileRequester owner, IGridNode node) {
                owner.invalidateCalculationSources();
            }
        });
    }

    private void invalidateCalculationSources() {
        this.cancellationDepth++;
        try {
            this.invalidateCalculationStates();
        } finally {
            this.cancellationDepth--;
        }
    }

    private void invalidateCalculationStates() {
        for (int slot = 0; slot < this.requestStatus.length; slot++) {
            if (this.submissions[slot] != null) {
                this.submissions[slot].revoked = true;
            }
            if (this.requestStatus[slot] == StatusState.REQUEST || this.requestStatus[slot] instanceof PlanState
                || this.requestStatus[slot] instanceof BlockedState) {
                this.cancelRequest(slot);
            } else {
                this.requestGeneration[slot]++;
            }
            this.clearRetry(slot);
        }
        this.materialSubsets.clearAll();
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        data.setTag(REQUESTS_TAG, this.requestManager.writeToNBT());
        data.setTag(STORAGE_MANAGER_TAG, this.storageManager.writeToNBT());
        data.setTag(REQUEST_STATUS_TAG, this.writeStatusToNBT());
        var submissions = new NBTTagCompound();
        for (int slot = 0; slot < this.submissions.length; slot++) {
            if (this.submissions[slot] != null) {
                submissions.setTag(Integer.toString(slot), this.submissions[slot].writeToNBT());
            }
        }
        data.setTag(SUBMISSIONS_TAG, submissions);
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        this.cancellationDepth++;
        try {
            this.invalidateCalculationStates();
            this.loadRequesterTag(data);
        } finally {
            this.cancellationDepth--;
        }
    }

    private void loadRequesterTag(NBTTagCompound data) {
        if (data.hasKey(SUBMISSIONS_TAG) && !data.hasKey(SUBMISSIONS_TAG, 10)) {
            throw new IllegalStateException("Invalid requester submission records");
        }
        super.loadTag(data);
        this.readSubmissions(data.getCompoundTag(SUBMISSIONS_TAG));
        if (data.hasKey(REQUESTS_TAG, 10)) {
            this.requestManager.readFromNBT(data.getCompoundTag(REQUESTS_TAG));
        }
        if (data.hasKey(STORAGE_MANAGER_TAG, 10)) {
            this.storageManager.readFromNBT(data.getCompoundTag(STORAGE_MANAGER_TAG));
        }
        if (data.hasKey(REQUEST_STATUS_TAG, 10)) {
            this.readStatusFromNBT(data.getCompoundTag(REQUEST_STATUS_TAG));
        }
        this.restorePersistentStatus();
    }

    @Override
    public void exportSettings(SettingsFrom mode, NBTTagCompound output) {
        super.exportSettings(mode, output);
        if (mode == SettingsFrom.MEMORY_CARD) {
            output.setTag(MEMORY_CARD_REQUESTS_TAG, this.requestManager.writeToNBT());
        }
    }

    @Override
    public void importSettings(SettingsFrom mode, NBTTagCompound input, @Nullable EntityPlayer player) {
        super.importSettings(mode, input, player);
        if (mode == SettingsFrom.MEMORY_CARD && input.hasKey(MEMORY_CARD_REQUESTS_TAG, 10)) {
            this.cancellationDepth++;
            try {
                this.resetRuntimeState();
                this.requestManager.replaceFromNBT(input.getCompoundTag(MEMORY_CARD_REQUESTS_TAG));
                this.materialSubsets.clearAll();
                this.saveChanges();
            } finally {
                this.cancellationDepth--;
            }
        }
    }

    @Override
    public Set<EnumFacing> getGridConnectableSides(BlockOrientation orientation) {
        EnumSet<EnumFacing> exposedSides = EnumSet.allOf(EnumFacing.class);
        exposedSides.remove(orientation.getSide(RelativeSide.FRONT));
        return exposedSides;
    }

    @Override
    public RequestList getRequests() {
        return this.requestManager;
    }

    public RequesterStorageTracker getStorageTracker() {
        return this.storageManager;
    }

    @Override
    public IActionSource getActionSource() {
        return this.actionSource;
    }

    @Override
    public ITextComponent getRequesterName() {
        return this.hasCustomName()
            ? new TextComponentString(Objects.requireNonNull(this.getCustomName()))
            : TextComponentItemStack.of(AEBlocks.REQUESTER.stack());
    }

    @Override
    public long getRequesterSortValue() {
        return ((long) this.pos.getZ() << 24) ^ ((long) this.pos.getX() << 8) ^ this.pos.getY();
    }

    @Override
    public void onRequestChanged(int index) {
        this.cancellationDepth++;
        try {
            this.cancelRequest(index);
            this.storageManager.clearConfiguration(index);
            this.clearRetry(index);
            this.setRequestState(index, StatusState.IDLE);
            this.materialSubsets.clear(index);
            this.saveChanges();
        } finally {
            this.cancellationDepth--;
        }
    }

    @Nullable
    private Collection<AEKey> getSimulationSubset(AEKey output) {
        var grid = this.getMainNode().getGrid();
        if (grid == null || this.world == null) {
            return null;
        }
        long revision = craftingRevision(grid);
        var cached = this.materialSubsets.peekByKey(output, grid, revision);
        if (cached != null) {
            return cached;
        }
        // First request for this output: seed the slot cache from the closure the crafting service remembered for the
        // last completed calculation. Absent until one has completed, in which case the calculation derives the
        // closure itself and the next request hits the cache.
        if (grid.getCraftingService() instanceof CraftingService service) {
            var closure = service.peekSimulationClosure(output);
            if (closure != null) {
                var keys = closure.keys();
                rememberSubset(output, grid, revision, keys);
                return keys;
            }
        }
        return null;
    }

    /**
     * Judges the request top-down against the inventory using the cached material closure. Returns true when the
     * closure proves a required material is entirely missing, so the calculation could only ever report missing
     * items; otherwise the walked subset is remembered for the calculation.
     */
    public boolean planMaterialSubset(AEKey key, long amount) {
        var grid = this.getMainNode().getGrid();
        if (grid == null || this.world == null) {
            return false;
        }
        if (!(grid.getCraftingService() instanceof CraftingService service)) {
            return false;
        }
        var closure = service.peekSimulationClosure(key);
        if (closure == null) {
            return false;
        }
        var result = closure.check(amount, grid.getStorageService().getCachedInventory());
        if (result.guaranteedMissing()) {
            return true;
        }
        rememberSubset(key, grid, craftingRevision(grid), result.subset());
        return false;
    }

    /**
     * Caches a material closure for every slot requesting the given output. Runs on the server thread.
     */
    private void rememberSubset(AEKey output, IGrid grid, long revision, Collection<AEKey> keys) {
        for (int i = 0; i < this.requestManager.size(); i++) {
            if (output.equals(this.requestManager.get(i).getKey())) {
                this.materialSubsets.put(i, output, grid, revision, keys);
            }
        }
    }

    private static long craftingRevision(IGrid grid) {
        return grid.getCraftingService().getCraftablesVersion();
    }

    /**
     * Simulation requester handed to the crafting calculation: forwards the request lifecycle to the tile while
     * answering with the material subset cached for it.
     */
    private final class RequesterSimulationSource implements ICraftingSimulationRequester {
        @Override
        public IActionSource getActionSource() {
            return actionSource;
        }

        @Nullable
        @Override
        public IGridNode getGridNode() {
            return TileRequester.this.getMainNode().getNode();
        }

        @Nullable
        @Override
        public Collection<AEKey> getSimulationSubset(AEKey output) {
            return TileRequester.this.getSimulationSubset(output);
        }
    }

    @Override
    public void onRequestUpdated(int index) {
        if (this.requestStatus[index] instanceof PlanState || this.requestStatus[index] instanceof BlockedState) {
            this.cancelRequest(index);
        } else {
            // Configuration changes invalidate callbacks, but do not cancel an already submitted link.
            this.requestGeneration[index]++;
        }
        this.clearRetry(index);
        if (this.requestManager.get(index).isEnabled()
            && this.requestStatus[index] instanceof NoPatternState) {
            this.setRequestState(index, StatusState.IDLE);
        }
        this.saveChanges();
    }

    @Override
    public IGridNode getActionableNode() {
        return this.getMainNode().getNode();
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 20, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (this.world == null || this.world.isRemote || !this.getMainNode().isActive()) {
            return TickRateModulation.IDLE;
        }

        return this.handleRequests();
    }

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        ImmutableSet.Builder<ICraftingLink> links = ImmutableSet.builder();
        for (int slot = 0; slot < this.requestStatus.length; slot++) {
            StatusState state = this.requestStatus[slot];
            if (state instanceof LinkState(var link)) {
                links.add(link);
            }
            var submission = this.submissions[slot];
            if (submission != null && submission.link != null
                && !(state instanceof LinkState(var link) && link == submission.link)) {
                links.add(submission.link);
            }
        }
        return links.build();
    }

    @Override
    public long insertCraftedItems(ICraftingLink link, AEKey what, long amount, Actionable mode) {
        if (amount <= 0) {
            return 0;
        }

        int slot = this.findLinkSlot(link);
        if (slot < 0) {
            return 0;
        }

        if (mode == Actionable.MODULATE) {
            long accepted = this.storageManager.addPending(slot, what, amount);
            if (accepted > 0) {
                this.receiptSavePending = true;
                this.saveCommittedReceipt();
            }
            return accepted;
        }
        return this.storageManager.getAcceptableAmount(slot, what, amount);
    }

    private void saveCommittedReceipt() {
        if (!this.receiptSavePending || this.savingReceipt) {
            return;
        }
        this.savingReceipt = true;
        this.receiptSavePending = false;
        try {
            this.saveChanges();
            this.receiptSaveFailureLogged = false;
        } catch (RuntimeException failure) {
            // Custody has committed: preserve the accepted receipt and retry only the notification.
            this.receiptSavePending = true;
            if (!this.receiptSaveFailureLogged) {
                this.receiptSaveFailureLogged = true;
                AELog.error(failure, "Failed to save committed requester receipt at " + this.pos);
            }
        } finally {
            this.savingReceipt = false;
        }
    }

    @Override
    public void jobStateChange(ICraftingLink link) {
        boolean settled = false;
        for (int i = 0; i < this.submissions.length; i++) {
            var submission = this.submissions[i];
            if (submission != null && submission.link == link) {
                submission.terminal = true;
                if (!submission.inFlight && !submission.canceling) {
                    this.submissions[i] = null;
                    this.setRequestState(i, this.requestStatus[i]);
                    settled = true;
                }
                break;
            }
        }
        int slot = this.findLinkSlot(link);
        if (slot >= 0) {
            boolean canceled = link.isCanceled();
            if (this.findLinkSlot(link) != slot) {
                return;
            }
            this.setRequestState(slot, canceled ? StatusState.IDLE : StatusState.EXPORT);
            settled = true;
        }
        if (settled) {
            this.receiptSavePending = true;
            this.saveCommittedReceipt();
        }
    }

    TickRateModulation handleRequests() {
        if (!this.canProcessRequests()) {
            return TickRateModulation.IDLE;
        }
        this.saveCommittedReceipt();
        boolean changed = false;
        TickRateModulation tickRateModulation = TickRateModulation.IDLE;
        for (int i = 0; i < this.requestStatus.length; i++) {
            if (!this.canProcessRequests()) {
                break;
            }
            StatusState current = this.requestStatus[i];
            StatusState result = this.handleRequest(i);
            if (!current.equals(result)) {
                changed = true;
            }

            TickRateModulation resultTickRateModulation = result.getTickRateModulation();
            if (resultTickRateModulation.ordinal() > tickRateModulation.ordinal()) {
                tickRateModulation = resultTickRateModulation;
            }

        }
        if (changed) {
            this.receiptSavePending = true;
            this.saveCommittedReceipt();
        }
        return tickRateModulation;
    }

    public boolean isActive() {
        for (int slot = 0; slot < this.requestStatus.length; slot++) {
            if (this.submissions[slot] != null || this.requestStatus[slot].visibleStatus() != RequestStatus.IDLE) {
                return true;
            }
        }
        return false;
    }

    private StatusState handleRequest(int slot) {
        if (this.handlingRequest[slot] || this.submissions[slot] != null) {
            return this.requestStatus[slot];
        }
        this.handlingRequest[slot] = true;
        try {
            while (this.canProcessRequests()) {
                long generation = this.requestGeneration[slot];
                StatusState state = this.requestStatus[slot];
                StatusState result = state.handle(this, slot);
                if (this.submissions[slot] != null) {
                    if (this.requestStatus[slot] == state) {
                        this.setRequestState(slot, StatusState.IDLE);
                    }
                    return this.requestStatus[slot];
                }
                if (generation != this.requestGeneration[slot] || state != this.requestStatus[slot]) {
                    // A canceled BlockedState can acquire a nested Future before its callback returns.
                    if (result != this.requestStatus[slot]
                        && (result instanceof PlanState || result instanceof BlockedState)) {
                        this.cancelState(slot, result);
                    }
                    return this.requestStatus[slot];
                }
                this.setRequestState(slot, result);
                if (generation != this.requestGeneration[slot] || result != this.requestStatus[slot]
                    || result.type() == RequestStatus.IDLE || result.equals(state)) {
                    return this.requestStatus[slot];
                }
            }
            return this.requestStatus[slot];
        } finally {
            this.handlingRequest[slot] = false;
        }
    }

    private void setRequestState(int slot, StatusState state) {
        StatusState previous = this.requestStatus[slot];
        RequestStatus previousVisibleStatus = this.requestManager.get(slot).getClientStatus();
        this.requestStatus[slot] = state;
        var submission = this.submissions[slot];
        var visible = submission != null && (!submission.confirmed || submission.revoked)
            ? RequestStatus.UNCONFIRMED : state.visibleStatus();
        this.requestManager.get(slot).setClientStatus(visible);
        if (!previous.equals(state) || previousVisibleStatus != visible) {
            this.markForUpdate();
        }
    }

    private int findLinkSlot(ICraftingLink link) {
        for (int i = 0; i < this.requestStatus.length; i++) {
            StatusState state = this.requestStatus[i];
            if (state instanceof LinkState(var stateLink) && stateLink == link) {
                var submission = this.submissions[i];
                return submission == null || submission.confirmed && !submission.revoked ? i : -1;
            }
        }
        return -1;
    }

    private NBTTagCompound writeStatusToNBT() {
        var tag = new NBTTagCompound();
        for (int i = 0; i < this.requestStatus.length; i++) {
            StatusState state = this.requestStatus[i];
            if (state instanceof LinkState(var link)) {
                if (this.submissions[i] != null && this.submissions[i].link == link) {
                    continue;
                }
                var linkTag = new NBTTagCompound();
                link.writeToNBT(linkTag);
                tag.setTag(Integer.toString(i), linkTag);
            }
        }
        return tag;
    }

    private void readStatusFromNBT(NBTTagCompound tag) {
        var loaded = new StatusState[this.requestStatus.length];
        Arrays.fill(loaded, StatusState.IDLE);
        var generations = this.requestGeneration.clone();
        var submissions = this.submissions.clone();
        for (int i = 0; i < this.requestStatus.length; i++) {
            String key = Integer.toString(i);
            if (tag.hasKey(key, 10)) {
                ICraftingLink link = StorageHelper.loadCraftingLink(tag.getCompoundTag(key), this);
                if (link != null) {
                    var pending = this.submissions[i];
                    if (pending != null) {
                        if (pending.link == null || !pending.link.getCraftingID().equals(link.getCraftingID())) {
                            throw new IllegalStateException("Cannot replace a requester link with unconfirmed cancellation");
                        }
                    } else {
                        loaded[i] = new LinkState(link);
                    }
                }
            }
        }
        if (!Arrays.equals(generations, this.requestGeneration) || !Arrays.equals(submissions, this.submissions)) {
            throw new IllegalStateException("Requester ownership changed during link restoration");
        }
        boolean changed = false;
        // Publish all local rows before one notification can reenter and install newer state.
        for (int i = 0; i < loaded.length; i++) {
            var visible = this.submissions[i] == null ? loaded[i].visibleStatus() : RequestStatus.UNCONFIRMED;
            changed |= !this.requestStatus[i].equals(loaded[i])
                || this.requestManager.get(i).getClientStatus() != visible;
            this.requestStatus[i] = loaded[i];
            this.requestManager.get(i).setClientStatus(visible);
        }
        if (changed) {
            this.markForUpdate();
        }
    }

    private void restorePersistentStatus() {
        for (int i = 0; i < this.requestStatus.length; i++) {
            if (!this.requestManager.get(i).isEnabled()
                && this.requestManager.get(i).getClientStatus() == RequestStatus.NO_PATTERN) {
                this.setRequestState(i, new NoPatternState());
            }
            if (this.submissions[i] != null) {
                this.requestManager.get(i).setClientStatus(RequestStatus.UNCONFIRMED);
            }
        }
    }

    @Override
    public boolean canForceStartCrafting(ICraftingPlan plan) {
        return this.submittingForceStart;
    }

    public boolean hasIdleCpu() {
        return this.services.hasIdleCpu();
    }

    public boolean shouldDelayRetry(int slot, RequestStatus status) {
        long now = TickHandler.instance().getCurrentTick();
        if (status == RequestStatus.MISSING) {
            return now < this.missingRetryUntil[slot];
        }
        if (status == RequestStatus.CPU) {
            return !this.hasIdleCpu() || now < this.cpuRetryUntil[slot];
        }
        return false;
    }

    public void markMissingRetry(int slot) {
        this.missingRetryUntil[slot] = TickHandler.instance().getCurrentTick() + MISSING_RETRY_TICKS;
    }

    public void markCpuTooSmallRetry(int slot) {
        this.cpuRetryUntil[slot] = TickHandler.instance().getCurrentTick() + CPU_TOO_SMALL_RETRY_TICKS;
    }

    public void clearMissingRetry(int slot) {
        this.missingRetryUntil[slot] = 0;
    }

    public void clearRetry(int slot) {
        this.missingRetryUntil[slot] = 0;
        this.cpuRetryUntil[slot] = 0;
    }

    public StatusState disableRequestForNoPattern(int slot) {
        long generation = this.requestGeneration[slot] + 1;
        this.cancelRequest(slot);
        if (generation != this.requestGeneration[slot]) {
            return this.requestStatus[slot];
        }
        this.clearRetry(slot);
        boolean willUpdate = this.requestManager.get(slot).isEnabled()
            || this.requestManager.get(slot).getClientStatus() != RequestStatus.NO_PATTERN;
        this.requestManager.get(slot).disableWithStatus(RequestStatus.NO_PATTERN);
        if (generation + (willUpdate ? 1 : 0) == this.requestGeneration[slot]) {
            this.setRequestState(slot, new NoPatternState());
        }
        return this.requestStatus[slot];
    }

    public long getStoredAmount(AEKey key) {
        return this.services.getStoredAmount(key);
    }

    /**
     * Server-owner token for checking callbacks before publishing or submitting uncommitted work.
     */
    public long getRequestGeneration(int slot) {
        return this.requestGeneration[slot];
    }

    /**
     * Capture on the server owner before starting a calculation; never pass this live context to a worker.
     */
    public CalculationSource captureCalculationSource(int slot) {
        return new CalculationSource(slot, this.requestGeneration[slot], this.getMainNode().getGrid(),
            this.getMainNode().getNode(), this.world);
    }

    public boolean isCalculationSourceCurrent(CalculationSource source) {
        return this.canProcessRequests() && source.generation() == this.requestGeneration[source.slot()]
            && source.grid() == this.getMainNode().getGrid()
            && source.node() == this.getMainNode().getNode() && source.world() == this.world
            && this.canProcessRequests() && source.generation() == this.requestGeneration[source.slot()];
    }

    public Future<ICraftingPlan> beginPlan(AEKey key, long amount) {
        if (!this.canProcessRequests()) {
            return CompletableFuture.completedFuture(null);
        }
        return this.services.beginPlan(key, amount);
    }

    public boolean canProcessRequests() {
        return !this.requestsClosed && this.cancellationDepth == 0;
    }

    public ICraftingSubmitResult submitPlan(ICraftingPlan plan, int slot) {
        if (!this.canProcessRequests()) {
            return new SubmitResult(CraftingSubmitErrorCode.NO_CPU_FOUND, null);
        }
        this.submittingForceStart = this.requestManager.get(slot).isForceStart();
        try {
            return this.services.submitPlan(plan, this, this, this.submittingForceStart);
        } finally {
            this.submittingForceStart = false;
        }
    }

    /**
     * Own the submission before invoking services; an uncertain receipt must never re-submit the plan.
     */
    public StatusState submitPlan(ICraftingPlan plan, int slot, CalculationSource source, GenericStack output) {
        if (this.submissions[slot] != null) {
            return this.requestStatus[slot];
        }
        var submission = new Submission(output, source.generation(), this.lifecycleGeneration);
        if (!this.isCalculationSourceCurrent(source)) {
            return this.requestStatus[slot];
        }
        StatusState previous = this.requestStatus[slot];
        this.submissions[slot] = submission;
        this.requestManager.get(slot).setClientStatus(RequestStatus.UNCONFIRMED);
        try {
            var result = this.submitPlan(plan, slot);
            submission.link = result.link();
            boolean successful = result.successful();
            if (submission.terminal) {
                this.submissions[slot] = null;
                return this.requestStatus[slot] == previous ? StatusState.IDLE : this.requestStatus[slot];
            }
            if (successful && submission.link != null) {
                if (!submission.revoked && this.isCalculationSourceCurrent(source)) {
                    this.storageManager.setTotalAmount(slot, output.what(), output.amount());
                    if (!submission.revoked && this.isCalculationSourceCurrent(source)) {
                        var state = new LinkState(submission.link);
                        submission.confirmed = true;
                        try {
                            this.setRequestState(slot, state);
                        } catch (RuntimeException failure) {
                            AELog.error(failure, "Failed to notify a committed requester submission");
                        }
                        if (submission.terminal) {
                            this.submissions[slot] = null;
                            return this.requestStatus[slot];
                        }
                        if (!submission.revoked && this.requestStatus[slot] == state
                            && this.isCalculationSourceCurrent(source)) {
                            this.submissions[slot] = null;
                            return state;
                        }
                    }
                }
                submission.revoked = true;
                this.cancelSubmission(slot, submission);
                return this.requestStatus[slot] == previous ? StatusState.IDLE : this.requestStatus[slot];
            }
            if (submission.link != null) {
                submission.revoked = true;
                this.cancelSubmission(slot, submission);
                return this.requestStatus[slot] == previous ? StatusState.IDLE : this.requestStatus[slot];
            }
            if (!successful) {
                var code = result.errorCode();
                if (submission.revoked || !this.isCalculationSourceCurrent(source)) {
                    if (code != null) {
                        this.submissions[slot] = null;
                    }
                    return this.requestStatus[slot];
                }
                Object detail = code == CraftingSubmitErrorCode.NO_SUITABLE_CPU_FOUND ? result.errorDetail() : null;
                if (code != null) {
                    this.submissions[slot] = null;
                    if (!submission.revoked && this.isCalculationSourceCurrent(source)) {
                        return this.submissionFailure(slot, code, detail);
                    }
                    return this.requestStatus[slot];
                }
            }
            AELog.warn("Requester submission returned no definite receipt; automatic retry stopped");
            return StatusState.IDLE;
        } catch (RuntimeException failure) {
            AELog.error(failure, "Requester submission is unconfirmed; automatic retry stopped");
            return this.requestStatus[slot];
        } finally {
            submission.inFlight = false;
            if (this.submissions[slot] == submission && this.requestStatus[slot] == previous) {
                this.requestStatus[slot] = StatusState.IDLE;
            }
            this.requestManager.get(slot).setClientStatus(this.submissions[slot] != null
                ? RequestStatus.UNCONFIRMED : this.requestStatus[slot].visibleStatus());
            this.receiptSavePending = true;
            this.saveCommittedReceipt();
        }
    }

    private StatusState submissionFailure(int slot, CraftingSubmitErrorCode code, @Nullable Object detail) {
        if (code == CraftingSubmitErrorCode.CPU_TOO_SMALL || code == CraftingSubmitErrorCode.NO_SUITABLE_CPU_FOUND
            && detail instanceof UnsuitableCpus cpus && cpus.tooSmall() > 0) {
            this.markCpuTooSmallRetry(slot);
            return BlockedState.cpu();
        }
        return switch (code) {
            case NO_CPU_FOUND, NO_SUITABLE_CPU_FOUND, CPU_BUSY, CPU_OFFLINE -> BlockedState.cpu();
            case NO_CRAFTING_PATTERN -> this.disableRequestForNoPattern(slot);
            case MISSING_INGREDIENT, INCOMPLETE_PLAN -> {
                this.markMissingRetry(slot);
                yield BlockedState.missing();
            }
            default -> StatusState.IDLE;
        };
    }

    private void cancelSubmission(int slot, Submission submission) {
        if (submission.link == null || submission.canceling) {
            return;
        }
        submission.canceling = true;
        this.cancellationDepth++;
        try {
            submission.link.cancel();
            if ((submission.terminal || submission.link.isCanceled() || submission.link.isDone())
                && this.submissions[slot] == submission) {
                this.submissions[slot] = null;
            }
        } catch (RuntimeException failure) {
            AELog.error(failure, "Requester submission cancellation is unconfirmed; retaining its link");
        } finally {
            this.cancellationDepth--;
            submission.canceling = false;
            if (submission.terminal && this.submissions[slot] == submission) {
                this.submissions[slot] = null;
            }
        }
    }

    private void readSubmissions(NBTTagCompound tag) {
        long lifecycle = this.lifecycleGeneration;
        var generations = this.requestGeneration.clone();
        var previous = this.submissions.clone();
        var loaded = new Submission[this.submissions.length];
        for (int slot = 0; slot < loaded.length; slot++) {
            String key = Integer.toString(slot);
            if (tag.hasKey(key)) {
                loaded[slot] = new Submission(tag.getTag(key), this);
                if (loaded[slot].terminal) {
                    loaded[slot] = null;
                }
            }
            var current = this.submissions[slot];
            if (current != null) {
                if (loaded[slot] != null && !current.sameSubmission(loaded[slot])) {
                    throw new IllegalStateException("Cannot replace an unconfirmed requester submission");
                }
                loaded[slot] = current;
            }
        }
        if (lifecycle != this.lifecycleGeneration || !Arrays.equals(generations, this.requestGeneration)
            || !Arrays.equals(previous, this.submissions)) {
            throw new IllegalStateException("Requester ownership changed during submission restoration");
        }
        System.arraycopy(loaded, 0, this.submissions, 0, loaded.length);
    }

    private void resetRuntimeState() {
        for (int i = 0; i < this.requestStatus.length; i++) {
            this.cancelRequest(i);
            this.storageManager.clearConfiguration(i);
            this.clearRetry(i);
            this.setRequestState(i, StatusState.IDLE);
        }
        this.materialSubsets.clearAll();
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops) {
        this.requestsClosed = true;
        this.lifecycleGeneration++;
        this.cancelAllRequests();
        super.addAdditionalDrops(drops);
        for (int slot = 0; slot < this.requestStatus.length; slot++) {
            var submission = this.submissions[slot];
            if (submission != null) {
                var recovery = AEItems.MISSING_CONTENT.stack();
                var tag = new NBTTagCompound();
                tag.setInteger("requester_recovery_schema", 1);
                tag.setInteger("requester_slot", slot);
                tag.setLong("requester_position", this.pos.toLong());
                tag.setTag("requester_submission", submission.writeRecoveryToNBT());
                tag.setString("missing_content_error",
                    "Unconfirmed requester operation; original data retained. Check the crafting CPU; do not refund or replay.");
                recovery.setTagCompound(tag);
                if (drops.add(recovery)) {
                    submission.recoveryCollected = true;
                }
            }
            for (var stack : this.storageManager.getBufferedStacks(slot)) {
                GenericResourcePackageItem.addDrop(stack.what(), stack.amount(), drops);
            }
        }
    }

    public long insert(AEKey key, long amount) {
        return this.services.insert(key, amount);
    }

    @Override
    public void clearContent() {
        this.requestsClosed = true;
        this.lifecycleGeneration++;
        this.cancelAllRequests();
        super.clearContent();
        for (int slot = 0; slot < this.requestStatus.length; slot++) {
            this.storageManager.clear(slot);
            var submission = this.submissions[slot];
            if (submission != null && submission.recoveryCollected && !submission.inFlight) {
                this.submissions[slot] = null;
                this.requestManager.get(slot).setClientStatus(this.requestStatus[slot].visibleStatus());
            }
        }
    }

    @Override
    public void onChunkUnloaded() {
        this.requestsClosed = true;
        this.lifecycleGeneration++;
        this.cancelAllRequests();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        this.requestsClosed = true;
        this.lifecycleGeneration++;
        this.cancelAllRequests();
        super.setRemoved();
    }

    @Override
    public void onReady() {
        this.requestsClosed = true;
        long generation = this.lifecycleGeneration;
        super.onReady();
        if (generation == this.lifecycleGeneration) {
            this.requestsClosed = false;
        }
    }

    private void cancelRequest(int slot) {
        this.cancellationDepth++;
        try {
            this.cancelRequestState(slot);
        } finally {
            this.cancellationDepth--;
        }
    }

    private void cancelRequestState(int slot) {
        this.requestGeneration[slot]++;
        StatusState state = this.requestStatus[slot];
        var submission = this.submissions[slot];
        if (state instanceof LinkState(var link) && submission == null) {
            // Transfer the committed link before any external cancellation or serialization callback.
            submission = new Submission(link, this.requestGeneration[slot], this.lifecycleGeneration);
            this.submissions[slot] = submission;
        }
        if (submission != null) {
            submission.revoked = true;
        }
        boolean visibleChanged = this.requestManager.get(slot).getClientStatus() != RequestStatus.IDLE;
        // Detach before invoking extension cancellation callbacks, including nested calculations.
        this.requestStatus[slot] = StatusState.IDLE;
        this.requestManager.get(slot).setClientStatus(submission == null ? RequestStatus.IDLE : RequestStatus.UNCONFIRMED);
        try {
            if (!(state instanceof LinkState(var link) && submission != null && submission.link == link)) {
                this.cancelState(slot, state);
            }
            if (submission != null && !submission.inFlight) {
                this.cancelSubmission(slot, submission);
            }
        } finally {
            if (submission != null) {
                this.requestManager.get(slot).setClientStatus(this.submissions[slot] != null
                    ? RequestStatus.UNCONFIRMED : this.requestStatus[slot].visibleStatus());
                this.receiptSavePending = true;
                this.saveCommittedReceipt();
            }
        }
        if (visibleChanged) {
            try {
                this.markForUpdate();
            } catch (RuntimeException failure) {
                AELog.error(failure, "Failed to notify canceled requester state at " + this.pos + " slot " + slot);
            }
        }
    }

    private void cancelAllRequests() {
        for (int i = 0; i < this.requestStatus.length; i++) {
            this.cancelRequest(i);
        }
    }

    private void cancelState(int slot, StatusState state) {
        this.cancellationDepth++;
        try {
            state.cancel();
        } catch (RuntimeException failure) {
            AELog.error(failure, "Failed to cancel requester state at " + this.pos + " slot " + slot);
        } finally {
            this.cancellationDepth--;
        }
    }

    public record CalculationSource(int slot, long generation, @Nullable IGrid grid,
                                    @Nullable IGridNode node, @Nullable World world) {
    }

    private static final class Submission {
        private final NBTBase original;
        @Nullable
        private ICraftingLink link;
        private boolean inFlight;
        private boolean confirmed;
        private boolean revoked;
        private boolean terminal;
        private boolean canceling;
        private boolean recoveryCollected;

        private Submission(GenericStack output, long generation, long lifecycleGeneration) {
            var tag = new NBTTagCompound();
            tag.setInteger("schema", 1);
            tag.setUniqueId("submissionId", UUID.randomUUID());
            tag.setLong("generation", generation);
            tag.setLong("lifecycleGeneration", lifecycleGeneration);
            tag.setTag("request", GenericStack.writeTag(output));
            this.original = tag;
            this.inFlight = true;
        }

        private Submission(@Nullable ICraftingLink link, long generation, long lifecycleGeneration) {
            var tag = new NBTTagCompound();
            tag.setInteger("schema", 1);
            tag.setUniqueId("submissionId", UUID.randomUUID());
            tag.setLong("generation", generation);
            tag.setLong("lifecycleGeneration", lifecycleGeneration);
            tag.setString("operation", "cancel");
            this.original = tag;
            this.link = link;
            this.revoked = true;
        }

        private Submission(NBTBase original, TileRequester owner) {
            this.original = original.copy();
            this.revoked = true;
            if (original instanceof NBTTagCompound tag && tag.getInteger("schema") == 1
                && tag.hasKey("link", 10)) {
                try {
                    this.link = StorageHelper.loadCraftingLink(tag.getCompoundTag("link"), owner);
                    this.terminal = this.link.isCanceled() || this.link.isDone();
                } catch (RuntimeException failure) {
                    AELog.error(failure, "Retaining unreadable requester submission receipt");
                }
            }
        }

        private boolean sameSubmission(Submission other) {
            return this.original.equals(other.original) || this.original instanceof NBTTagCompound first
                && other.original instanceof NBTTagCompound second && first.getInteger("schema") == 1
                && second.getInteger("schema") == 1 && first.hasUniqueId("submissionId")
                && second.hasUniqueId("submissionId")
                && Objects.requireNonNull(first.getUniqueId("submissionId")).equals(second.getUniqueId("submissionId"))
                && first.getLong("generation") == second.getLong("generation")
                && first.getLong("lifecycleGeneration") == second.getLong("lifecycleGeneration")
                && first.getString("operation").equals(second.getString("operation"))
                && first.getCompoundTag("request").equals(second.getCompoundTag("request"))
                && (Objects.equals(first.getTag("link"), second.getTag("link"))
                || !first.hasKey("link") && this.link != null && other.link != null
                && this.link.getCraftingID().equals(other.link.getCraftingID()));
        }

        private NBTBase writeToNBT() {
            var copy = this.original.copy();
            if (copy instanceof NBTTagCompound tag && tag.getInteger("schema") == 1 && this.link != null) {
                var linkTag = new NBTTagCompound();
                this.link.writeToNBT(linkTag);
                tag.setTag("link", linkTag);
            }
            return copy;
        }

        private NBTBase writeRecoveryToNBT() {
            var copy = this.original.copy();
            if (copy instanceof NBTTagCompound tag && tag.getInteger("schema") == 1 && this.link != null) {
                try {
                    var id = this.link.getCraftingID();
                    if (id != null) {
                        tag.setUniqueId("knownCraftId", id);
                    }
                } catch (RuntimeException failure) {
                    tag.setBoolean("craftIdUnavailable", true);
                    AELog.error(failure, "Could not read requester recovery link identity");
                }
                try {
                    var linkTag = new NBTTagCompound();
                    this.link.writeToNBT(linkTag);
                    tag.setTag("link", linkTag);
                } catch (RuntimeException failure) {
                    tag.setBoolean("linkSerializationFailed", true);
                    AELog.error(failure, "Preserving original requester submission after link serialization failure");
                }
            }
            return copy;
        }
    }

    interface RequesterServices {
        long getStoredAmount(AEKey key);

        boolean hasIdleCpu();

        Future<ICraftingPlan> beginPlan(AEKey key, long amount);

        ICraftingSubmitResult submitPlan(ICraftingPlan plan, ICraftingRequester requester, IActionHost actionHost,
                                         boolean forceStart);

        long insert(AEKey key, long amount);
    }

    private record SubmitResult(CraftingSubmitErrorCode errorCode, ICraftingLink link)
        implements ICraftingSubmitResult {
        @Override
        public Object errorDetail() {
            return null;
        }
    }

    private final class GridRequesterServices implements RequesterServices {
        @Override
        public long getStoredAmount(AEKey key) {
            var grid = getMainNode().getGrid();
            return grid == null ? 0 : grid.getStorageService().getCachedInventory().get(key);
        }

        @Override
        public boolean hasIdleCpu() {
            var grid = getMainNode().getGrid();
            if (grid == null) {
                return false;
            }
            for (var cpu : grid.getCraftingService().getCpus()) {
                if (!cpu.isBusy()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Future<ICraftingPlan> beginPlan(AEKey key, long amount) {
            var grid = getMainNode().getGrid();
            if (grid == null || world == null) {
                return CompletableFuture.completedFuture(null);
            }
            return grid.getCraftingService().beginCraftingCalculation(world, simulationSource,
                key, amount, CalculationStrategy.REPORT_MISSING_ITEMS);
        }

        @Override
        public ICraftingSubmitResult submitPlan(ICraftingPlan plan, ICraftingRequester requester,
                                                IActionHost actionHost, boolean forceStart) {
            var grid = getMainNode().getGrid();
            if (grid == null) {
                return new SubmitResult(CraftingSubmitErrorCode.NO_CPU_FOUND, null);
            }
            return grid.getCraftingService().submitJob(plan, requester, null, false, actionSource, forceStart);
        }

        @Override
        public long insert(AEKey key, long amount) {
            var grid = getMainNode().getGrid();
            if (grid == null) {
                return 0;
            }
            return StorageHelper.poweredInsert(grid.getEnergyService(), grid.getStorageService().getInventory(),
                key, amount, actionSource, Actionable.MODULATE);
        }
    }

}
