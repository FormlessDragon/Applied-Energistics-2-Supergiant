package ae2.parts.storagebus;

import ae2.api.AECapabilities;
import ae2.api.behaviors.ExternalStorageStrategy;
import ae2.api.config.Actionable;
import ae2.api.config.AccessRestriction;
import ae2.api.config.FuzzyMode;
import ae2.api.config.IncludeExclude;
import ae2.api.config.Setting;
import ae2.api.config.Settings;
import ae2.api.config.StorageFilter;
import ae2.api.config.YesNo;
import ae2.api.features.IPlayerRegistry;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.security.IActionSource;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.parts.IPartCollisionHelper;
import ae2.api.parts.IPartHost;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.IStorageMounts;
import ae2.api.storage.IStorageProvider;
import ae2.api.storage.MEStorage;
import ae2.api.storage.MEStorageChangeListener;
import ae2.api.storage.MEStorageMonitor;
import ae2.api.util.AECableType;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigManagerBuilder;
import ae2.container.GuiIds;
import ae2.container.ISubGui;
import ae2.core.AppEng;
import ae2.core.AELog;
import ae2.core.definitions.AEItems;
import ae2.core.gui.GuiOpener;
import ae2.core.settings.TickRates;
import ae2.helpers.IConfigInvHost;
import ae2.helpers.IPriorityHost;
import ae2.helpers.InterfaceLogicHost;
import ae2.items.parts.PartModels;
import ae2.me.helpers.MachineSource;
import ae2.me.storage.CompositeStorage;
import ae2.me.storage.ITickingMonitor;
import ae2.me.storage.MEInventoryHandler;
import ae2.me.storage.NullInventory;
import ae2.parts.PartAdjacentApi;
import ae2.parts.PartModel;
import ae2.parts.automation.StackWorldBehaviors;
import ae2.parts.automation.UpgradeablePart;
import ae2.util.ConfigInventory;
import ae2.util.Platform;
import ae2.util.prioritylist.DefaultPriorityList;
import ae2.util.prioritylist.FuzzyPriorityList;
import ae2.util.prioritylist.IPartitionList;
import ae2.util.prioritylist.PrecisePriorityList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatBasic;
import net.minecraft.stats.StatList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldServer;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

public class StorageBusPart extends UpgradeablePart
    implements IGridTickable, IStorageProvider, IPriorityHost, IConfigInvHost {

    public static final ResourceLocation MODEL_BASE = AppEng.makeId("part/storage_bus_base");

    @PartModels
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, AppEng.makeId("part/storage_bus_off"));

    @PartModels
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, AppEng.makeId("part/storage_bus_on"));

    @PartModels
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE,
        AppEng.makeId("part/storage_bus_has_channel"));
    private static final ResourceLocation RECURSIVE_NETWORKING_STAT_ID = AppEng.makeId("recursive_networking");
    private static final String RECURSIVE_NETWORKING_STAT_KEY = "stat."
        + RECURSIVE_NETWORKING_STAT_ID.getNamespace()
        + "."
        + RECURSIVE_NETWORKING_STAT_ID.getPath();
    private static StatBase recursiveNetworkingStat;

    protected final IActionSource source;
    protected final StorageBusInventory handler = createHandler();
    private final PartAdjacentApi<MEStorage> adjacentStorageAccessor;
    @Nullable
    private ITextComponent handlerDescription;
    @Nullable
    private Map<AEKeyType, ExternalStorageStrategy> externalStorageStrategies;
    private boolean wasOnline;
    private final ConfigInventory config = ConfigInventory.configTypes(63)
                                                          .changeListener(this::onConfigurationChanged)
                                                          .build();
    private int priority;
    private PendingUpdateStatus updateStatus = PendingUpdateStatus.FAST_UPDATE;
    private boolean externalStorageExtractableOnly;
    @Nullable
    private ITickingMonitor monitor;
    public StorageBusPart(IPartItem<?> partItem) {
        super(partItem);
        this.adjacentStorageAccessor = new PartAdjacentApi<>(this, AECapabilities.ME_STORAGE,
            this::onCapabilityInvalidation);
        this.source = new MachineSource(this);
        getMainNode().addService(IStorageProvider.class, this).addService(IGridTickable.class, this);
    }

    @Override
    public void removeFromWorld() {
        this.handler.removeAllListeners();
        super.removeFromWorld();
    }

    private static synchronized StatBase getRecursiveNetworkingStat() {
        if (recursiveNetworkingStat == null) {
            StatBase existing = StatList.getOneShotStat(RECURSIVE_NETWORKING_STAT_KEY);
            if (existing == null) {
                existing = new StatBasic(RECURSIVE_NETWORKING_STAT_KEY,
                    new TextComponentTranslation(RECURSIVE_NETWORKING_STAT_KEY))
                    .initIndependentStat()
                    .registerStat();
            }
            recursiveNetworkingStat = existing;
        }

        return recursiveNetworkingStat;
    }

    @Override
    protected void registerSettings(IConfigManagerBuilder builder) {
        super.registerSettings(builder);
        builder.registerSetting(Settings.ACCESS, AccessRestriction.READ_WRITE);
        builder.registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        builder.registerSetting(Settings.STORAGE_FILTER, StorageFilter.EXTRACTABLE_ONLY);
        builder.registerSetting(Settings.FILTER_ON_EXTRACT, YesNo.YES);
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        scheduleUpdate();
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);

        boolean currentOnline = getMainNode().isOnline();
        if (this.wasOnline != currentOnline) {
            this.wasOnline = currentOnline;
            this.getHost().markForUpdate();
            remountStorage();
        }
    }

    protected void remountStorage() {
        IStorageProvider.requestUpdate(getMainNode());
    }

    @Override
    protected void onSettingChanged(IConfigManager manager, Setting<?> setting) {
        this.onConfigurationChanged();
        this.getHost().markForSave();
    }

    @Override
    public void upgradesChanged() {
        super.upgradesChanged();
        this.onConfigurationChanged();
    }

    protected void scheduleUpdate() {
        if (isClientSide()) {
            return;
        }

        this.updateStatus = PendingUpdateStatus.FAST_UPDATE;
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.priority = data.getInteger("priority");
        this.config.readFromChildTag(data, "config");
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setInteger("priority", this.priority);
        this.config.writeToChildTag(data, "config");
    }

    @Override
    public boolean onUseWithoutItem(EntityPlayer player, Vec3d pos) {
        if (!isClientSide()) {
            openConfigGui(player);
        }
        return true;
    }

    @Override
    public boolean onUseItemOn(ItemStack heldItem, EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (super.onUseItemOn(heldItem, player, hand, pos)) {
            return true;
        }
        return this.onUseWithoutItem(player, pos);
    }

    protected final void openConfigGui(EntityPlayer player) {
        GuiOpener.openPartGui(player, getGuiKey(), this);
    }

    @Override
    public void returnToMainContainer(EntityPlayer player, ISubGui subGui) {
        GuiOpener.openPartGui(player, getGuiKey(), this, true);
    }

    @Override
    public ItemStack getMainContainerIcon() {
        return getPartItem().asItemStack();
    }

    public GuiIds.GuiKey getGuiKey() {
        return GuiIds.GuiKey.STORAGE_BUS;
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(3, 3, 15, 13, 13, 16);
        bch.addBox(2, 2, 14, 14, 14, 15);
        bch.addBox(5, 5, 12, 11, 11, 14);
    }

    @Override
    protected int getUpgradeSlots() {
        return 6;
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 4;
    }

    @Override
    public void onNeighborChanged(IBlockAccess level, BlockPos pos, BlockPos neighbor) {
        EnumFacing side = getSide();
        if (side != null && pos.offset(side).equals(neighbor) && !isClientSide()) {
            this.adjacentStorageAccessor.onNeighborChanged(neighbor);
            this.clearCachedExternalStorageStrategies();
            this.scheduleUpdate();
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.StorageBus, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (this.updateStatus != PendingUpdateStatus.NO_UPDATE) {
            this.updateTarget(false);
        }

        if (this.monitor != null) {
            return this.monitor.onTick();
        }

        return this.updateStatus == PendingUpdateStatus.SLOW_UPDATE ? TickRateModulation.IDLE
            : TickRateModulation.SLEEP;
    }

    public MEStorage getInternalHandler() {
        return this.handler.getDelegate();
    }

    protected StorageBusInventory createHandler() {
        return new StorageBusInventory(NullInventory.of());
    }

    private boolean hasRegisteredCellToNetwork() {
        return getMainNode().isOnline() && !(this.handler.getDelegate() instanceof NullInventory);
    }

    @Nullable
    public ITextComponent getConnectedToDescription() {
        return this.handlerDescription;
    }

    protected void onConfigurationChanged() {
        if (getMainNode().isReady()) {
            updateTarget(true);
        }
    }

    private void onCapabilityInvalidation() {
        this.handler.setDelegate(NullInventory.of());
        this.handlerDescription = null;
        this.clearCachedExternalStorageStrategies();
        this.scheduleUpdate();
    }

    protected void updateTarget(boolean forceFullUpdate) {
        if (isClientSide()) {
            return;
        }

        MEStorage foundMonitor = null;
        Reference2ObjectMap<AEKeyType, MEStorage> foundExternalApi = new Reference2ObjectOpenHashMap<>(0);

        EnumFacing side = getSide();
        if (side != null && Platform.areBlockEntitiesTicking(getLevel(), getTileEntity().getPos().offset(side))) {
            this.updateStatus = PendingUpdateStatus.NO_UPDATE;

            foundMonitor = adjacentStorageAccessor.find();

            if (foundMonitor == null) {
                foundExternalApi = new Reference2ObjectOpenHashMap<>(2);
                findExternalStorages(foundExternalApi);
            }
        } else {
            this.updateStatus = PendingUpdateStatus.SLOW_UPDATE;
        }

        boolean extractableOnly = isExtractableOnly();
        if (this.handler.getDelegate() instanceof CompositeStorage compositeStorage
            && !foundExternalApi.isEmpty()
            && (!forceFullUpdate || this.externalStorageExtractableOnly == extractableOnly)) {
            if (!forceFullUpdate) {
                compositeStorage.setStorages(foundExternalApi);
            }
            this.handlerDescription = compositeStorage.getDescription();
            configureHandler();
            return;
        } else if (foundMonitor == this.handler.getDelegate()) {
            configureHandler();
            return;
        }

        boolean wasSleeping = this.monitor == null;
        boolean wasRegistered = this.hasRegisteredCellToNetwork();

        MEStorage newInventory;
        if (foundMonitor != null) {
            newInventory = foundMonitor;
            this.checkStorageBusOnInterface();
            this.handlerDescription = newInventory.getDescription();
        } else if (!foundExternalApi.isEmpty()) {
            newInventory = new CompositeStorage(foundExternalApi);
            this.externalStorageExtractableOnly = extractableOnly;
            this.handlerDescription = newInventory.getDescription();
        } else {
            newInventory = NullInventory.of();
            this.handlerDescription = null;
        }
        this.handler.setDelegate(newInventory);
        configureHandler();

        if (newInventory instanceof ITickingMonitor) {
            this.monitor = (ITickingMonitor) newInventory;
        } else if (!(newInventory instanceof NullInventory) && !(newInventory instanceof MEStorageMonitor)) {
            this.monitor = this.handler;
        } else {
            this.monitor = null;
        }

        if (wasSleeping != (this.monitor == null)) {
            getMainNode().ifPresent((grid, node) -> {
                if (this.monitor == null) {
                    grid.getTickManager().sleepDevice(node);
                } else {
                    grid.getTickManager().wakeDevice(node);
                }
            });
        }

        if (wasRegistered != this.hasRegisteredCellToNetwork()) {
            remountStorage();
        }
    }

    private void configureHandler() {
        this.handler.setAccessRestriction(this.getConfigManager().getSetting(Settings.ACCESS));
        this.handler.setWhitelist(isUpgradedWith(AEItems.INVERTER_CARD) ? IncludeExclude.BLACKLIST
            : IncludeExclude.WHITELIST);
        var partitionList = createFilter();
        this.handler.setPartitionList(partitionList);
        this.handler.setReadPartitionFiltering(!partitionList.isEmpty());
        this.handler.setVoidOverflow(this.isUpgradedWith(AEItems.VOID_CARD));
        this.handler.setSticky(this.isUpgradedWith(AEItems.STICKY_CARD));

        boolean filterOnExtract = this.getConfigManager().getSetting(Settings.FILTER_ON_EXTRACT) == YesNo.YES;
        this.handler.setExtractFiltering(filterOnExtract, isExtractableOnly() && filterOnExtract);
        this.handler.configurationChanged();
    }

    protected boolean isExtractableOnly() {
        return this.getConfigManager().getSetting(Settings.STORAGE_FILTER) == StorageFilter.EXTRACTABLE_ONLY;
    }

    protected IPartitionList createFilter() {
        KeyCounter filterKeys = new KeyCounter();
        FuzzyMode fuzzyMode = isUpgradedWith(AEItems.FUZZY_CARD)
            ? this.getConfigManager().getSetting(Settings.FUZZY_MODE)
            : null;

        int slotsToUse = 18 + getInstalledUpgrades(AEItems.CAPACITY_CARD) * 9;
        for (int x = 0; x < this.config.size() && x < slotsToUse; x++) {
            var key = this.config.getKey(x);
            if (key != null) {
                filterKeys.add(key, 1);
            }
        }
        if (filterKeys.isEmpty()) {
            return DefaultPriorityList.INSTANCE;
        }
        if (fuzzyMode != null) {
            return new FuzzyPriorityList(filterKeys, fuzzyMode);
        }
        return new PrecisePriorityList(filterKeys);
    }

    private void findExternalStorages(Map<AEKeyType, MEStorage> storages) {
        boolean extractableOnly = isExtractableOnly();
        for (Map.Entry<AEKeyType, ExternalStorageStrategy> entry : getExternalStorageStrategies().entrySet()) {
            MEStorage wrapper = entry.getValue().createWrapper(extractableOnly,
                this::invalidateOnExternalStorageChange);
            if (wrapper != null) {
                storages.put(entry.getKey(), wrapper);
            }
        }
    }

    private void invalidateOnExternalStorageChange() {
        this.clearCachedExternalStorageStrategies();
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    protected void clearCachedExternalStorageStrategies() {
        this.externalStorageStrategies = null;
    }

    private void checkStorageBusOnInterface() {
        EnumFacing side = getSide();
        if (side == null) {
            return;
        }

        EnumFacing oppositeSide = side.getOpposite();
        BlockPos targetPos = getTileEntity().getPos().offset(side);
        TileEntity targetBe = getLevel().getTileEntity(targetPos);

        Object targetHost = targetBe;
        if (targetBe instanceof IPartHost partHost) {
            targetHost = partHost.getPart(oppositeSide);
        }

        if (targetHost instanceof InterfaceLogicHost) {
            MinecraftServer server = getLevel().getMinecraftServer();
            if (server != null) {
                var actionableNode = this.getActionableNode();
                if (actionableNode == null) {
                    return;
                }
                EntityPlayerMP player = IPlayerRegistry.getConnected(server, actionableNode.getOwningPlayerId());
                if (player != null) {
                    player.addStat(getRecursiveNetworkingStat());
                    AppEng.instance().getAdvancementTriggers().getRecursive().trigger(player);
                }
            }
        }
    }

    @Override
    public void mountInventories(IStorageMounts mounts) {
        if (this.hasRegisteredCellToNetwork()) {
            mounts.mount(this.handler, this.priority);
        }
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(int newValue) {
        this.priority = newValue;
        this.getHost().markForSave();
        this.remountStorage();
    }

    @Override
    public ConfigInventory getConfig() {
        return this.config;
    }

    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return MODELS_ON;
        } else {
            return MODELS_OFF;
        }
    }

    private Map<AEKeyType, ExternalStorageStrategy> getExternalStorageStrategies() {
        if (this.externalStorageStrategies == null) {
            TileEntity host = getHost().getTileEntity();
            EnumFacing side = getSide();
            if (side == null) {
                return Collections.emptyMap();
            }
            this.externalStorageStrategies = StackWorldBehaviors.createExternalStorageStrategies(
                (WorldServer) host.getWorld(),
                host.getPos().offset(side),
                side.getOpposite());
        }
        return this.externalStorageStrategies;
    }

    private enum PendingUpdateStatus {
        FAST_UPDATE,
        SLOW_UPDATE,
        NO_UPDATE
    }

    protected static class StorageBusInventory extends MEInventoryHandler implements MEStorageMonitor, ITickingMonitor {
        private final ObjectList<ListenerRegistration> listeners = new ObjectArrayList<>();
        private final ObjectList<ListenerRegistration> listenerDispatchBuffer = new ObjectArrayList<>();
        private final DelegateListener delegateListener = new DelegateListener();
        private KeyCounter targetCache = KeyCounter.saturating();
        private KeyCounter targetScratch = KeyCounter.saturating();
        @Nullable
        private MEStorageMonitor monitoredDelegate;
        @Nullable
        private Thread delegateThread;
        private boolean cacheDirty = true;
        private boolean cacheInitialized;
        private boolean processingDelegateCallback;
        private boolean dispatchingListeners;
        private boolean dispatchingListUpdate;
        private boolean listUpdatePending;

        protected StorageBusInventory(MEStorage inventory) {
            super(inventory);
        }

        @Override
        protected MEStorage getDelegate() {
            return super.getDelegate();
        }

        @Override
        protected void setDelegate(MEStorage delegate) {
            unbindDelegateMonitor();
            super.setDelegate(delegate);
            this.cacheInitialized = false;
            this.cacheDirty = true;
            if (!this.listeners.isEmpty()) {
                bindDelegateMonitor();
                refreshTarget(false);
            }
            notifyListUpdate();
        }

        public void setAccessRestriction(AccessRestriction setting) {
            setAllowExtraction(setting.isAllowExtraction());
            setAllowInsertion(setting.isAllowInsertion());
        }

        public void configurationChanged() {
            notifyListUpdate();
        }

        protected long getCachedAmount(AEKey what) {
            ensureCache();
            return Math.max(0, this.targetCache.get(what));
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            long inserted = super.insert(what, amount, mode, source);
            if (inserted > 0 && mode == Actionable.MODULATE && this.monitoredDelegate == null) {
                markCacheDirty();
            }
            return inserted;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            long extracted = super.extract(what, amount, mode, source);
            if (extracted > 0 && mode == Actionable.MODULATE && this.monitoredDelegate == null) {
                markCacheDirty();
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            ensureCache();
            for (var entry : this.targetCache) {
                if (entry.getLongValue() > 0 && isVisibleInAvailableStacks(entry.getKey())) {
                    out.add(entry.getKey(), entry.getLongValue());
                }
            }
        }

        @Override
        public TickRateModulation onTick() {
            if (this.monitoredDelegate != null) {
                return TickRateModulation.SLEEP;
            }

            boolean publishChanges = this.cacheInitialized && !this.cacheDirty && !this.listeners.isEmpty();
            boolean changed = refreshTarget(publishChanges);
            return changed ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }

        @Override
        public void addListener(MEStorageChangeListener listener, Object verificationToken) {
            for (int i = 0; i < this.listeners.size(); i++) {
                var registration = this.listeners.get(i);
                if (registration.listener == listener) {
                    throw new IllegalStateException("The storage listener is already registered.");
                }
            }

            boolean firstListener = this.listeners.isEmpty();
            this.listeners.add(new ListenerRegistration(listener, verificationToken));
            if (firstListener) {
                bindDelegateMonitor();
                refreshTarget(false);
            }
        }

        @Override
        public void removeListener(MEStorageChangeListener listener) {
            for (int i = this.listeners.size() - 1; i >= 0; i--) {
                var registration = this.listeners.get(i);
                if (registration.listener == listener) {
                    registration.active = false;
                    this.listeners.remove(i);
                }
            }
            if (this.listeners.isEmpty()) {
                unbindDelegateMonitor();
            }
        }

        private void removeAllListeners() {
            for (int i = 0; i < this.listeners.size(); i++) {
                this.listeners.get(i).active = false;
            }
            this.listeners.clear();
            unbindDelegateMonitor();
        }

        private void bindDelegateMonitor() {
            if (this.monitoredDelegate != null) {
                throw new IllegalStateException("The storage bus delegate monitor is already bound.");
            }
            if (getDelegate() instanceof MEStorageMonitor monitor) {
                this.monitoredDelegate = monitor;
                this.delegateThread = Thread.currentThread();
                monitor.addListener(this.delegateListener, getDelegate());
            }
        }

        private void unbindDelegateMonitor() {
            if (this.monitoredDelegate != null) {
                this.monitoredDelegate.removeListener(this.delegateListener);
                this.monitoredDelegate = null;
                this.delegateThread = null;
            }
        }

        private void ensureCache() {
            if (!this.cacheInitialized || this.cacheDirty) {
                refreshTarget(false);
            }
        }

        private boolean refreshTarget(boolean publishChanges) {
            this.targetScratch.reset();
            getDelegate().getAvailableStacks(this.targetScratch);
            this.targetScratch.removeZeros();
            boolean changed = hasDifference(this.targetCache, this.targetScratch);
            if (publishChanges) {
                publishReplacement(this.targetCache, this.targetScratch);
            }
            var previous = this.targetCache;
            this.targetCache = this.targetScratch;
            this.targetScratch = previous;
            this.cacheInitialized = true;
            this.cacheDirty = false;
            return changed;
        }

        private boolean hasDifference(KeyCounter previous, KeyCounter replacement) {
            for (var entry : replacement) {
                if (entry.getLongValue() != previous.get(entry.getKey())) {
                    return true;
                }
            }
            for (var entry : previous) {
                if (entry.getLongValue() > 0 && replacement.get(entry.getKey()) == 0) {
                    return true;
                }
            }
            return false;
        }

        private void publishReplacement(KeyCounter previous, KeyCounter replacement) {
            for (var entry : replacement) {
                long oldAmount = Math.max(0, previous.get(entry.getKey()));
                long delta = entry.getLongValue() - oldAmount;
                if (delta != 0) {
                    if (isVisibleInAvailableStacks(entry.getKey())) {
                        notifyDelta(entry.getKey(), delta);
                    }
                }
            }
            for (var entry : previous) {
                if (entry.getLongValue() > 0 && replacement.get(entry.getKey()) == 0) {
                    if (isVisibleInAvailableStacks(entry.getKey())) {
                        notifyDelta(entry.getKey(), -entry.getLongValue());
                    }
                }
            }
        }

        private void applyDelegateDelta(AEKey what, long delta) {
            if (what == null || delta == 0) {
                if (what == null) {
                    AELog.error("Storage bus target reported a null storage key.");
                    markCacheDirty();
                }
                return;
            }
            if (this.cacheDirty) {
                requestListUpdate();
                return;
            }

            long current = Math.max(0, this.targetCache.get(what));
            if ((current == Long.MAX_VALUE && delta < 0)
                || (delta < 0 && (delta == Long.MIN_VALUE || current < -delta))) {
                AELog.error("Storage bus target reported delta %d for %s with cached amount %d.", delta, what, current);
                markCacheDirty();
                return;
            }
            long updated = delta > 0 && current > Long.MAX_VALUE - delta ? Long.MAX_VALUE : current + delta;
            if (updated == 0) {
                this.targetCache.remove(what);
            } else {
                this.targetCache.set(what, updated);
            }
            if (isVisibleInAvailableStacks(what)) {
                notifyDelta(what, delta);
            }
        }

        private void markCacheDirty() {
            if (!this.cacheDirty) {
                this.cacheDirty = true;
                notifyListUpdate();
            }
        }

        private void notifyDelta(AEKey what, long delta) {
            dispatchListeners(what, delta, false);
        }

        private void notifyListUpdate() {
            if (this.listeners.isEmpty()) {
                return;
            }
            dispatchListeners(null, 0, true);
        }

        private void requestListUpdate() {
            if (this.listeners.isEmpty()) {
                return;
            }
            if (this.dispatchingListeners || this.processingDelegateCallback) {
                if (!this.dispatchingListUpdate) {
                    this.listUpdatePending = true;
                }
            } else {
                notifyListUpdate();
            }
        }

        private void dispatchListeners(@Nullable AEKey what, long delta, boolean listUpdate) {
            if (this.dispatchingListeners) {
                AELog.error("Reentrant storage bus listener notification; scheduling a target rescan.");
                this.cacheDirty = true;
                if (!this.dispatchingListUpdate) {
                    this.listUpdatePending = true;
                }
                return;
            }

            this.dispatchingListeners = true;
            this.dispatchingListUpdate = listUpdate;
            this.listenerDispatchBuffer.clear();
            this.listenerDispatchBuffer.addAll(this.listeners);
            try {
                for (int i = 0; i < this.listenerDispatchBuffer.size(); i++) {
                    var registration = this.listenerDispatchBuffer.get(i);
                    if (!registration.active) {
                        continue;
                    }
                    if (!registration.listener.isValid(registration.verificationToken)) {
                        registration.active = false;
                        continue;
                    }
                    if (listUpdate) {
                        registration.listener.onListUpdate();
                    } else {
                        registration.listener.onStackChange(what, delta);
                    }
                }
            } finally {
                this.listenerDispatchBuffer.clear();
                this.dispatchingListUpdate = false;
                this.dispatchingListeners = false;
            }
            removeInactiveListeners();

            flushPendingListUpdate();
        }

        private void flushPendingListUpdate() {
            if (this.listUpdatePending && !this.processingDelegateCallback && !this.dispatchingListeners) {
                this.listUpdatePending = false;
                notifyListUpdate();
            }
        }

        private void removeInactiveListeners() {
            for (int i = this.listeners.size() - 1; i >= 0; i--) {
                if (!this.listeners.get(i).active) {
                    this.listeners.remove(i);
                }
            }
            if (this.listeners.isEmpty()) {
                unbindDelegateMonitor();
            }
        }

        private final class DelegateListener implements MEStorageChangeListener {
            @Override
            public boolean isValid(Object verificationToken) {
                return verificationToken == getDelegate()
                    && monitoredDelegate == getDelegate()
                    && !listeners.isEmpty();
            }

            @Override
            public void onStackChange(AEKey what, long delta) {
                if (Thread.currentThread() != delegateThread) {
                    AELog.error("Storage bus target invoked a callback from the wrong thread.");
                    cacheDirty = true;
                    return;
                }
                if (processingDelegateCallback || dispatchingListeners) {
                    AELog.error("Reentrant storage bus target callback; scheduling a target rescan.");
                    cacheDirty = true;
                    requestListUpdate();
                    return;
                }
                processingDelegateCallback = true;
                try {
                    applyDelegateDelta(what, delta);
                } finally {
                    processingDelegateCallback = false;
                    flushPendingListUpdate();
                }
            }

            @Override
            public void onListUpdate() {
                if (Thread.currentThread() != delegateThread) {
                    AELog.error("Storage bus target invalidated its list from the wrong thread.");
                    cacheDirty = true;
                    return;
                }
                if (processingDelegateCallback || dispatchingListeners) {
                    AELog.error("Reentrant storage bus target list update; scheduling a target rescan.");
                    cacheDirty = true;
                    requestListUpdate();
                    return;
                }
                processingDelegateCallback = true;
                try {
                    markCacheDirty();
                } finally {
                    processingDelegateCallback = false;
                    flushPendingListUpdate();
                }
            }
        }

        private static final class ListenerRegistration {
            private final MEStorageChangeListener listener;
            private final Object verificationToken;
            private boolean active = true;

            private ListenerRegistration(MEStorageChangeListener listener, Object verificationToken) {
                this.listener = listener;
                this.verificationToken = verificationToken;
            }
        }
    }
}
