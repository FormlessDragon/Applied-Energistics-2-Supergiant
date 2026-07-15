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

package ae2.me.service;

import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.IGridServiceProvider;
import ae2.api.networking.energy.IAEPowerStorage;
import ae2.api.networking.energy.IEnergyService;
import ae2.api.networking.energy.IEnergyWatcher;
import ae2.api.networking.energy.IEnergyWatcherNode;
import ae2.api.networking.energy.IPassiveEnergyGenerator;
import ae2.api.networking.events.GridPowerIdleChange;
import ae2.api.networking.events.GridPowerStatusChange;
import ae2.api.networking.events.GridPowerStorageChanged;
import ae2.api.networking.events.GridPowerStorageStateChanged;
import ae2.api.networking.pathing.IPathingService;
import ae2.core.AEConfig;
import ae2.core.AELog;
import ae2.me.Grid;
import ae2.me.GridNode;
import ae2.me.energy.EnergyThreshold;
import ae2.me.energy.EnergyWatcher;
import ae2.me.energy.GridEnergyStorage;
import ae2.me.energy.IEnergyOverlayGridConnection;
import ae2.tile.networking.TileCreativeEnergyCell;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeSet;

public class EnergyService implements IEnergyService, IGridServiceProvider {
    private static final String TAG_STORED_ENERGY = "e";
    private static final int NBT_DOUBLE = 6;
    private static final double CREATIVE_POWER = (double) Long.MAX_VALUE / 10000;

    static {
        GridHelper.addGridServiceEventHandler(GridPowerIdleChange.class, IEnergyService.class,
            (service, event) -> ((EnergyService) service).nodeIdlePowerChangeHandler(event));
        GridHelper.addGridServiceEventHandler(GridPowerStorageStateChanged.class, IEnergyService.class,
            (service, event) -> ((EnergyService) service).storagePowerChangeHandler(event));
        GridHelper.addGridServiceEventHandler(GridPowerStorageChanged.class, IEnergyService.class,
            (service, event) -> ((EnergyService) service).storagePowerChangeHandler(event));
    }

    final Grid grid;
    private final NavigableSet<EnergyThreshold> interests = new TreeSet<>();
    private final EnergyPowerStatistics energyPowerStatistics = new EnergyPowerStatistics();
    private final EnergyStorageGroup energyStorageGroup = new EnergyStorageGroup(this.energyPowerStatistics);
    private final Multiset<IEnergyOverlayGridConnection> overlayGridConnections = HashMultiset.create();
    private final Reference2ObjectMap<IGridNode, IEnergyWatcher> watchers = new Reference2ObjectOpenHashMap<>();
    private final GridEnergyStorage localStorage;
    private final PathingService pgc;
    /**
     * Passive generators available on this energy grid.
     */
    private final ReferenceSet<IPassiveEnergyGenerator> passiveGenerators = new ReferenceOpenHashSet<>();
    /**
     * The overlay grid containing all the energy services of grids that may be connected by parts like
     * {@linkplain ae2.parts.networking.QuartzFiberPart quartz fibers}.
     */
    EnergyOverlayGrid overlayGrid = null;
    /**
     * idle draw.
     */
    private double drainPerTick = 0;
    private double avgDrainPerTick = 0;
    private double avgInjectionPerTick = 0;
    /**
     * power status
     */
    private boolean publicHasPower = false;
    private boolean hasPower = true;
    private long ticksSinceHasPowerChange = 900;
    private double lastStoredPower = -1;
    private int creativeEnergyCellCount;

    public EnergyService(IGrid g, IPathingService pgc) {
        this.grid = (Grid) g;
        this.pgc = (PathingService) pgc;
        this.localStorage = new GridEnergyStorage(grid);
        this.energyStorageGroup.register(this.localStorage);
    }

    public void nodeIdlePowerChangeHandler(GridPowerIdleChange ev) {
// update power usage based on event.
        final var node = (GridNode) ev.node;

        final double newDraw = node.getIdlePowerUsage();
        final double diffDraw = newDraw - node.getPreviousDraw();
        node.setPreviousDraw(newDraw);

        this.drainPerTick += diffDraw;
    }

    public void storagePowerChangeHandler(GridPowerStorageStateChanged ev) {
        refreshRegisteredStorage(ev.storage);
    }

    public void storagePowerChangeHandler(GridPowerStorageChanged ev) {
        if (!this.energyStorageGroup.contains(ev.storage)) {
            AELog.warn("Ignoring a power-storage change event for an unregistered storage.");
            return;
        }

        switch (ev.type) {
            case VALUES_CHANGED -> refreshRegisteredStorage(ev.storage);
            case ROUTING_CHANGED -> invalidateOverlayStorageCache();
        }
    }

    @Override
    public void onServerStartTick() {
        if (isCreativePowerModeActive()) {
            var overlay = getOverlayGrid();
            overlay.setCurrentPassiveGenerator(null);
            for (var passiveGenerator : passiveGenerators) {
                passiveGenerator.setSuppressed(true);
            }
            return;
        }

        for (var passiveGenerator : passiveGenerators) {
// Inject the passive energy once per overlay grid and do it at the energy service that actually
            var currentGenerator = getOverlayGrid().getCurrentPassiveGenerator();
            if (currentGenerator == passiveGenerator) {
// Strictly worse than the current generator
                passiveGenerator.setSuppressed(false);
            } else if (currentGenerator == null || passiveGenerator.getRate() > currentGenerator.getRate()) {
                if (currentGenerator != null) {
                    currentGenerator.setSuppressed(true);
// contains the passive generator.
                }
                getOverlayGrid().setCurrentPassiveGenerator(passiveGenerator);
                passiveGenerator.setSuppressed(false);
            } else {
                passiveGenerator.setSuppressed(true);
            }
        }
    }

    @Override
    public void onServerEndTick() {
        if (isCreativePowerModeActive()) {
            var overlay = getOverlayGrid();
            overlay.setCurrentPassiveGenerator(null);
            for (var passiveGenerator : passiveGenerators) {
                passiveGenerator.setSuppressed(true);
            }

            if (!this.interests.isEmpty()) {
                final double oldPower = this.lastStoredPower;
                this.lastStoredPower = this.getStoredPower();

                final EnergyThreshold low = new EnergyThreshold(Math.min(oldPower, this.lastStoredPower),
                    Integer.MIN_VALUE);
                final EnergyThreshold high = new EnergyThreshold(Math.max(oldPower, this.lastStoredPower),
                    Integer.MAX_VALUE);

                for (EnergyThreshold th : this.interests.subSet(low, true, high, true)) {
                    ((EnergyWatcher) th.getEnergyWatcher()).post(this);
                }
            }

            this.energyPowerStatistics.reset();
            double averageLength = 40.0;
            this.avgDrainPerTick *= (averageLength - 1) / averageLength;
            this.avgInjectionPerTick *= (averageLength - 1) / averageLength;
            this.hasPower = true;
            this.ticksSinceHasPowerChange = 900;
            this.publicPowerState(true, this.grid);
            return;
        }

        var currentPassiveGenerator = getOverlayGrid().getCurrentPassiveGenerator();
// If the node came with buffered energy, add it to our internal storage
        if (currentPassiveGenerator != null && passiveGenerators.contains(currentPassiveGenerator)) {
            injectPower(currentPassiveGenerator.getRate(), Actionable.MODULATE);
        }

        if (!this.interests.isEmpty()) {
            final double oldPower = this.lastStoredPower;
            this.lastStoredPower = this.getStoredPower();

            final EnergyThreshold low = new EnergyThreshold(Math.min(oldPower, this.lastStoredPower),
                Integer.MIN_VALUE);
            final EnergyThreshold high = new EnergyThreshold(Math.max(oldPower, this.lastStoredPower),
                Integer.MAX_VALUE);

            for (EnergyThreshold th : this.interests.subSet(low, true, high, true)) {
                ((EnergyWatcher) th.getEnergyWatcher()).post(this);
            }
        }

        double averageLength = 40.0;
        this.avgDrainPerTick *= (averageLength - 1) / averageLength;
        this.avgInjectionPerTick *= (averageLength - 1) / averageLength;

        this.avgDrainPerTick += this.energyPowerStatistics.getPowerExtraction() / averageLength;
        this.avgInjectionPerTick += this.energyPowerStatistics.getPowerInjection() / averageLength;
        this.energyPowerStatistics.reset();

        final boolean currentlyHasPower;
        if (this.drainPerTick > 0.0001) {
            final double drained = this.extractAEPower(this.getIdlePowerUsage(), Actionable.MODULATE,
                PowerMultiplier.CONFIG);
            currentlyHasPower = drained >= this.drainPerTick - 0.001;
        } else {
            currentlyHasPower = this.extractAEPower(0.1, Actionable.SIMULATE, PowerMultiplier.CONFIG) > 0;
        }

        if (currentlyHasPower == this.hasPower) {
            this.ticksSinceHasPowerChange++;
        } else {
            this.ticksSinceHasPowerChange = 0;
        }

        this.hasPower = currentlyHasPower;

        if (this.hasPower && this.ticksSinceHasPowerChange > 30) {
            this.publicPowerState(true, this.grid);
        } else if (!this.hasPower) {
            this.publicPowerState(false, this.grid);
        }

    }

    @Override
    public double getIdlePowerUsage() {
        return this.drainPerTick + this.pgc.getChannelPowerUsage();
    }

    @Override
    public double getChannelPowerUsage() {
        return this.pgc.getChannelPowerUsage();
    }

    private void publicPowerState(boolean newState, IGrid grid) {
        if (this.publicHasPower == newState) {
            return;
        }

        this.publicHasPower = newState;
        this.grid.setImportantFlag(0, this.publicHasPower);
        grid.postEvent(new GridPowerStatusChange());

        this.grid.notifyAllNodes(IGridNodeListener.State.POWER);
    }

    public Collection<IEnergyOverlayGridConnection> getOverlayGridConnections() {
        return this.overlayGridConnections;
    }

    @Override
    public double extractAEPower(double amt, Actionable mode, PowerMultiplier pm) {
        Preconditions.checkArgument(Double.isFinite(amt) && amt >= 0, "amt must be finite and >= 0");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(pm, "pm");

        var overlay = getOverlayGrid();
        if (isCreativePowerModeActive(overlay)) {
            return amt;
        }

        final double toExtract = pm.multiply(amt);
        Preconditions.checkArgument(Double.isFinite(toExtract) && toExtract >= 0,
            "multiplied power must be finite and >= 0");
        return pm.divide(overlay.extractPower(this, toExtract, mode));
    }

    @Override
    public double getAvgPowerUsage() {
        return this.avgDrainPerTick;
    }

    @Override
    public double getAvgPowerInjection() {
        return this.avgInjectionPerTick;
    }

    @Override
    public boolean isNetworkPowered() {
        return this.publicHasPower;
    }

    @Override
    public double getStoredPower() {
        var overlay = getOverlayGrid();
        return isCreativePowerModeActive(overlay) ? CREATIVE_POWER : overlay.getStoredPower(this);
    }

    @Override
    public double injectPower(double amt, Actionable mode) {
        Preconditions.checkArgument(Double.isFinite(amt) && amt >= 0, "amt must be finite and >= 0");
        Objects.requireNonNull(mode, "mode");

        var overlay = getOverlayGrid();
        if (isCreativePowerModeActive(overlay)) {
            return amt;
        }

        return overlay.injectPower(this, amt, mode);
    }

    @Override
    public void saveNodeData(IGridNode gridNode, NBTTagCompound savedData) {
// When node-data is saved, we allocate it 1/N of our stored local energy
        var perNodeStorage = localStorage.getNodeEnergyShare();
        if (perNodeStorage > 0) {
            savedData.setDouble(TAG_STORED_ENERGY, perNodeStorage);
        }
    }

    public boolean registerEnergyInterest(EnergyThreshold threshold) {
        return this.interests.add(threshold);
    }

    public boolean unregisterEnergyInterest(EnergyThreshold threshold) {
        return this.interests.remove(threshold);
    }

    public void invalidateOverlayEnergyGrid() {
        var currentOverlay = this.overlayGrid;
        if (currentOverlay != null) {
            currentOverlay.invalidate();
        }
    }

    private void invalidateOverlayStorageCache() {
        var currentOverlay = this.overlayGrid;
        if (currentOverlay != null) {
            currentOverlay.invalidateStorageCache();
        }
    }

    public boolean isCreativePowerModeActive() {
        return isCreativePowerModeActive(getOverlayGrid());
    }

    private boolean isCreativePowerModeActive(EnergyOverlayGrid overlay) {
        return AEConfig.instance().isDebugEnergyEnabled() || overlay.hasCreativePowerSource();
    }

    public void onCreativePowerModeChanged() {
        if (this.overlayGrid != null) {
            this.overlayGrid.setCurrentPassiveGenerator(null);
            this.overlayGrid.invalidateStorageCache();
        }

        if (isCreativePowerModeActive()) {
            this.hasPower = true;
            this.ticksSinceHasPowerChange = 900;
            this.publicPowerState(true, this.grid);
        } else {
            this.hasPower = false;
            this.ticksSinceHasPowerChange = 0;
        }
    }

    boolean hasCreativeEnergyCell() {
        return this.creativeEnergyCellCount > 0;
    }

    private EnergyOverlayGrid getOverlayGrid() {
        var currentOverlay = this.overlayGrid;
        if (currentOverlay == null || !currentOverlay.isAttachedTo(this)) {
            this.overlayGrid = null;
            EnergyOverlayGrid.buildCache(this);
            currentOverlay = this.overlayGrid;
        }
        return Objects.requireNonNull(currentOverlay);
    }

    @Override
    public double getMaxStoredPower() {
        var overlay = getOverlayGrid();
        return isCreativePowerModeActive(overlay) ? CREATIVE_POWER : overlay.getMaximumPower(this);
    }

    @Override
    public double getEnergyDemand(double maxRequired) {
        Preconditions.checkArgument(Double.isFinite(maxRequired) && maxRequired >= 0,
            "maxRequired must be finite and >= 0");

        var overlay = getOverlayGrid();
        if (isCreativePowerModeActive(overlay)) {
            return 0;
        }

        return overlay.getEnergyDemand(this, maxRequired);
    }

    @Override
    public void removeNode(IGridNode node) {
        var currentOverlay = this.overlayGrid;
        invalidateOverlayStorageCache();
        localStorage.removeNode();

        var passiveGenerator = node.getService(IPassiveEnergyGenerator.class);
        if (passiveGenerator != null) {
            passiveGenerators.remove(passiveGenerator);
            if (currentOverlay != null && currentOverlay.getCurrentPassiveGenerator() == passiveGenerator) {
                currentOverlay.setCurrentPassiveGenerator(null);
            }
        }

        var ps = node.getService(IAEPowerStorage.class);
        if (ps != null) {
            this.energyStorageGroup.unregister(ps);
        }

        if (node.getOwner() instanceof TileCreativeEnergyCell) {
            Preconditions.checkState(this.creativeEnergyCellCount > 0,
                "Cannot remove a creative energy cell from an empty service count");
            this.creativeEnergyCellCount--;
            if (this.creativeEnergyCellCount == 0 && currentOverlay != null) {
                currentOverlay.removeCreativePowerService();
            }
        }

        var gridProvider = node.getService(IEnergyOverlayGridConnection.class);
        if (gridProvider != null) {
            this.overlayGridConnections.remove(gridProvider);
            invalidateOverlayEnergyGrid();
        }

        final GridNode gridNode = (GridNode) node;
        this.drainPerTick -= gridNode.getPreviousDraw();

        var watcher = this.watchers.remove(node);
        if (watcher != null) {
            watcher.reset();
        }
    }

    @Override
    public void addNode(IGridNode node, @Nullable NBTTagCompound storedData) {
        invalidateOverlayStorageCache();
        localStorage.addNode();

        var ps = node.getService(IAEPowerStorage.class);
        if (ps != null) {
            this.energyStorageGroup.register(ps);
        }

        var gridProvider = node.getService(IEnergyOverlayGridConnection.class);
        if (gridProvider != null) {
            this.overlayGridConnections.add(gridProvider);
            invalidateOverlayEnergyGrid();
        }

        final GridNode gridNode = (GridNode) node;
        gridNode.setPreviousDraw(node.getIdlePowerUsage());
        this.drainPerTick += gridNode.getPreviousDraw();

        var passiveGenerator = node.getService(IPassiveEnergyGenerator.class);
        if (passiveGenerator != null) {
            passiveGenerators.add(passiveGenerator);
        }

        if (node.getOwner() instanceof TileCreativeEnergyCell) {
            Preconditions.checkState(this.creativeEnergyCellCount < Integer.MAX_VALUE,
                "Creative energy cell count overflow");
            boolean serviceAlreadyCreative = this.creativeEnergyCellCount > 0;
            ++this.creativeEnergyCellCount;
            if (!serviceAlreadyCreative && this.overlayGrid != null) {
                this.overlayGrid.addCreativePowerService();
            }
        }

        var ews = node.getService(IEnergyWatcherNode.class);
        if (ews != null) {
            var iw = new EnergyWatcher(this, ews);
            this.watchers.put(node, iw);
            ews.updateWatcher(iw);
        }

        if (storedData != null && storedData.hasKey(TAG_STORED_ENERGY, NBT_DOUBLE)) {
            double buffer = storedData.getDouble(TAG_STORED_ENERGY);
            if (buffer > 0) {
                localStorage.injectAEPower(buffer, Actionable.MODULATE);
            }
        }
    }

    private void refreshRegisteredStorage(IAEPowerStorage storage) {
        if (!this.energyStorageGroup.contains(storage)) {
            AELog.warn("Ignoring a power-storage value event for an unregistered storage.");
            return;
        }

        var currentOverlay = this.overlayGrid;
        if (currentOverlay != null) {
            currentOverlay.refreshStorageValues(this.energyStorageGroup, storage);
        }
    }

    EnergyStorageGroup getEnergyStorageGroup() {
        return this.energyStorageGroup;
    }

}
