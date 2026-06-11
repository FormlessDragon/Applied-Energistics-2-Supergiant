package ae2.helpers;

import ae2.api.config.Actionable;
import ae2.api.config.FuzzyMode;
import ae2.api.config.Settings;
import ae2.api.networking.GridFlags;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.crafting.ICraftingForceStartRequester;
import ae2.api.networking.crafting.ICraftingLink;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingRequester;
import ae2.api.networking.energy.IEnergySource;
import ae2.api.networking.security.IActionHost;
import ae2.api.networking.security.IActionSource;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.MEStorage;
import ae2.api.storage.StorageHelper;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.IUpgradeableObject;
import ae2.api.upgrades.UpgradeInventories;
import ae2.api.util.AECableType;
import ae2.api.util.DimensionalBlockPos;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigurableObject;
import ae2.core.AEConfig;
import ae2.core.definitions.AEItems;
import ae2.core.settings.TickRates;
import ae2.items.materials.UpgradeCardItem;
import ae2.me.helpers.MachineSource;
import ae2.me.storage.DelegatingMEInventory;
import ae2.me.storage.NullInventory;
import ae2.text.TextComponentItemStack;
import ae2.util.ConfigInventory;
import ae2.util.Platform;
import com.google.common.collect.ImmutableSet;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public class InterfaceLogic implements ICraftingForceStartRequester, IUpgradeableObject,
    IConfigurableObject {
    public static final int SLOTS_PER_PAGE = 18;

    protected final InterfaceLogicHost host;
    protected final IManagedGridNode mainNode;
    protected final IActionSource actionSource;
    protected final IActionSource interfaceRequestSource;
    private final MultiCraftingTracker craftingTracker;
    private final IUpgradeInventory upgrades;
    private final IConfigManager cm;
    private final GenericStack[] plannedWork;
    private final ConfigInventory config;
    private final ConfigInventory storage;
    @Nullable
    private InterfaceInventory localInvHandler;
    private MEStorage networkStorage = NullInventory.of();
    private int priority;
    private boolean hasConfig;

    public InterfaceLogic(IManagedGridNode gridNode, InterfaceLogicHost host, Item machineType) {
        this(gridNode, host, machineType, AEConfig.instance().getInterfacePageLimit() * SLOTS_PER_PAGE);
    }

    public InterfaceLogic(IManagedGridNode gridNode, InterfaceLogicHost host, Item machineType, int slots) {
        this.host = host;
        this.config = ConfigInventory.configStacks(slots).changeListener(this::onConfigRowChanged)
                                     .allowOverstacking(true).build();
        this.storage = ConfigInventory.storage(slots).slotFilter(this::isAllowedInStorageSlot)
                                      .changeListener(this::onStorageChanged).allowOverstacking(true).build();
        this.mainNode = gridNode
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .addService(IGridTickable.class, new Ticker());
        this.actionSource = new MachineSource(mainNode::getNode);
        this.interfaceRequestSource = new InterfaceRequestSource(mainNode::getNode);

        this.mainNode.addService(ICraftingRequester.class, this);
        this.upgrades = UpgradeInventories.forMachine(machineType, 1, this::onUpgradesChanged);
        this.craftingTracker = new MultiCraftingTracker(this, slots);
        this.cm = IConfigManager.builder(this::onConfigChanged)
                                .registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL)
                                .build();
        this.plannedWork = new GenericStack[slots];

        this.getConfig().useRegisteredCapacities();
        this.getStorage().useRegisteredCapacities();
    }

    public static boolean isForceStartCraftingEnabled(Iterable<ItemStack> upgrades) {
        for (var stack : upgrades) {
            if (UpgradeCardItem.isForceCraftingEnabled(stack)) {
                return true;
            }
        }
        return false;
    }

    public int getPageCount() {
        return Math.max(1, (this.config.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);
    }

    private boolean isAllowedInStorageSlot(int slot, AEKey what) {
        if (slot < config.size()) {
            var configured = config.getKey(slot);
            if (configured == null || configured.equals(what)) {
                return true;
            }
            if (upgrades.isInstalled(AEItems.FUZZY_CARD.item())) {
                var fuzzyMode = getConfigManager().getSetting(Settings.FUZZY_MODE);
                return configured.fuzzyEquals(what, fuzzyMode);
            }
            return false;
        }
        return true;
    }

    public int getPriority() {
        return this.priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
        this.host.saveChanges();
    }

    private void readConfig() {
        this.hasConfig = !this.config.isEmpty();
        this.updatePlan();
        this.notifyNeighbors();
    }

    public void writeToNBT(NBTTagCompound tag) {
        this.config.writeToChildTag(tag, "config");
        this.storage.writeToChildTag(tag, "storage");
        this.upgrades.writeToNBT(tag, "upgrades");
        this.cm.writeToNBT(tag);
        this.craftingTracker.writeToNBT(tag);
        tag.setInteger("priority", this.priority);
    }

    public void readFromNBT(NBTTagCompound tag) {
        this.craftingTracker.readFromNBT(tag);
        this.upgrades.readFromNBT(tag, "upgrades");
        this.config.readFromChildTag(tag, "config");
        this.storage.readFromChildTag(tag, "storage");
        this.cm.readFromNBT(tag);
        this.readConfig();
        this.priority = tag.getInteger("priority");
    }

    protected final OptionalInt getRequestInterfacePriority(IActionSource src) {
        return src.context(InterfaceRequestContext.class)
                  .map(ctx -> OptionalInt.of(ctx.getPriority()))
                  .orElseGet(OptionalInt::empty);
    }

    protected final boolean isSameGrid(IActionSource src) {
        var otherGrid = src.machine().map(IActionHost::getActionableNode).map(IGridNode::grid).orElse(null);
        return otherGrid == mainNode.getGrid();
    }

    protected final boolean hasWorkToDo() {
        for (var requiredWork : this.plannedWork) {
            if (requiredWork != null) {
                return true;
            }
        }
        return false;
    }

    public void notifyNeighbors() {
        if (this.mainNode.isActive()) {
            this.mainNode.ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
        }

        var blockEntity = this.host.getTileEntity();
        if (blockEntity.getWorld() != null) {
            Platform.notifyBlocksOfNeighbors(blockEntity.getWorld(), blockEntity.getPos());
        }
    }

    public void gridChanged() {
        IGrid grid = mainNode.getGrid();
        this.networkStorage = grid != null ? grid.getStorageService().getInventory() : NullInventory.of();
        this.notifyNeighbors();
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.cm;
    }

    public ConfigInventory getStorage() {
        return this.storage;
    }

    public ConfigInventory getConfig() {
        return this.config;
    }

    public MEStorage getInventory() {
        if (this.hasConfig) {
            return getLocalInventory();
        }
        return this.networkStorage;
    }

    private MEStorage getLocalInventory() {
        if (this.localInvHandler == null) {
            this.localInvHandler = new InterfaceInventory();
        }
        return this.localInvHandler;
    }

    private boolean updateStorage() {
        boolean didSomething = false;

        for (int x = 0; x < plannedWork.length; x++) {
            var work = plannedWork[x];
            if (work != null) {
                didSomething = this.usePlan(x, work.what(), work.amount()) || didSomething;
            }
        }

        return didSomething;
    }

    private boolean usePlan(int slot, AEKey what, long amount) {
        boolean changed = tryUsePlan(slot, what, amount);
        if (changed) {
            this.updatePlan(slot);
        }
        return changed;
    }

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return this.craftingTracker.getRequestedJobs();
    }

    @Override
    public long insertCraftedItems(ICraftingLink link, AEKey what, long amount, Actionable mode) {
        int slot = this.craftingTracker.getSlot(link);
        return this.storage.insert(slot, what, amount, mode);
    }

    @Override
    public void jobStateChange(ICraftingLink link) {
        this.craftingTracker.jobStateChange(link);
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    @Override
    @Nullable
    public IGridNode getActionableNode() {
        return this.mainNode.getNode();
    }

    private void updatePlan() {
        boolean hadWork = this.hasWorkToDo();
        for (int x = 0; x < this.config.size(); x++) {
            this.updatePlan(x);
        }
        boolean hasWork = this.hasWorkToDo();

        if (hadWork != hasWork) {
            this.mainNode.ifPresent((grid, node) -> {
                if (hasWork) {
                    grid.getTickManager().alertDevice(node);
                } else {
                    grid.getTickManager().sleepDevice(node);
                }
            });
        }
    }

    private void updatePlan(int slot) {
        var req = this.config.getStack(slot);
        var stored = this.storage.getStack(slot);

        if (req == null && stored != null) {
            this.plannedWork[slot] = new GenericStack(stored.what(), -stored.amount());
        } else if (req != null) {
            if (stored == null) {
                this.plannedWork[slot] = req;
            } else if (storedRequestEquals(req.what(), stored.what())) {
                if (req.amount() != stored.amount()) {
                    this.plannedWork[slot] = new GenericStack(req.what(), req.amount() - stored.amount());
                } else {
                    this.plannedWork[slot] = null;
                }
            } else {
                this.plannedWork[slot] = new GenericStack(stored.what(), -stored.amount());
            }
        } else {
            this.plannedWork[slot] = null;
        }
    }

    private boolean storedRequestEquals(AEKey request, AEKey stored) {
        if (upgrades.isInstalled(AEItems.FUZZY_CARD.item()) && request.supportsFuzzyRangeSearch()) {
            return request.fuzzyEquals(stored, cm.getSetting(Settings.FUZZY_MODE));
        }
        return request.equals(stored);
    }

    private boolean tryUsePlan(int slot, AEKey what, long amount) {
        IGrid grid = mainNode.getGrid();
        if (grid == null) {
            return false;
        }

        MEStorage networkInv = grid.getStorageService().getInventory();
        IEnergySource energySrc = grid.getEnergyService();

        if (amount < 0) {
            amount = -amount;

            var inSlot = storage.getStack(slot);
            if (!what.matches(inSlot) || inSlot.amount() < amount) {
                return true;
            }

            long inserted = StorageHelper.poweredInsert(energySrc, networkInv, what, amount,
                this.interfaceRequestSource);
            if (inserted > 0) {
                storage.extract(slot, what, inserted, Actionable.MODULATE);
            }
            return inserted > 0;
        }

        if (this.craftingTracker.isBusy(slot)) {
            return this.handleCrafting(slot, what, amount);
        } else if (amount > 0) {
            if (storage.insert(slot, what, amount, Actionable.SIMULATE) != amount) {
                return true;
            }

            if (acquireFromNetwork(energySrc, networkInv, slot, what, amount)) {
                return true;
            }

            if (storage.getStack(slot) == null && upgrades.isInstalled(AEItems.FUZZY_CARD.item())) {
                FuzzyMode fuzzyMode = getConfigManager().getSetting(Settings.FUZZY_MODE);
                for (var entry : grid.getStorageService().getCachedInventory().findFuzzy(what, fuzzyMode)) {
                    long maxAmount = storage.insert(slot, entry.getKey(), amount, Actionable.SIMULATE);
                    if (acquireFromNetwork(energySrc, networkInv, slot, entry.getKey(), maxAmount)) {
                        return true;
                    }
                }
            }

            return this.handleCrafting(slot, what, amount);
        }

        return false;
    }

    private boolean acquireFromNetwork(IEnergySource energySrc, MEStorage networkInv, int slot, AEKey what,
                                       long amount) {
        long acquired = StorageHelper.poweredExtraction(energySrc, networkInv, what, amount,
            this.interfaceRequestSource);
        if (acquired > 0) {
            long inserted = storage.insert(slot, what, acquired, Actionable.MODULATE);
            if (inserted < acquired) {
                throw new IllegalStateException("bad attempt at managing inventory. Voided items: " + inserted);
            }
            return true;
        }
        return false;
    }

    private boolean handleCrafting(int slot, AEKey key, long amount) {
        var grid = mainNode.getGrid();
        if (grid != null && upgrades.isInstalled(AEItems.CRAFTING_CARD.item()) && key != null) {
            return this.craftingTracker.handleCrafting(slot, key, amount, this.host.getTileEntity().getWorld(),
                grid.getCraftingService(), this.actionSource);
        }
        return false;
    }

    @Override
    public boolean canForceStartCrafting(ICraftingPlan plan) {
        return isForceStartCraftingEnabled(this.upgrades);
    }

    private void cancelCrafting() {
        this.craftingTracker.cancel();
    }

    private void onConfigChanged() {
        this.host.saveChanges();
        this.updatePlan();
    }

    private void onUpgradesChanged() {
        this.host.saveChanges();

        if (!upgrades.isInstalled(AEItems.CRAFTING_CARD.item())) {
            this.cancelCrafting();
        }

        this.updatePlan();
    }

    private void onConfigRowChanged() {
        this.host.saveChanges();
        this.readConfig();
    }

    private void onStorageChanged() {
        this.host.saveChanges();
        this.updatePlan();
    }

    public void addDrops(List<ItemStack> drops) {
        for (var stack : this.upgrades) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }

        var level = this.host.getTileEntity().getWorld();
        BlockPos pos = this.host.getTileEntity().getPos();
        if (level != null) {
            for (int i = 0; i < this.storage.size(); i++) {
                var stack = storage.getStack(i);
                if (stack != null) {
                    stack.what().addDrops(stack.amount(), drops, level, pos);
                }
            }
        }
    }

    public void clearContent() {
        this.upgrades.clear();
        this.storage.clear();
    }

    public AECableType getCableConnectionType(EnumFacing dir) {
        return AECableType.SMART;
    }

    public DimensionalBlockPos getLocation() {
        return new DimensionalBlockPos(this.host.getTileEntity());
    }

    private class Ticker implements IGridTickable {
        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(TickRates.Interface, !hasWorkToDo());
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (!mainNode.isActive()) {
                return TickRateModulation.SLEEP;
            }

            boolean couldDoWork = updateStorage();
            if (!hasWorkToDo()) {
                return TickRateModulation.SLEEP;
            }
            return couldDoWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }
    }

    private class InterfaceRequestSource extends MachineSource {
        private final InterfaceRequestContext context = new InterfaceRequestContext();

        InterfaceRequestSource(IActionHost host) {
            super(host);
        }

        @Override
        public <T> Optional<T> context(Class<T> key) {
            if (key == InterfaceRequestContext.class) {
                return Optional.of(key.cast(this.context));
            }
            return super.context(key);
        }
    }

    private class InterfaceRequestContext {
        public int getPriority() {
            return priority;
        }
    }

    private class InterfaceInventory extends DelegatingMEInventory {
        InterfaceInventory() {
            super(storage);
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (getRequestInterfacePriority(source).isPresent() && isSameGrid(source)) {
                return 0;
            }
            return super.insert(what, amount, mode, source);
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            var requestPriority = getRequestInterfacePriority(source);
            if (requestPriority.isPresent() && requestPriority.getAsInt() <= getPriority() && isSameGrid(source)) {
                return 0;
            }
            return super.extract(what, amount, mode, source);
        }

        @Override
        public ITextComponent getDescription() {
            return TextComponentItemStack.of(host.getMainContainerIcon());
        }
    }
}
