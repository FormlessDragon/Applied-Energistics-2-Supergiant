package appeng.parts.storagebus;

import appeng.api.AECapabilities;
import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.config.AccessRestriction;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Setting;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.config.YesNo;
import appeng.api.features.IPlayerRegistry;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigManagerBuilder;
import appeng.container.GuiIds;
import appeng.container.ISubGui;
import appeng.core.AppEng;
import appeng.core.definitions.AEItems;
import appeng.core.gui.GuiOpener;
import appeng.core.settings.TickRates;
import appeng.helpers.IConfigInvHost;
import appeng.helpers.IPriorityHost;
import appeng.helpers.InterfaceLogicHost;
import appeng.items.parts.PartModels;
import appeng.me.helpers.MachineSource;
import appeng.me.storage.CompositeStorage;
import appeng.me.storage.ITickingMonitor;
import appeng.me.storage.MEInventoryHandler;
import appeng.me.storage.NullInventory;
import appeng.parts.PartAdjacentApi;
import appeng.parts.PartModel;
import appeng.parts.automation.StackWorldBehaviors;
import appeng.parts.automation.UpgradeablePart;
import appeng.util.ConfigInventory;
import appeng.util.Platform;
import appeng.util.prioritylist.DefaultPriorityList;
import appeng.util.prioritylist.FuzzyPriorityList;
import appeng.util.prioritylist.IPartitionList;
import appeng.util.prioritylist.PrecisePriorityList;
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
    private final StorageBusInventory handler = new StorageBusInventory(NullInventory.of());
    private final PartAdjacentApi<MEStorage> adjacentStorageAccessor;    private final ConfigInventory config = ConfigInventory.configTypes(63)
                                                          .changeListener(this::onConfigurationChanged)
                                                          .build();
    @Nullable
    private ITextComponent handlerDescription;
    @Nullable
    private Map<AEKeyType, ExternalStorageStrategy> externalStorageStrategies;
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

    private void remountStorage() {
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

    private void scheduleUpdate() {
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

    private void updateTarget(boolean forceFullUpdate) {
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

    private boolean isExtractableOnly() {
        return this.getConfigManager().getSetting(Settings.STORAGE_FILTER) == StorageFilter.EXTRACTABLE_ONLY;
    }

    private IPartitionList createFilter() {
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

    void clearCachedExternalStorageStrategies() {
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

    private static class StorageBusInventory extends MEInventoryHandler {
        StorageBusInventory(MEStorage inventory) {
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
