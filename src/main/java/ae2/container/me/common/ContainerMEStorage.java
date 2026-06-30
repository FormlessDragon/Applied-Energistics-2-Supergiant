/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.container.me.common;

import ae2.api.config.Actionable;
import ae2.api.config.Setting;
import ae2.api.config.Settings;
import ae2.api.config.SortDir;
import ae2.api.config.SortOrder;
import ae2.api.config.ViewItems;
import ae2.api.config.YesNo;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.implementations.blockentities.IViewCellStorage;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.crafting.ICraftingCPU;
import ae2.api.networking.energy.IEnergySource;
import ae2.api.networking.security.IActionHost;
import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.ILinkStatus;
import ae2.api.storage.ITerminalHost;
import ae2.api.storage.MEStorage;
import ae2.api.storage.StorageHelper;
import ae2.api.storage.cells.IBasicCellItem;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigurableObject;
import ae2.api.util.KeyTypeSelection;
import ae2.api.util.KeyTypeSelectionHost;
import ae2.container.AEBaseContainer;
import ae2.container.GuiIds;
import ae2.container.SlotSemantics;
import ae2.container.ToolboxInventory;
import ae2.container.guisync.GuiSync;
import ae2.container.guisync.ILinkStatusAwareContainer;
import ae2.container.implementations.ContainerCraftAmount;
import ae2.container.interfaces.IKeyTypeSelectionContainer;
import ae2.container.slot.AppEngSlot;
import ae2.container.slot.RestrictedInputSlot;
import ae2.core.AELog;
import ae2.core.gui.locator.GuiHostLocator;
import ae2.core.network.InitNetwork;
import ae2.core.network.bidirectional.ConfigValuePacket;
import ae2.core.network.clientbound.MEInventoryUpdatePacket;
import ae2.core.network.clientbound.RecursiveIngredientReserveAmountPacket;
import ae2.core.network.clientbound.SetLinkStatusPacket;
import ae2.core.network.serverbound.MEInteractionPacket;
import ae2.helpers.InventoryAction;
import ae2.helpers.WirelessTerminalGuiHost;
import ae2.items.misc.GenericResourcePackageItem;
import ae2.items.misc.PackageInsertResult;
import ae2.me.helpers.ActionHostEnergySource;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ContainerMEStorage extends AEBaseContainer
    implements IConfigurableObject, IMEInteractionHandler, ILinkStatusAwareContainer,
    IKeyTypeSelectionContainer {

    private static final short SEARCH_KEY_TYPES_ID = 101;
    protected final MEStorage storage;
    protected final IEnergySource energySource;
    private final List<RestrictedInputSlot> viewCellSlots;
    private final IConfigManager clientCM;
    private final ToolboxInventory toolboxInventory;
    private final ITerminalHost host;
    private final GuiIds.GuiKey guiKey;
    private final IncrementalUpdateHelper updateHelper = new IncrementalUpdateHelper();
    /**
     * The number of active crafting jobs in the network. -1 means unknown and will hide the label on the screen.
     */
    @GuiSync(100)
    public int activeCraftingJobs = -1;
    @GuiSync(SEARCH_KEY_TYPES_ID)
    public SyncedKeyTypes searchKeyTypes = new SyncedKeyTypes();
    private long recursiveIngredientReserveAmount = 1;
    private long lastSentRecursiveIngredientReserveAmount = Long.MIN_VALUE;
    private ILinkStatus linkStatus = ILinkStatus.ofDisconnected(null);
    @Nullable
    private Runnable gui;
    private IConfigManager serverCM;
    /**
     * The repository of entries currently known on the client-side. This is maintained by the screen associated with
     * this container and will only be non-null on the client-side.
     */
    @Nullable
    private IClientRepo clientRepo;

    /**
     * The last set of craftables sent to the client.
     */
    private Set<AEKey> previousCraftables = ObjectSets.emptySet();
    private KeyCounter previousAvailableStacks = new KeyCounter();

    public ContainerMEStorage(GuiIds.GuiKey guiKey, InventoryPlayer ip, ITerminalHost host) {
        this(guiKey, ip, host, true);
    }

    protected ContainerMEStorage(GuiIds.GuiKey guiKey, InventoryPlayer ip, ITerminalHost host,
                                 boolean bindInventory) {
        super(ip, host);

        this.guiKey = Objects.requireNonNull(guiKey, "gui key is null");
        this.host = host;
        if (host instanceof IEnergySource) {
            this.energySource = (IEnergySource) host;
        } else if (host instanceof IActionHost) {
            this.energySource = new ActionHostEnergySource((IActionHost) host);
        } else {
            this.energySource = IEnergySource.empty();
        }
        this.storage = Objects.requireNonNull(host.getInventory(), "host inventory is null");

        this.clientCM = IConfigManager.builder(this::onSettingChanged)
                                      .registerSetting(Settings.SORT_BY, SortOrder.NAME)
                                      .registerSetting(Settings.VIEW_MODE, ViewItems.ALL)
                                      .registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING)
                                      .registerSetting(Settings.PATTERN_AUTO_FILL, YesNo.NO)
                                      .build();

        if (isServerSide()) {
            this.serverCM = host.getConfigManager();
        }

        if (!hideViewCells() && host instanceof IViewCellStorage) {
            InternalInventory viewCellStorage = ((IViewCellStorage) host).getViewCellStorage();
            this.viewCellSlots = new ObjectArrayList<>(viewCellStorage.size());
            for (int i = 0; i < viewCellStorage.size(); i++) {
                RestrictedInputSlot slot = new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.VIEW_CELL,
                    viewCellStorage, i);
                this.addSlot(slot, SlotSemantics.VIEW_CELL);
                this.viewCellSlots.add(slot);
            }
        } else {
            this.viewCellSlots = Collections.emptyList();
        }

        this.toolboxInventory = new ToolboxInventory(this);

        setupUpgrades(host.getUpgrades());
        setupWirelessSingularity(host);

        if (bindInventory) {
            this.addPlayerInventorySlots(0, 0);
        }
    }

    private static boolean areStacksEqual(ItemStack left, ItemStack right) {
        return ItemStack.areItemsEqual(left, right) && ItemStack.areItemStackTagsEqual(left, right);
    }

    private static <T> void addSetDifference(Consumer<T> consumer, Set<T> left, Set<T> right) {
        for (var element : left) {
            if (!right.contains(element)) {
                consumer.accept(element);
            }
        }
    }

    private void setupWirelessSingularity(ITerminalHost host) {
        if (host instanceof WirelessTerminalGuiHost<?> wirelessHost) {
            RestrictedInputSlot slot = new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.QE_SINGULARITY,
                wirelessHost.getSingularityStorage(), 0, 0, 0);
            slot.setStackLimit(1);
            this.addSlot(slot, SlotSemantics.WIRELESS_SINGULARITY);
        }
    }

    public ToolboxInventory getToolbox() {
        return toolboxInventory;
    }

    protected boolean hideViewCells() {
        return false;
    }

    @Nullable
    public IGridNode getGridNode() {
        if (host instanceof IActionHost) {
            return ((IActionHost) host).getActionableNode();
        }
        return null;
    }

    public boolean isKeyVisible(AEKey key) {
        if (itemGuiHost != null && itemGuiHost.getItem() instanceof IBasicCellItem i) {
            return i.getKeyType().contains(key);
        }

        return true;
    }

    @Override
    public void broadcastChanges() {
        toolboxInventory.tick();

        if (isServerSide()) {
            this.updateLinkStatus();

            this.updateActiveCraftingJobs();
            this.updateRecursiveIngredientReserveAmount();

            for (Setting<?> set : this.serverCM.getSettings()) {
                if (!canSyncSetting(this.serverCM, this.clientCM, set)) {
                    continue;
                }

                Enum<?> sideLocal = this.serverCM.getSetting(set);
                Enum<?> sideRemote = this.clientCM.getSetting(set);

                if (sideLocal != sideRemote) {
                    set.copy(serverCM, clientCM);
                    if (getPlayer() instanceof EntityPlayerMP) {
                        InitNetwork.CHANNEL.sendTo(new ConfigValuePacket(set, serverCM), (EntityPlayerMP) getPlayer());
                    }
                }
            }

            if (host instanceof KeyTypeSelectionHost) {
                this.searchKeyTypes = new SyncedKeyTypes(((KeyTypeSelectionHost) host).getKeyTypeSelection().enabled());
            }

            Set<AEKey> craftables = getCraftablesFromGrid();
            KeyCounter availableStacks = storage.getAvailableStacks();

            KeyCounter requestables = new KeyCounter();

            try {
                addSetDifference(updateHelper::addChange, previousCraftables, craftables);
                addSetDifference(updateHelper::addChange, craftables, previousCraftables);

                previousAvailableStacks.removeAll(availableStacks);
                previousAvailableStacks.removeZeros();
                previousAvailableStacks.keySet().forEach(updateHelper::addChange);

                if (updateHelper.hasChanges()) {
                    MEInventoryUpdatePacket.Builder builder = MEInventoryUpdatePacket.builder(updateHelper.isFullUpdate());
                    builder.setFilter(this::isKeyVisible);
                    builder.addChanges(updateHelper, availableStacks, craftables, requestables);
                    builder.buildAndSend(this::sendPacketToClient);
                    updateHelper.commitChanges();
                }

            } catch (Exception e) {
                AELog.warn(e, "Failed to send incremental inventory update to client");
            }

            previousCraftables = new ObjectOpenHashSet<>(craftables);
            previousAvailableStacks = availableStacks;

            super.broadcastChanges();
        }

    }

    @Override
    public void onServerDataSync(ShortSet updatedFields) {
        super.onServerDataSync(updatedFields);

        if (updatedFields.contains(SEARCH_KEY_TYPES_ID)) {
            if (getGui() != null) {
                getGui().run();
            }
        }
    }

    protected boolean showsCraftables() {
        return true;
    }

    private Set<AEKey> getCraftablesFromGrid() {
        IGridNode hostNode = getGridNode();
        if (hostNode == null && host instanceof IActionHost) {
            hostNode = ((IActionHost) host).getActionableNode();
        }
        if (!showsCraftables()) {
            return Collections.emptySet();
        }

        if (hostNode != null && hostNode.isActive()) {
            return hostNode.grid().getCraftingService().getCraftables(this::isKeyVisible);
        }
        return Collections.emptySet();
    }

    private void updateActiveCraftingJobs() {
        IGridNode hostNode = getGridNode();
        IGrid grid = null;
        if (hostNode != null) {
            grid = hostNode.grid();
        }

        if (grid == null) {
            this.activeCraftingJobs = -1;
            return;
        }

        int activeJobs = 0;
        for (ICraftingCPU cpu : grid.getCraftingService().getCpus()) {
            if (cpu.isBusy()) {
                activeJobs++;
            }
        }
        this.activeCraftingJobs = activeJobs;
    }

    private void updateRecursiveIngredientReserveAmount() {
        IGridNode hostNode = getGridNode();
        if (hostNode == null || !hostNode.isActive()) {
            return;
        }

        long amount = hostNode.grid().getCraftingService().getRecursiveIngredientReserveAmount();
        if (amount == this.lastSentRecursiveIngredientReserveAmount) {
            return;
        }

        this.lastSentRecursiveIngredientReserveAmount = amount;
        this.recursiveIngredientReserveAmount = amount;
        if (getPlayer() instanceof EntityPlayerMP player) {
            InitNetwork.CHANNEL.sendTo(new RecursiveIngredientReserveAmountPacket(amount), player);
        }
    }

    public long getRecursiveIngredientReserveAmount() {
        return this.recursiveIngredientReserveAmount;
    }

    public void setRecursiveIngredientReserveAmount(long amount) {
        long clampedAmount = Math.clamp(amount, 0, PatternDetailsHelper.MAX_PROCESSING_PATTERN_AMOUNT);
        this.recursiveIngredientReserveAmount = clampedAmount;
        if (isServerSide()) {
            this.lastSentRecursiveIngredientReserveAmount = clampedAmount;
        }
    }

    private void onSettingChanged(IConfigManager manager, Setting<?> setting) {
        if (this.getGui() != null) {
            this.getGui().run();
        }
    }

    static boolean canSyncSetting(IConfigManager serverConfig, IConfigManager clientConfig, Setting<?> setting) {
        return serverConfig.hasSetting(setting) && clientConfig.hasSetting(setting);
    }

    @Override
    public IConfigManager getConfigManager() {
        if (isServerSide()) {
            return this.serverCM;
        }
        return this.clientCM;
    }

    public List<ItemStack> getViewCells() {
        return this.viewCellSlots.stream()
                                 .map(AppEngSlot::getStack)
                                 .collect(Collectors.toList());
    }

    /**
     * Checks that the inventory monitor is connected, a power source exists and that it is powered.
     */
    protected final boolean canInteractWithGrid() {
        return getLinkStatus().connected();
    }

    @Override
    public final void handleInteraction(long serial, InventoryAction action) {
        if (isClientSide()) {
            InitNetwork.sendToServer(new MEInteractionPacket(windowId, serial, action));
            return;
        }

        if (!canInteractWithGrid()) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) this.getPlayerInventory().player;

        if (serial == -1) {
            handleNetworkInteraction(player, null, action);
            return;
        }

        AEKey stack = getStackBySerial(serial);
        if (stack == null) {
            return;
        }

        handleNetworkInteraction(player, stack, action);
    }

    protected void handleNetworkInteraction(EntityPlayerMP player, @Nullable AEKey clickedKey, InventoryAction action) {

        if (!canInteractWithGrid()) {
            return;
        }

        if (action == InventoryAction.AUTO_CRAFT) {
            GuiHostLocator locator = getLocator();
            if (locator != null && clickedKey != null) {
                ContainerCraftAmount.open(player, locator, clickedKey, clickedKey.getAmountPerUnit());
            }
            return;
        }

        switch (action) {
            case FILL_ITEM -> tryFillContainerItem(clickedKey, false, false);
            case FILL_ITEM_MOVE_TO_PLAYER -> tryFillContainerItem(clickedKey, true, false);
            case FILL_ENTIRE_ITEM -> tryFillContainerItem(clickedKey, false, true);
            case FILL_ENTIRE_ITEM_MOVE_TO_PLAYER -> tryFillContainerItem(clickedKey, true, true);
            case EMPTY_ITEM -> handleEmptyHeldItem(
                (what, amount, mode) -> StorageHelper.poweredInsert(energySource, storage, what, amount,
                    getActionSource(), mode),
                false);
            case EMPTY_ENTIRE_ITEM -> handleEmptyHeldItem(
                (what, amount, mode) -> StorageHelper.poweredInsert(energySource, storage, what, amount,
                    getActionSource(), mode),
                true);
            default -> {
            }
        }

        if (clickedKey == null) {
            if (action == InventoryAction.SPLIT_OR_PLACE_SINGLE || action == InventoryAction.ROLL_DOWN) {
                putCarriedItemIntoNetwork(true);
            } else if (action == InventoryAction.PICKUP_OR_SET_DOWN) {
                putCarriedItemIntoNetwork(false);
            }
            return;
        }

        if (!(clickedKey instanceof AEItemKey clickedItem)) {
            return;
        }

        switch (action) {
            case SHIFT_CLICK -> moveOneStackToPlayer(clickedItem);
            case ROLL_DOWN -> {
                ItemStack carried = getCarried();
                if (!carried.isEmpty()) {
                    AEItemKey what = AEItemKey.of(carried);
                    long inserted = StorageHelper.poweredInsert(energySource, storage, what, 1, this.getActionSource());
                    if (inserted > 0) {
                        getCarried().shrink(1);
                    }
                }
            }
            case ROLL_UP, PICKUP_SINGLE -> {
                ItemStack item = getCarried();

                if (!item.isEmpty()) {
                    if (item.getCount() >= item.getMaxStackSize()) {
                        return;
                    }
                    if (!clickedItem.matches(item)) {
                        return;
                    }
                }

                long extracted = StorageHelper.poweredExtraction(energySource, storage, clickedItem, 1,
                    this.getActionSource());
                if (extracted > 0) {
                    if (item.isEmpty()) {
                        setCarried(clickedItem.toStack());
                    } else {
                        item.grow(1);
                    }
                }
            }
            case PICKUP_OR_SET_DOWN -> {
                if (!getCarried().isEmpty()) {
                    putCarriedItemIntoNetwork(false);
                } else {
                    long extracted = StorageHelper.poweredExtraction(
                        energySource,
                        storage,
                        clickedItem,
                        clickedItem.getMaxStackSize(),
                        this.getActionSource());
                    if (extracted > 0) {
                        setCarried(clickedItem.toStack((int) extracted));
                    } else {
                        setCarried(ItemStack.EMPTY);
                    }
                }
            }
            case SPLIT_OR_PLACE_SINGLE -> {
                if (!getCarried().isEmpty()) {
                    putCarriedItemIntoNetwork(true);
                } else {
                    long extracted = storage.extract(
                        clickedItem,
                        clickedItem.getMaxStackSize(),
                        Actionable.SIMULATE,
                        this.getActionSource());

                    if (extracted > 0) {
                        extracted = extracted + 1 >> 1;
                        extracted = StorageHelper.poweredExtraction(energySource, storage, clickedItem, extracted,
                            this.getActionSource());
                    }

                    if (extracted > 0) {
                        setCarried(clickedItem.toStack((int) extracted));
                    } else {
                        setCarried(ItemStack.EMPTY);
                    }
                }
            }
            case CREATIVE_DUPLICATE -> {
                if (player.capabilities.isCreativeMode) {
                    ItemStack stack = clickedItem.toStack();
                    stack.setCount(stack.getMaxStackSize());
                    setCarried(stack);
                }
            }
            case MOVE_REGION -> {
                final int playerInv = player.inventory.mainInventory.size();
                for (int slotNum = 0; slotNum < playerInv; slotNum++) {
                    if (!moveOneStackToPlayer(clickedItem)) {
                        break;
                    }
                }
            }
            default -> {
            }
        }
    }

    public boolean retrieveItemToPlayer(AEItemKey what) {
        if (isClientSide() || !canInteractWithGrid()) {
            return false;
        }
        return moveOneStackToPlayer(what);
    }

    public boolean openCraftAmount(EntityPlayerMP player, AEKey what) {
        if (isClientSide() || !canInteractWithGrid()) {
            return false;
        }

        IGridNode node = getGridNode();
        if (node == null || !node.isActive() || !node.grid().getCraftingService().isCraftable(what)) {
            return false;
        }

        GuiHostLocator locator = getLocator();
        if (locator == null) {
            return false;
        }

        ContainerCraftAmount.open(player, locator, what, what.getAmountPerUnit());
        return true;
    }

    private void tryFillContainerItem(@Nullable AEKey clickedKey, boolean moveToPlayer, boolean fillAll) {
        boolean grabbedEmptyBucket = false;
        AEFluidKey fluidKey = clickedKey instanceof AEFluidKey ? (AEFluidKey) clickedKey : null;
        if (getCarried().isEmpty() && fluidKey != null
            && fluidKey.getFluid().getBlock() != null) {
            if (storage.extract(AEItemKey.of(Items.BUCKET), 1, Actionable.MODULATE, getActionSource()) >= 1) {
                setCarried(new ItemStack(Items.BUCKET));
                grabbedEmptyBucket = true;
            }
        }

        ItemStack carriedBefore = getCarried().copy();

        handleFillingHeldItem(
            new FillingSource() {
                @Override
                public long extract(long amount, Actionable mode) {
                    return StorageHelper.poweredExtraction(energySource, storage, clickedKey, amount, getActionSource(),
                        mode);
                }

                @Override
                public long insert(AEKey what, long amount, Actionable mode) {
                    return StorageHelper.poweredInsert(energySource, storage, what, amount, getActionSource(), mode);
                }
            },
            clickedKey, fillAll);

        if (grabbedEmptyBucket && getCarried().getItem() == Items.BUCKET) {
            long inserted = storage.insert(AEItemKey.of(getCarried()), getCarried().getCount(), Actionable.MODULATE,
                getActionSource());
            ItemStack newCarried = getCarried().copy();
            newCarried.shrink(Ints.saturatedCast(inserted));
            setCarried(newCarried);
        }
        if (moveToPlayer && !areStacksEqual(getCarried(), carriedBefore)) {
            if (getPlayer().inventory.addItemStackToInventory(getCarried())) {
                setCarried(ItemStack.EMPTY);
            }
        }
    }

    protected void putCarriedItemIntoNetwork(boolean singleItem) {
        ItemStack heldStack = getCarried();

        if (!singleItem && GenericResourcePackageItem.isPackage(heldStack)) {
            PackageInsertResult result = GenericResourcePackageItem.tryInsertPackage(heldStack, energySource, storage,
                this.getActionSource(), Actionable.MODULATE);
            setCarried(result.remainder());
            return;
        }

        AEItemKey what = AEItemKey.of(heldStack);
        if (what == null) {
            return;
        }

        long amount = heldStack.getCount();
        if (singleItem) {
            amount = 1;
        }

        long inserted = StorageHelper.poweredInsert(energySource, storage, what, amount,
            this.getActionSource());
        ItemStack remainder = heldStack.copy();
        remainder.shrink(Ints.saturatedCast(inserted));
        setCarried(remainder);
    }

    /**
     * Inserts an item shortcut stack from another inventory into the ME network.
     */
    public ItemStack insertBogoSorterShortcutStack(ItemStack input) {
        if (!canInteractWithGrid() || input.isEmpty()) {
            return input;
        }

        AEItemKey key = AEItemKey.of(input);
        if (key == null || !isKeyVisible(key)) {
            return input;
        }

        long inserted = StorageHelper.poweredInsert(energySource, storage, key, input.getCount(), getActionSource());
        ItemStack remainder = input.copy();
        remainder.shrink(Ints.saturatedCast(inserted));
        return remainder;
    }

    private boolean moveOneStackToPlayer(AEItemKey what) {
        long potentialAmount = storage.extract(what, what.getMaxStackSize(), Actionable.SIMULATE, getActionSource());
        if (potentialAmount <= 0) {
            return false;
        }

        List<Slot> destinationSlots = getQuickMoveDestinationSlots(what.toStack(), false);

        for (Slot destinationSlot : destinationSlots) {
            int amount = getPlaceableAmount(destinationSlot, what);
            if (amount <= 0) {
                continue;
            }

            long extracted = StorageHelper.poweredExtraction(energySource, storage, what, amount, getActionSource());
            if (extracted == 0) {
                return false;
            }

            ItemStack currentItem = destinationSlot.getStack();
            if (!currentItem.isEmpty()) {
                ItemStack newStack = currentItem.copy();
                newStack.setCount(newStack.getCount() + (int) extracted);
                destinationSlot.putStack(newStack);
            } else {
                destinationSlot.putStack(what.toStack((int) extracted));
            }
            destinationSlot.onSlotChanged();
            return true;
        }

        return false;
    }

    @Nullable
    protected final AEKey getStackBySerial(long serial) {
        return updateHelper.getBySerial(serial);
    }

    public ILinkStatus getLinkStatus() {
        return linkStatus;
    }

    @Override
    public void setLinkStatus(ILinkStatus linkStatus) {
        this.linkStatus = linkStatus;
    }

    @Nullable
    private Runnable getGui() {
        return this.gui;
    }

    /**
     * Sets the current screen.
     * The screen is notified when settings change.
     * It updates its sorting after the notification.
     */
    public void setGui(@Nullable Runnable gui) {
        this.gui = gui;
    }

    @Nullable
    public IClientRepo getClientRepo() {
        return clientRepo;
    }

    public void setClientRepo(@Nullable IClientRepo clientRepo) {
        this.clientRepo = clientRepo;
    }

    /**
     * Try to transfer an item stack into the grid.
     */
    @Override
    protected ItemStack transferStackToContainerWithRemainder(ItemStack input) {
        if (!canInteractWithGrid()) {
            return super.transferStackToContainerWithRemainder(input);
        }

        if (GenericResourcePackageItem.isPackage(input)) {
            PackageInsertResult result = GenericResourcePackageItem.tryInsertPackage(input, energySource, storage,
                this.getActionSource(), Actionable.MODULATE);
            return result.remainder();
        }

        return super.transferStackToContainerWithRemainder(input);
    }

    @Override
    protected int transferStackToContainer(ItemStack input) {
        if (!canInteractWithGrid()) {
            return super.transferStackToContainer(input);
        }

        AEItemKey key = AEItemKey.of(input);
        if (key == null || !isKeyVisible(key)) {
            return 0;
        }

        return (int) StorageHelper.poweredInsert(energySource, storage,
            key, input.getCount(),
            this.getActionSource());
    }

    /**
     * Checks if the terminal has a given reservedAmounts of the requested item. Used to determine for recipe-view
     * integrations if a
     * recipe is potentially craftable based on the available items.
     * <p/>
     * This method is <strong>slow</strong>, but it is client-only and thus doesn't scale with the player count.
     */
    public boolean hasIngredient(Ingredient ingredient, Object2IntMap<Object> reservedAmounts) {
        IClientRepo clientRepo = getClientRepo();

        if (clientRepo != null && getLinkStatus().connected()) {
            for (GridInventoryEntry stack : clientRepo.getByIngredient(ingredient)) {
                int reservedAmount = reservedAmounts.getInt(stack);
                if (stack.storedAmount() - reservedAmount >= 1) {
                    reservedAmounts.put(stack, reservedAmount + 1);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * @return The stacks available in the storage as determined the last time this container was ticked.
     */
    @SuppressWarnings("unused")
    protected final KeyCounter getPreviousAvailableStacks() {
        Preconditions.checkState(isServerSide());
        return previousAvailableStacks;
    }

    public boolean canConfigureTypeFilter() {
        return this.host instanceof KeyTypeSelectionHost;
    }

    public ITerminalHost getHost() {
        return host;
    }

    public GuiIds.GuiKey getGuiKey() {
        return guiKey;
    }

    protected void updateLinkStatus() {
        ILinkStatus linkStatus = host.getLinkStatus();
        if (!Objects.equals(this.linkStatus, linkStatus)) {
            this.linkStatus = linkStatus;
            sendPacketToClient(new SetLinkStatusPacket(linkStatus));
        }
    }

    @Override
    public KeyTypeSelection getServerKeyTypeSelection() {
        return ((KeyTypeSelectionHost) host).getKeyTypeSelection();
    }

    @Override
    public SyncedKeyTypes getClientKeyTypeSelection() {
        return searchKeyTypes;
    }
}
