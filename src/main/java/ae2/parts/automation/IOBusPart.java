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

import ae2.api.config.FuzzyMode;
import ae2.api.config.RedstoneMode;
import ae2.api.config.Setting;
import ae2.api.config.Settings;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.security.IActionSource;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.ISubGuiHost;
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
import ae2.items.parts.PartModels;
import ae2.me.helpers.MachineSource;
import ae2.parts.PartModel;
import ae2.util.ConfigInventory;
import ae2.util.Platform;
import ae2.util.prioritylist.DefaultPriorityList;
import ae2.util.prioritylist.FuzzyPriorityList;
import ae2.util.prioritylist.IPartitionList;
import ae2.util.prioritylist.PrecisePriorityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public abstract class IOBusPart extends UpgradeablePart implements IGridTickable, IConfigInvHost, ISubGuiHost {

    public static final ResourceLocation MODEL_BASE = AppEng.makeId("part/import_bus_base");
    @PartModels
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE,
        AppEng.makeId("part/import_bus_off"));
    @PartModels
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE,
        AppEng.makeId("part/import_bus_on"));
    @PartModels
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE,
        AppEng.makeId("part/import_bus_has_channel"));
    protected final IActionSource source;
    private final ConfigInventory config;
    private final TickRates tickRates;
    // Filter derived from the config
    @Nullable
    private IPartitionList filter;
    private boolean lastRedstone = false;
    /**
     * Indicates that an I/O bus in redstone pulse mode has observed a low to high redstone transition and is waiting to
     * act on this during its next tick.
     */
    private boolean pendingPulse = false;

    public IOBusPart(TickRates tickRates, Set<AEKeyType> supportedKeyTypes, IPartItem<?> partItem) {
        super(partItem);
        this.tickRates = tickRates;
        this.source = new MachineSource(this);
        this.config = ConfigInventory.configTypes(63).supportedTypes(supportedKeyTypes)
                                     .changeListener(this::updateState).build();
        getMainNode().addService(IGridTickable.class, this);
    }

    @Override
    protected void registerSettings(IConfigManagerBuilder builder) {
        super.registerSettings(builder);
        builder.registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        builder.registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
    }

    @Override
    protected int getUpgradeSlots() {
        return 5;
    }

    @Override
    public RedstoneMode getRSMode() {
        return this.getConfigManager().getSetting(Settings.REDSTONE_CONTROLLED);
    }

    private boolean isInPulseMode() {
        return getRSMode() == RedstoneMode.SIGNAL_PULSE;
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 5;
    }

    /**
     * All export and import bus parts have a configuration ui.
     */
    protected abstract GuiIds.GuiKey getGuiKey();

    @Override
    public void upgradesChanged() {
        this.updateState();
    }

    @Override
    public void readFromNBT(NBTTagCompound extra) {
        super.readFromNBT(extra);
        this.config.readFromChildTag(extra, "config");
        this.filter = null;
        this.pendingPulse = isInPulseMode() && extra.getBoolean("pendingPulse");
    }

    @Override
    public void writeToNBT(NBTTagCompound extra) {
        super.writeToNBT(extra);
        this.config.writeToChildTag(extra, "config");
        if (isInPulseMode() && pendingPulse) {
            extra.setBoolean("pendingPulse", true);
        }
    }

    @Override
    public ConfigInventory getConfig() {
        return config;
    }

    protected final IPartitionList getFilter() {
        if (filter == null) {
            KeyCounter filterKeys = new KeyCounter();
            for (var key : getConfig().keySet()) {
                if (key != null) {
                    filterKeys.add(key, 1);
                }
            }
            if (filterKeys.isEmpty()) {
                filter = DefaultPriorityList.INSTANCE;
            } else if (isUpgradedWith(AEItems.FUZZY_CARD)) {
                filter = new FuzzyPriorityList(filterKeys, this.getConfigManager().getSetting(Settings.FUZZY_MODE));
            } else {
                filter = new PrecisePriorityList(filterKeys);
            }
        }
        return filter;
    }

    @Override
    public void onNeighborChanged(IBlockAccess level, BlockPos pos, BlockPos neighbor) {
        if (isInPulseMode()) {
            var hostIsPowered = this.getHost().hasRedstone();
            if (this.lastRedstone != hostIsPowered) {
                this.lastRedstone = hostIsPowered;
                if (this.lastRedstone && !this.pendingPulse) {
                    // Perform the action based on the pulse on the next tick
                    this.pendingPulse = true;
                    getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
                }
            }
        } else {
            // This handles waking up the bus if the adjacent redstone has changed
            updateRedstoneState();
        }
    }

    protected int availableSlots() {
        return Math.min(18 + getInstalledUpgrades(AEItems.CAPACITY_CARD) * 9, this.getConfig().size());
    }

    protected int getOperationsPerTick() {
        return switch (getInstalledUpgrades(AEItems.SPEED_CARD)) {
            case 1 -> 8;
            case 2 -> 32;
            case 3 -> 64;
            case 4 -> 96;
            default -> 1;
        };
    }

    @Override
    public final TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        // Sometimes between being woken up and actually doing work, the config/redstone mode may have changed
        // put us back to sleep if that was the case
        if (isSleeping()) {
            return TickRateModulation.SLEEP;
        }

        if (!canDoBusWork()) {
            return TickRateModulation.IDLE;
        }

        // Reset a potential redstone pulse trigger
        this.pendingPulse = false;

        var hasDoneWork = this.doBusWork(node.grid());

        // We may be back to sleep (i.e. in pulse mode)
        if (isSleeping()) {
            return TickRateModulation.SLEEP;
        } else {
            return hasDoneWork ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
        }
    }

    /**
     * Checks if the bus can actually do something.
     * <p>
     * Currently this tests if the chunk for the target is actually loaded, and if the main node has it's channel and
     * power requirements fulfilled.
     *
     * @return true, if the bus should do its work.
     */
    protected final boolean canDoBusWork() {
        if (!getMainNode().isActive()) {
            return false;
        }

        TileEntity self = this.getHost().getTileEntity();
        var targetPos = self.getPos().offset(getSide());
        return Platform.areBlockEntitiesTicking(self.getWorld(), targetPos);
    }

    private void updateState() {
        filter = null; // rebuild the filter

        updateRedstoneState();
    }

    @Override
    protected void onSettingChanged(IConfigManager manager, Setting<?> setting) {
        super.onSettingChanged(manager, setting);

        updateRedstoneState();

        // Ensure we have an up-to-date last redstone state when pulse mode is activated to
        // correctly detect subsequent pulses
        if (isInPulseMode()) {
            this.lastRedstone = getHost().hasRedstone();
        }
    }

    private void updateRedstoneState() {
        // Clear the pending pulse flag if the upgrade is removed or the config is toggled off
        if (!this.isInPulseMode()) {
            this.pendingPulse = false;
        }

        getMainNode().ifPresent((grid, node) -> {
            if (!this.isSleeping()) {
                grid.getTickManager().wakeDevice(node);
            } else {
                grid.getTickManager().sleepDevice(node);
            }
        });
    }

    @Override
    public final boolean onUseWithoutItem(EntityPlayer player, Vec3d pos) {
        if (!isClientSide()) {
            GuiOpener.openPartGui(player, getGuiKey(), this);
        }
        return true;
    }

    @Override
    public final boolean onUseItemOn(ItemStack heldItem, EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (super.onUseItemOn(heldItem, player, hand, pos)) {
            return true;
        }
        return this.onUseWithoutItem(player, pos);
    }

    @Override
    public final TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(tickRates.getMin(), tickRates.getMax(), isSleeping());
    }

    @Override
    protected boolean isSleeping() {
        if (isInPulseMode() && this.pendingPulse) {
            return false;
        } else {
            return super.isSleeping();
        }
    }

    protected abstract boolean doBusWork(IGrid grid);

    @Override
    public void addToWorld() {
        super.addToWorld();

        // To correctly detect pulses (changes from low to high), we need to know the current state when we
        // are added to the world.
        this.lastRedstone = this.getHost().hasRedstone();
        // We may have observed a redstone pulse before, but were unable to act on it due to being unloaded before
        // we ticked again. Ensure that we do act on this pulse as soon as possible.
        if (pendingPulse) {
            getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
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
}
