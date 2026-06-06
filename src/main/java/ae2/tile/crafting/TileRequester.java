package ae2.tile.crafting;

import ae2.api.config.Actionable;
import ae2.api.networking.GridFlags;
import ae2.api.networking.IGridNode;
import ae2.api.networking.crafting.CalculationStrategy;
import ae2.api.networking.crafting.CraftingSubmitErrorCode;
import ae2.api.networking.crafting.ICraftingForceStartRequester;
import ae2.api.networking.crafting.ICraftingLink;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingRequester;
import ae2.api.networking.crafting.ICraftingSubmitResult;
import ae2.api.networking.security.IActionHost;
import ae2.api.networking.security.IActionSource;
import ae2.api.networking.storage.IStorageWatcherNode;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.orientation.BlockOrientation;
import ae2.api.orientation.RelativeSide;
import ae2.api.stacks.AEKey;
import ae2.api.storage.StorageHelper;
import ae2.core.AEConfig;
import ae2.core.definitions.AEBlocks;
import ae2.hooks.ticking.TickHandler;
import ae2.text.TextComponentItemStack;
import ae2.tile.crafting.requester.LinkState;
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
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class TileRequester extends AENetworkedTile implements RequestHost, IGridTickable, ICraftingForceStartRequester {

    private static final int MISSING_RETRY_TICKS = 100;
    private static final int CPU_TOO_SMALL_RETRY_TICKS = 100;

    private static final String REQUESTS_TAG = "requests";
    private static final String STORAGE_MANAGER_TAG = "storage_manager";
    private static final String REQUEST_STATUS_TAG = "request_status";
    private static final String MEMORY_CARD_REQUESTS_TAG = "requester_requests";

    private final RequestList requestManager;
    private final RequesterStorageTracker storageManager;
    private final StatusState[] requestStatus;
    private final long[] missingRetryUntil;
    private final long[] cpuRetryUntil;
    private final IActionSource actionSource = IActionSource.ofMachine(this);
    private final RequesterServices services = new GridRequesterServices();
    private boolean submittingForceStart;

    public TileRequester() {
        int requestCount = AEConfig.instance().getRequests();
        this.requestManager = new RequestList(this, requestCount);
        this.storageManager = new RequesterStorageTracker(requestCount, this::clearMissingRetry);
        this.requestStatus = new StatusState[requestCount];
        this.missingRetryUntil = new long[requestCount];
        this.cpuRetryUntil = new long[requestCount];

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
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        data.setTag(REQUESTS_TAG, this.requestManager.writeToNBT());
        data.setTag(STORAGE_MANAGER_TAG, this.storageManager.writeToNBT());
        data.setTag(REQUEST_STATUS_TAG, this.writeStatusToNBT());
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
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
            this.requestManager.replaceFromNBT(input.getCompoundTag(MEMORY_CARD_REQUESTS_TAG));
            this.resetRuntimeState();
            this.saveChanges();
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
            ? new TextComponentString(this.getCustomName())
            : TextComponentItemStack.of(AEBlocks.REQUESTER.stack());
    }

    @Override
    public long getRequesterSortValue() {
        return ((long) this.pos.getZ() << 24) ^ ((long) this.pos.getX() << 8) ^ this.pos.getY();
    }

    @Override
    public void onRequestChanged(int index) {
        this.cancelRequest(index);
        this.storageManager.clear(index);
        this.clearRetry(index);
        this.setRequestState(index, StatusState.IDLE);
        this.saveChanges();
    }

    @Override
    public void onRequestUpdated(int index) {
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
        for (StatusState state : this.requestStatus) {
            if (state instanceof LinkState(var link)) {
                links.add(link);
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
            this.storageManager.addPending(slot, what, amount);
            this.saveChanges();
        }
        return amount;
    }

    @Override
    public void jobStateChange(ICraftingLink link) {
        int slot = this.findLinkSlot(link);
        if (slot >= 0) {
            this.setRequestState(slot, link.isCanceled() ? StatusState.IDLE : StatusState.EXPORT);
            this.saveChanges();
        }
    }

    TickRateModulation handleRequests() {
        boolean changed = false;
        TickRateModulation tickRateModulation = TickRateModulation.IDLE;
        for (int i = 0; i < this.requestStatus.length; i++) {
            StatusState current = this.requestStatus[i];
            StatusState result = this.handleRequest(i);
            if (!current.equals(result)) {
                changed = true;
            }

            TickRateModulation resultTickRateModulation = result.getTickRateModulation();
            if (resultTickRateModulation.ordinal() > tickRateModulation.ordinal()) {
                tickRateModulation = resultTickRateModulation;
            }

            this.setRequestState(i, result);
        }
        if (changed) {
            this.saveChanges();
        }
        return tickRateModulation;
    }

    public boolean isActive() {
        for (StatusState state : this.requestStatus) {
            if (state.visibleStatus() != RequestStatus.IDLE) {
                return true;
            }
        }
        return false;
    }

    private StatusState handleRequest(int slot) {
        StatusState state = this.requestStatus[slot];
        StatusState result = state.handle(this, slot);
        this.setRequestState(slot, result);
        if (this.requestStatus[slot].type() != RequestStatus.IDLE && !this.requestStatus[slot].equals(state)) {
            return this.handleRequest(slot);
        }
        return this.requestStatus[slot];
    }

    private void setRequestState(int slot, StatusState state) {
        StatusState previous = this.requestStatus[slot];
        RequestStatus previousVisibleStatus = this.requestManager.get(slot).getClientStatus();
        this.requestStatus[slot] = state;
        this.requestManager.get(slot).setClientStatus(state.visibleStatus());
        if (!previous.equals(state) || previousVisibleStatus != state.visibleStatus()) {
            this.markForUpdate();
        }
    }

    private int findLinkSlot(ICraftingLink link) {
        for (int i = 0; i < this.requestStatus.length; i++) {
            StatusState state = this.requestStatus[i];
            if (state instanceof LinkState(var stateLink) && stateLink.equals(link)) {
                return i;
            }
        }
        return -1;
    }

    private NBTTagCompound writeStatusToNBT() {
        var tag = new NBTTagCompound();
        for (int i = 0; i < this.requestStatus.length; i++) {
            StatusState state = this.requestStatus[i];
            if (state instanceof LinkState(var link)) {
                var linkTag = new NBTTagCompound();
                link.writeToNBT(linkTag);
                tag.setTag(Integer.toString(i), linkTag);
            }
        }
        return tag;
    }

    private void readStatusFromNBT(NBTTagCompound tag) {
        Arrays.fill(this.requestStatus, StatusState.IDLE);
        for (int i = 0; i < this.requestStatus.length; i++) {
            String key = Integer.toString(i);
            if (tag.hasKey(key, 10)) {
                ICraftingLink link = StorageHelper.loadCraftingLink(tag.getCompoundTag(key), this);
                if (link != null) {
                    this.setRequestState(i, new LinkState(link));
                }
            }
        }
    }

    private void restorePersistentStatus() {
        for (int i = 0; i < this.requestStatus.length; i++) {
            if (!this.requestManager.get(i).isEnabled()
                && this.requestManager.get(i).getClientStatus() == RequestStatus.NO_PATTERN) {
                this.setRequestState(i, new NoPatternState());
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
        this.cancelRequest(slot);
        this.clearRetry(slot);
        this.requestManager.get(slot).disableWithStatus(RequestStatus.NO_PATTERN);
        return new NoPatternState();
    }

    public long getStoredAmount(AEKey key) {
        return this.services.getStoredAmount(key);
    }

    public Future<ICraftingPlan> beginPlan(AEKey key, long amount) {
        return this.services.beginPlan(key, amount);
    }

    public ICraftingSubmitResult submitPlan(ICraftingPlan plan, int slot) {
        this.submittingForceStart = this.requestManager.get(slot).isForceStart();
        try {
            return this.services.submitPlan(plan, this, this, this.submittingForceStart);
        } finally {
            this.submittingForceStart = false;
        }
    }

    public long insert(AEKey key, long amount) {
        return this.services.insert(key, amount);
    }

    private void resetRuntimeState() {
        for (int i = 0; i < this.requestStatus.length; i++) {
            this.cancelRequest(i);
            this.storageManager.clear(i);
            this.clearRetry(i);
            this.setRequestState(i, StatusState.IDLE);
        }
    }

    @Override
    public void onChunkUnloaded() {
        this.cancelAllRequests();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        this.cancelAllRequests();
        super.setRemoved();
    }

    private void cancelAllRequests() {
        for (int i = 0; i < this.requestStatus.length; i++) {
            this.cancelRequest(i);
        }
    }

    private void cancelRequest(int slot) {
        StatusState state = this.requestStatus[slot];
        if (state instanceof LinkState(var link)) {
            link.cancel();
        } else if (state instanceof PlanState(var future)) {
            future.cancel(true);
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
            return grid.getCraftingService().getCpus().stream().anyMatch(cpu -> !cpu.isBusy());
        }

        @Override
        public Future<ICraftingPlan> beginPlan(AEKey key, long amount) {
            var grid = getMainNode().getGrid();
            if (grid == null || world == null) {
                return CompletableFuture.completedFuture(null);
            }
            return grid.getCraftingService().beginCraftingCalculation(world, TileRequester.this::getActionSource,
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
