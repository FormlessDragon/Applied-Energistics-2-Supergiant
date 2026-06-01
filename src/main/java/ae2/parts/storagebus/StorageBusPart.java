package ae2.parts.storagebus;

import ae2.api.AECapabilities;
import ae2.api.behaviors.ExternalStorageStrategy;
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
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.IStorageMounts;
import ae2.api.storage.IStorageProvider;
import ae2.api.storage.MEStorage;
import ae2.api.util.AECableType;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigManagerBuilder;
import ae2.container.GuiIds;
import ae2.container.ISubGui;
import ae2.core.AppEng;
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
    private Map<AEKeyType, ExternalStorageStrategy> externalStorageStrategies;    private final ConfigInventory config = ConfigInventory.configTypes(63)
                                                          .changeListener(this::onConfigurationChanged)
                                                          .build();
    private boolean wasOnline;
    private int priority;
    private PendingUpdateStatus updateStatus = PendingUpdateStatus.FAST_UPDATE;
    @Nullable
    private ITickingMonitor monitor;
    public StorageBusPart(IPartItem<?> partItem) {
        super(partItem);
        this.adjacentStorageAccessor = new PartAdjacentApi<>(this, AECapabilities.ME_STORAGE,
            this::onCapabilityInvalidation);
        this.source = new MachineSource(this);
        getMainNode().addService(IStorageProvider.class, this).addService(IGridTickable.class, this);
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
        return 5;
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 4;
    }

    @Override
    public void onNeighborChanged(IBlockAccess level, BlockPos pos, BlockPos neighbor) {
        if (pos.offset(getSide()).equals(neighbor) && !isClientSide()) {
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

        if (Platform.areBlockEntitiesTicking(getLevel(), getTileEntity().getPos().offset(getSide()))) {
            this.updateStatus = PendingUpdateStatus.NO_UPDATE;

            foundMonitor = adjacentStorageAccessor.find();

            if (foundMonitor == null) {
                foundExternalApi = new Reference2ObjectOpenHashMap<>(2);
                findExternalStorages(foundExternalApi);
            }
        } else {
            this.updateStatus = PendingUpdateStatus.SLOW_UPDATE;
        }

        if (!forceFullUpdate && this.handler.getDelegate() instanceof CompositeStorage compositeStorage
            && !foundExternalApi.isEmpty()) {
            compositeStorage.setStorages(foundExternalApi);
            this.handlerDescription = compositeStorage.getDescription();
            return;
        } else if (!forceFullUpdate && foundMonitor == this.handler.getDelegate()) {
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
            this.handlerDescription = newInventory.getDescription();
        } else {
            newInventory = NullInventory.of();
            this.handlerDescription = null;
        }
        this.handler.setDelegate(newInventory);

        this.handler.setAccessRestriction(this.getConfigManager().getSetting(Settings.ACCESS));
        this.handler.setWhitelist(isUpgradedWith(AEItems.INVERTER_CARD) ? IncludeExclude.BLACKLIST
            : IncludeExclude.WHITELIST);
        this.handler.setPartitionList(createFilter());
        this.handler.setVoidOverflow(this.isUpgradedWith(AEItems.VOID_CARD));
        this.handler.setSticky(this.isUpgradedWith(AEItems.STICKY_CARD));

        boolean filterOnExtract = this.getConfigManager().getSetting(Settings.FILTER_ON_EXTRACT) == YesNo.YES;
        this.handler.setExtractFiltering(filterOnExtract, isExtractableOnly() && filterOnExtract);

        if (newInventory instanceof ITickingMonitor) {
            this.monitor = (ITickingMonitor) newInventory;
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
            if (this.config.getKey(x) != null) {
                filterKeys.add(this.config.getKey(x), 1);
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

    protected static class StorageBusInventory extends MEInventoryHandler {
        protected StorageBusInventory(MEStorage inventory) {
            super(inventory);
        }

        @Override
        protected MEStorage getDelegate() {
            return super.getDelegate();
        }

        @Override
        protected void setDelegate(MEStorage delegate) {
            super.setDelegate(delegate);
        }

        public void setAccessRestriction(AccessRestriction setting) {
            setAllowExtraction(setting.isAllowExtraction());
            setAllowInsertion(setting.isAllowInsertion());
        }
    }




}
