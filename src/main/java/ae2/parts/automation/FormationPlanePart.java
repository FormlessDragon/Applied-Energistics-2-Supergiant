/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
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

package ae2.parts.automation;

import ae2.api.behaviors.PlacementStrategy;
import ae2.api.config.Actionable;
import ae2.api.config.FormationPlaneMode;
import ae2.api.config.FuzzyMode;
import ae2.api.config.IncludeExclude;
import ae2.api.config.Setting;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.security.IActionSource;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.parts.IPartCollisionHelper;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.IStorageMounts;
import ae2.api.storage.IStorageProvider;
import ae2.api.storage.MEStorage;
import ae2.api.storage.StorageHelper;
import ae2.api.util.AECableType;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigManagerBuilder;
import ae2.container.GuiIds;
import ae2.container.ISubGui;
import ae2.core.definitions.AEItems;
import ae2.core.gui.GuiOpener;
import ae2.helpers.IConfigInvHost;
import ae2.helpers.IPriorityHost;
import ae2.helpers.IWorkIntervalHost;
import ae2.items.parts.PartModels;
import ae2.me.helpers.MachineSource;
import ae2.text.TextComponentItemStack;
import ae2.util.ConfigInventory;
import ae2.util.Platform;
import ae2.util.prioritylist.DefaultPriorityList;
import ae2.util.prioritylist.FuzzyPriorityList;
import ae2.util.prioritylist.IPartitionList;
import ae2.util.prioritylist.PrecisePriorityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class FormationPlanePart extends UpgradeablePart
    implements IStorageProvider, IPriorityHost, IConfigInvHost, IGridTickable, IWorkIntervalHost {

    private static final PlaneModels MODELS = new PlaneModels("part/formation_plane",
        "part/formation_plane_on");
    private static final int ACTIVE_OPERATIONS_PER_TICK = 1;
    private static final String WORK_INTERVAL_TAG = "workInterval";
    private static final long DEFAULT_WORK_INTERVAL = 10L;
    private static final Logger LOG = LoggerFactory.getLogger(FormationPlanePart.class);
    private final PlaneConnectionHelper connectionHelper = new PlaneConnectionHelper(this);
    private final MEStorage inventory = new InWorldStorage();
    private final ConfigInventory config;
    private final IActionSource source;
    private boolean wasOnline = false;
    private int priority = 0;
    @Nullable
    private PlacementStrategy placementStrategies;
    private IncludeExclude filterMode = IncludeExclude.WHITELIST;
    private IPartitionList filter;
    private long workInterval = DEFAULT_WORK_INTERVAL;
    private long pendingWorkTicks;

    public FormationPlanePart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().addService(IStorageProvider.class, this);
        getMainNode().addService(IGridTickable.class, this);
        this.source = new MachineSource(this);
        this.config = ConfigInventory.configTypes(63)
                                     .supportedTypes(StackWorldBehaviors.withPlacementStrategy())
                                     .changeListener(this::updateFilter)
                                     .build();
    }

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    @Override
    protected void registerSettings(IConfigManagerBuilder builder) {
        super.registerSettings(builder);
        builder.registerSetting(Settings.PLACE_BLOCK, YesNo.YES);
        builder.registerSetting(Settings.FORMATION_PLANE_MODE, FormationPlaneMode.PASSIVE);
        builder.registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
    }

    protected final PlacementStrategy getPlacementStrategies() {
        if (placementStrategies == null) {
            // Defer initialization until the grid exists
            var node = getMainNode().getNode();
            if (node == null) {
                return PlacementStrategy.noop();
            }
            var self = this.getHost().getTileEntity();
            var side = getSide();
            if (side == null) {
                return PlacementStrategy.noop();
            }
            var pos = self.getPos().offset(side);
            var fromSide = side.getOpposite();
            var owningEntityPlayerId = getMainNode().getNode().getOwningPlayerProfileId();
            placementStrategies = StackWorldBehaviors.createPlacementStrategies(
                (WorldServer) self.getWorld(), pos, fromSide, self, owningEntityPlayerId);
        }
        return placementStrategies;
    }

    protected final void updateFilter() {
        this.filter = createFilter();
        this.filterMode = isUpgradedWith(AEItems.INVERTER_CARD)
            ? IncludeExclude.BLACKLIST
            : IncludeExclude.WHITELIST;
    }

    @Override
    protected int getUpgradeSlots() {
        return 5;
    }

    @Override
    public void upgradesChanged() {
        this.updateFilter();
    }

    @Override
    public void onSettingChanged(IConfigManager manager, Setting<?> setting) {
        this.getHost().markForSave();
        if (setting == Settings.FORMATION_PLANE_MODE) {
            this.remountStorage();
        }
        updateTickingState();
    }

    private void remountStorage() {
        IStorageProvider.requestUpdate(getMainNode());
    }

    private boolean isActiveMode() {
        return getConfigManager().getSetting(Settings.FORMATION_PLANE_MODE) == FormationPlaneMode.ACTIVE;
    }

    private void updateTickingState() {
        getMainNode().ifPresent((grid, node) -> {
            if (isActiveMode()) {
                grid.getTickManager().wakeDevice(node);
            } else {
                grid.getTickManager().sleepDevice(node);
            }
        });
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        var currentOnline = this.getMainNode().isOnline();
        if (this.wasOnline != currentOnline) {
            this.wasOnline = currentOnline;
            this.remountStorage();
            this.updateTickingState();
            this.getHost().markForUpdate();
        }
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        connectionHelper.getBoxes(bch);
    }

    public PlaneConnections getConnections() {
        return connectionHelper.getConnections();
    }

    @Override
    public void onNeighborChanged(IBlockAccess level, BlockPos pos, BlockPos neighbor) {
        var side = getSide();
        if (side == null) {
            connectionHelper.updateConnections();
        } else if (pos.offset(side).equals(neighbor)) {
            // The neighbor this plane is facing has changed
            if (!isClientSide()) {
                getPlacementStrategies().clearBlocked();
            }
        } else {
            connectionHelper.updateConnections();
        }
    }

    @Override
    public void onUpdateShape(EnumFacing side) {
        var ourSide = getSide();
        if (ourSide == null) {
            return;
        }
        // A block might have been changed in front of us
        if (side.equals(ourSide)) {
            if (!isClientSide()) {
                getPlacementStrategies().clearBlocked();
            }
        } else if (ourSide.getAxis() != side.getAxis()) {
            // Changes perpendicular to our side may change the connected plane model to change
            connectionHelper.updateConnections();
        }
    }

    /**
     * Places the given stacks in-world and returns what couldn't be placed.
     *
     * @return The amount that was placed.
     * @see MEStorage#insert
     */
    protected long placeInWorld(AEKey what, long amount, Actionable type) {
        var placeBlock = this.getConfigManager().getSetting(Settings.PLACE_BLOCK);

        return getPlacementStrategies().placeInWorld(what, amount, type, placeBlock != YesNo.YES);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 1;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.priority = data.getInteger("priority");
        this.workInterval = Math.max(1L, data.hasKey(WORK_INTERVAL_TAG) ? data.getLong(WORK_INTERVAL_TAG) : DEFAULT_WORK_INTERVAL);
        this.pendingWorkTicks = 0;
        this.config.readFromChildTag(data, "config");
        remountStorage();
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setInteger("priority", this.getPriority());
        data.setLong(WORK_INTERVAL_TAG, this.workInterval);
        this.config.writeToChildTag(data, "config");
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
    public long getWorkInterval() {
        return this.workInterval;
    }

    @Override
    public void setWorkInterval(long newValue) {
        long clamped = Math.max(1L, newValue);
        if (this.workInterval == clamped) {
            return;
        }
        this.workInterval = clamped;
        this.pendingWorkTicks = 0;
        this.getHost().markForSave();
        this.updateTickingState();
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    @Override
    public void mountInventories(IStorageMounts mounts) {
        if (getMainNode().isOnline() && !isActiveMode()) {
            // Update the filter at least once before registering the inventory
            updateFilter();
            mounts.mount(inventory, priority);
        }
    }

    @Override
    public void returnToMainContainer(EntityPlayer player, ISubGui subGui) {
        GuiOpener.openPartGui(player, getGuiKey(), this, true);
    }

    @Override
    public ItemStack getMainContainerIcon() {
        return getPartItem().asItemStack();
    }

    private void openConfigGui(EntityPlayer player) {
        GuiOpener.openPartGui(player, getGuiKey(), this);
    }

    protected GuiIds.GuiKey getGuiKey() {
        return GuiIds.GuiKey.FORMATION_PLANE;
    }

    /**
     * Creates a partition list to filter stacks being injected into the plane against. If an inverter card is present,
     * it's a blacklist. If a fuzzy card is present and the storage channel supports fuzzy search, it'll be a list with
     * fuzzy support.
     */
    private IPartitionList createFilter() {
        KeyCounter filterKeys = new KeyCounter();
        FuzzyMode fuzzyMode = isUpgradedWith(AEItems.FUZZY_CARD)
            ? this.getConfigManager().getSetting(Settings.FUZZY_MODE)
            : null;
        var slotsToUse = 18 + this.getInstalledUpgrades(AEItems.CAPACITY_CARD) * 9;
        for (var x = 0; x < this.config.size() && x < slotsToUse; x++) {
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

    @Override
    public ConfigInventory getConfig() {
        return config;
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(this.isPowered(), this.isActive());
    }

    @Override
    public Object getModelData() {
        return getConnections();
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 1, !isActiveMode());
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!isActiveMode()) {
            this.pendingWorkTicks = 0;
            return TickRateModulation.SLEEP;
        }

        if (!getMainNode().isActive() || !canPlaceInTargetChunk()) {
            return TickRateModulation.IDLE;
        }

        if (!canRunActivePlacement()) {
            this.pendingWorkTicks = 0;
            return TickRateModulation.IDLE;
        }

        if (!shouldRunWork(ticksSinceLastCall)) {
            return TickRateModulation.IDLE;
        }

        return doActivePlacement(node) ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
    }

    private boolean shouldRunWork(int ticksSinceLastCall) {
        this.pendingWorkTicks += ticksSinceLastCall;
        if (this.pendingWorkTicks < this.workInterval) {
            return false;
        }
        this.pendingWorkTicks %= this.workInterval;
        return true;
    }

    private boolean canPlaceInTargetChunk() {
        var self = this.getHost().getTileEntity();
        var side = getSide();
        if (side == null) {
            return false;
        }
        var targetPos = self.getPos().offset(side);
        return Platform.areBlockEntitiesTicking(self.getWorld(), targetPos);
    }

    private boolean canRunActivePlacement() {
        updateFilter();
        return this.filterMode == IncludeExclude.BLACKLIST || (this.filter != null && !this.filter.isEmpty());
    }

    private boolean doActivePlacement(IGridNode node) {
        var storageService = node.grid().getStorageService();
        var cachedInventory = storageService.getCachedInventory();

        for (var entry : cachedInventory) {
            var what = entry.getKey();
            if (!matchesFilter(what)) {
                continue;
            }

            var amount = Math.min(entry.getLongValue(),
                (long) ACTIVE_OPERATIONS_PER_TICK * what.getAmountPerOperation());
            var placeable = placeInWorld(what, amount, Actionable.SIMULATE);
            if (placeable <= 0) {
                continue;
            }

            var extracted = StorageHelper.poweredExtraction(
                node.grid().getEnergyService(),
                storageService.getInventory(),
                what,
                placeable,
                source,
                Actionable.MODULATE);
            if (extracted <= 0) {
                continue;
            }

            var placed = placeInWorld(what, extracted, Actionable.MODULATE);
            if (placed < extracted) {
                var leftover = extracted - placed;
                leftover -= storageService.getInventory().insert(what, leftover, Actionable.MODULATE, source);
                if (leftover > 0) {
                    LOG.error("Formation plane active placement unexpectedly failed to return {}x{}", leftover, what);
                }
            }

            if (placed > 0) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesFilter(AEKey what) {
        return filter == null || filter.matchesFilter(what, filterMode);
    }

    /**
     * Models the block adjacent to this formation plane as storage.
     */
    class InWorldStorage implements MEStorage {
        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (!matchesFilter(what)) {
                return 0;
            }

            return placeInWorld(what, amount, mode);
        }

        @Override
        public ITextComponent getDescription() {
            return TextComponentItemStack.of(getPartItem().asItemStack());
        }
    }

}
