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

package appeng.parts.automation;

import appeng.api.behaviors.PlacementStrategy;
import appeng.api.config.Actionable;
import appeng.api.config.FormationPlaneMode;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Setting;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigManagerBuilder;
import appeng.container.GuiIds;
import appeng.container.ISubGui;
import appeng.core.definitions.AEItems;
import appeng.core.gui.GuiOpener;
import appeng.core.settings.TickRates;
import appeng.helpers.IConfigInvHost;
import appeng.helpers.IPriorityHost;
import appeng.items.parts.PartModels;
import appeng.me.helpers.MachineSource;
import appeng.text.TextComponentItemStack;
import appeng.util.ConfigInventory;
import appeng.util.Platform;
import appeng.util.prioritylist.DefaultPriorityList;
import appeng.util.prioritylist.FuzzyPriorityList;
import appeng.util.prioritylist.IPartitionList;
import appeng.util.prioritylist.PrecisePriorityList;
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
    implements IStorageProvider, IPriorityHost, IConfigInvHost, IGridTickable {

    private static final PlaneModels MODELS = new PlaneModels("part/formation_plane",
        "part/formation_plane_on");
    private static final int ACTIVE_OPERATIONS_PER_TICK = 1;
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
            var pos = self.getPos().offset(this.getSide());
            var side = getSide().getOpposite();
            var owningEntityPlayerId = getMainNode().getNode().getOwningPlayerProfileId();
            placementStrategies = StackWorldBehaviors.createPlacementStrategies(
                (WorldServer) self.getWorld(), pos, side, self, owningEntityPlayerId);
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
        if (pos.offset(this.getSide()).equals(neighbor)) {
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
        this.config.readFromChildTag(data, "config");
        remountStorage();
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setInteger("priority", this.getPriority());
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
        return new TickingRequest(TickRates.FormationPlane, !isActiveMode());
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!isActiveMode()) {
            return TickRateModulation.SLEEP;
        }

        if (!getMainNode().isActive() || !canPlaceInTargetChunk()) {
            return TickRateModulation.IDLE;
        }

        return doActivePlacement(node) ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
    }

    private boolean canPlaceInTargetChunk() {
        var self = this.getHost().getTileEntity();
        var targetPos = self.getPos().offset(getSide());
        return Platform.areBlockEntitiesTicking(self.getWorld(), targetPos);
    }

    private boolean doActivePlacement(IGridNode node) {
        updateFilter();

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
