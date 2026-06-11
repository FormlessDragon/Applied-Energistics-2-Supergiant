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

import ae2.api.config.AccessRestriction;
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
import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

public class EnergyService implements IEnergyService, IGridServiceProvider {
    private static final String TAG_STORED_ENERGY = "e";
    private static final int NBT_DOUBLE = 6;
    private static final Comparator<IAEPowerStorage> COMPARATOR_HIGHEST_PRIORITY_FIRST = (o1, o2) -> {
        final int cmp = Integer.compare(o2.getPriority(), o1.getPriority());
        return cmp != 0 ? cmp : Integer.compare(System.identityHashCode(o2), System.identityHashCode(o1));
    };
    private static final Comparator<IAEPowerStorage> COMPARATOR_LOWEST_PRIORITY_FIRST = (o1,
                                                                                         o2) -> -COMPARATOR_HIGHEST_PRIORITY_FIRST.compare(o1, o2);

    static {
        GridHelper.addGridServiceEventHandler(GridPowerIdleChange.class, IEnergyService.class,
            (service, event) -> ((EnergyService) service).nodeIdlePowerChangeHandler(event));
        GridHelper.addGridServiceEventHandler(GridPowerStorageStateChanged.class, IEnergyService.class,
            (service, event) -> ((EnergyService) service).storagePowerChangeHandler(event));
    }

    final Grid grid;
    private final NavigableSet<EnergyThreshold> interests = new TreeSet<>();
    // Should only be modified from the add/remove methods below to guard against
    private final SortedSet<IAEPowerStorage> providers = new ObjectRBTreeSet<>(COMPARATOR_HIGHEST_PRIORITY_FIRST);
    // Should only be modified from the add/remove methods below to guard against
    private final SortedSet<IAEPowerStorage> requesters = new ObjectRBTreeSet<>(COMPARATOR_LOWEST_PRIORITY_FIRST);
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
    // Used to track whether an extraction is currently in progress, to fail fast
    private boolean ongoingExtractOperation = false;
    // Used to track whether an injection is currently in progress, to fail fast
    private boolean ongoingInjectOperation = false;
    /**
     * estimated power available.
     */
    private int availableTicksSinceUpdate = 0;
    private double globalAvailablePower = 0;
    private double providerPowerSum;
    /**
     * idle draw.
     */
    private double drainPerTick = 0;
    private double avgDrainPerTick = 0;
    private double avgInjectionPerTick = 0;
    private double tickDrainPerTick = 0;
    private double tickInjectionPerTick = 0;
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
        this.requesters.add(this.localStorage);
        this.providers.add(this.localStorage);
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
        if (ev.storage.isAEPublicPowerStorage()) {
            switch (ev.type) {
                case PROVIDE_POWER -> addProvider(ev.storage);
                case RECEIVE_POWER -> addRequester(ev.storage);
            }
        } else {
            AELog.warn("Attempt to ask the IEnergyGrid to charge a non public energy store.");
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

            double averageLength = 40.0;
            this.avgDrainPerTick *= (averageLength - 1) / averageLength;
            this.avgInjectionPerTick *= (averageLength - 1) / averageLength;
            this.tickDrainPerTick = 0;
            this.tickInjectionPerTick = 0;
            this.hasPower = true;
            this.ticksSinceHasPowerChange = 900;
            this.publicPowerState(true, this.grid);
            this.availableTicksSinceUpdate++;
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

        this.avgDrainPerTick += this.tickDrainPerTick / averageLength;
        this.avgInjectionPerTick += this.tickInjectionPerTick / averageLength;

        this.tickDrainPerTick = 0;
        this.tickInjectionPerTick = 0;

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

        this.availableTicksSinceUpdate++;
    }

    private static double sanitizePowerAmount(double amount) {
        return Double.isFinite(amount) && amount > 0 ? amount : 0;
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

    private static double clampPowerResult(double amount, double requested) {
        if (Double.isNaN(amount) || amount <= 0) {
            return 0;
        }
        if (!Double.isFinite(amount)) {
            return requested;
        }
        return Math.min(amount, requested);
    }

    public Collection<IEnergyOverlayGridConnection> getOverlayGridConnections() {
        return this.overlayGridConnections;
    }

    private static double clampRemainingPower(double amount, double requested) {
        if (Double.isNaN(amount)) {
            return requested;
        }
        if (amount <= 0) {
            return 0;
        }
        if (!Double.isFinite(amount)) {
            return requested;
        }
        return Math.min(amount, requested);
    }

    private static double addPower(double current, double amount) {
        current = sanitizePowerAmount(current);
        amount = sanitizePowerAmount(amount);
        var result = current + amount;
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }

    @Override
    public double extractAEPower(double amt, Actionable mode, PowerMultiplier pm) {
        Preconditions.checkArgument(Double.isFinite(amt) && amt >= 0, "amt must be finite and >= 0");

        if (isCreativePowerModeActive()) {
            return amt;
        }

        final double toExtract = pm.multiply(amt);
        double extracted = 0;

        for (EnergyService service : getConnectedServices()) {
            extracted = Math.min(toExtract, extracted + service.extractProviderPower(toExtract - extracted, mode));

            if (extracted >= toExtract) {
                break;
            }
        }

        return pm.divide(extracted);
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

    /**
     * refresh current stored power.
     */
    @VisibleForTesting
    public void refreshPower() {
        this.availableTicksSinceUpdate = 0;
        this.globalAvailablePower = 0;
        for (IAEPowerStorage p : this.providers) {
            this.globalAvailablePower = addPower(this.globalAvailablePower, sanitizePowerAmount(p.getAECurrentPower()));
        }
    }

    @Override
    public double getStoredPower() {
        if (this.availableTicksSinceUpdate > 90) {
            this.refreshPower();
        }

        return Math.max(0.0, this.globalAvailablePower);
    }

    public double extractProviderPower(double amt, Actionable mode) {
        Preconditions.checkArgument(Double.isFinite(amt) && amt >= 0, "amt must be finite and >= 0");

        double extractedPower = 0;

        final Iterator<IAEPowerStorage> it = this.providers.iterator();

// when something externally
        ongoingExtractOperation = true;
        try {
            while (extractedPower < amt && it.hasNext()) {
                final IAEPowerStorage node = it.next();

                final double req = amt - extractedPower;
                final double newPower = clampPowerResult(node.extractAEPower(req, mode, PowerMultiplier.ONE), req);
                extractedPower += newPower;

                if (newPower < req && mode == Actionable.MODULATE) {
                    it.remove();
                }
            }
        } finally {
// modifies the energy grid.
            ongoingExtractOperation = false;
        }

        final double result = Math.min(extractedPower, amt);

        if (mode == Actionable.MODULATE) {
            if (extractedPower > amt) {
                this.localStorage.injectAEPower(extractedPower - amt, Actionable.MODULATE);
            }

            this.globalAvailablePower = Math.max(0.0, this.globalAvailablePower - result);
            this.tickDrainPerTick = addPower(this.tickDrainPerTick, result);
        }

        return result;
    }

    public double injectProviderPower(double amt, Actionable mode) {
        Preconditions.checkArgument(Double.isFinite(amt) && amt >= 0, "amt must be finite and >= 0");

        final double originalAmount = amt;

        var it = this.requesters.iterator();

// when something externally
        ongoingInjectOperation = true;
        try {
            while (amt > 0 && it.hasNext()) {
                final IAEPowerStorage node = it.next();
                amt = clampRemainingPower(node.injectAEPower(amt, mode), amt);

                if (amt > 0 && mode == Actionable.MODULATE) {
                    it.remove();
                }
            }
        } finally {
// modifies the energy grid.
            ongoingInjectOperation = false;
        }

        final double overflow = Math.max(0.0, amt);
        final double injected = originalAmount - overflow;

        if (mode == Actionable.MODULATE) {
            this.globalAvailablePower = addPower(this.globalAvailablePower, injected);
            this.tickInjectionPerTick = addPower(this.tickInjectionPerTick, injected);
        }

        return overflow;
    }

    public double getProviderEnergyDemand(double maxRequired) {
        Preconditions.checkArgument(Double.isFinite(maxRequired) && maxRequired >= 0,
            "maxRequired must be finite and >= 0");

        double required = 0;

        final Iterator<IAEPowerStorage> it = this.requesters.iterator();
        while (required < maxRequired && it.hasNext()) {
            final IAEPowerStorage node = it.next();
            if (node.getPowerFlow() != AccessRestriction.READ) {
                var demand = Math.max(0.0,
                    sanitizePowerAmount(node.getAEMaxPower()) - sanitizePowerAmount(node.getAECurrentPower()));
                required = addPower(required, Math.min(demand, maxRequired - required));
            }
        }

        return required;
    }

    private void addRequester(IAEPowerStorage requester) {
        Preconditions.checkState(!ongoingInjectOperation,
            "Cannot modify energy requesters while energy is being injected.");
        if (requester.getPowerFlow().isAllowInsertion()) {
            this.requesters.add(requester);
        }
    }

    private void removeRequester(IAEPowerStorage requester) {
        Preconditions.checkState(!ongoingInjectOperation,
            "Cannot modify energy requesters while energy is being injected.");
        this.requesters.remove(requester);
    }

    private void addProvider(IAEPowerStorage provider) {
        Preconditions.checkState(!ongoingExtractOperation,
            "Cannot modify energy providers while energy is being extracted.");
        if (provider.getPowerFlow().isAllowExtraction()) {
            this.providers.add(provider);
        }
    }

    private void removeProvider(IAEPowerStorage provider) {
        Preconditions.checkState(!ongoingExtractOperation,
            "Cannot modify energy providers while energy is being extracted.");
        this.providers.remove(provider);
    }

    @Override
    public double injectPower(double amt, Actionable mode) {
        Preconditions.checkArgument(Double.isFinite(amt) && amt >= 0, "amt must be finite and >= 0");

        if (isCreativePowerModeActive()) {
            return amt;
        }

        double leftover = amt;

        for (EnergyService service : getConnectedServices()) {
            leftover = service.injectProviderPower(leftover, mode);

            if (leftover <= 0) {
                break;
            }
        }

        return leftover;
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
        if (this.overlayGrid != null) {
            this.overlayGrid.invalidate();
        }
    }

    public boolean isCreativePowerModeActive() {
        return AEConfig.instance().isDebugEnergyEnabled() || getOverlayGrid().hasCreativePowerSource();
    }

    public void onCreativePowerModeChanged() {
        if (this.overlayGrid != null) {
            this.overlayGrid.setCurrentPassiveGenerator(null);
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

    private EnergyService[] getConnectedServices() {
        return getOverlayGrid().energyServices;
    }

    private EnergyOverlayGrid getOverlayGrid() {
        if (this.overlayGrid == null) {
            EnergyOverlayGrid.buildCache(this);
        }
        return Objects.requireNonNull(this.overlayGrid);
    }

    @Override
    public double getMaxStoredPower() {
        return addPower(this.providerPowerSum, this.localStorage.getAEMaxPower());
    }

    @Override
    public double getEnergyDemand(double maxRequired) {
        Preconditions.checkArgument(Double.isFinite(maxRequired) && maxRequired >= 0,
            "maxRequired must be finite and >= 0");

        if (isCreativePowerModeActive()) {
            return 0;
        }

        double required = 0;

        for (EnergyService service : getConnectedServices()) {
            required += service.getProviderEnergyDemand(maxRequired - required);

            if (required >= maxRequired) {
                break;
            }
        }

        return required;
    }

    @Override
    public void removeNode(IGridNode node) {
        localStorage.removeNode();

        var gridProvider = node.getService(IEnergyOverlayGridConnection.class);
        if (gridProvider != null) {
            this.overlayGridConnections.remove(gridProvider);
            invalidateOverlayEnergyGrid();
        }

        final GridNode gridNode = (GridNode) node;
        this.drainPerTick -= gridNode.getPreviousDraw();

        var passiveGenerator = node.getService(IPassiveEnergyGenerator.class);
        if (passiveGenerator != null) {
            passiveGenerators.remove(passiveGenerator);

            var overlayGrid = getOverlayGrid();
            if (overlayGrid.getCurrentPassiveGenerator() == passiveGenerator) {
                overlayGrid.setCurrentPassiveGenerator(null);
            }
        }

        var ps = node.getService(IAEPowerStorage.class);
        if (ps != null) {
            if (ps.isAEPublicPowerStorage()) {
                if (ps.getPowerFlow() != AccessRestriction.WRITE) {
                    this.providerPowerSum = Math.max(0.0,
                        this.providerPowerSum - sanitizePowerAmount(ps.getAEMaxPower()));
                    this.globalAvailablePower = Math.max(0.0,
                        this.globalAvailablePower - sanitizePowerAmount(ps.getAECurrentPower()));
                }

                removeProvider(ps);
                removeRequester(ps);
            }
        }

        if (node.getOwner() instanceof TileCreativeEnergyCell) {
            this.creativeEnergyCellCount--;
        }

        var watcher = this.watchers.remove(node);
        if (watcher != null) {
            watcher.reset();
        }
    }

    @Override
    public void addNode(IGridNode node, @Nullable NBTTagCompound storedData) {
        localStorage.addNode();

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

        var ps = node.getService(IAEPowerStorage.class);
        if (ps != null) {
            if (ps.isAEPublicPowerStorage()) {
                var powerFlow = ps.getPowerFlow();
                if (powerFlow.isAllowExtraction()) {
                    this.globalAvailablePower = addPower(this.globalAvailablePower,
                        sanitizePowerAmount(ps.getAECurrentPower()));
                    this.providerPowerSum = addPower(this.providerPowerSum, sanitizePowerAmount(ps.getAEMaxPower()));
                }

                addProvider(ps);
                addRequester(ps);
            }
        }

        if (node.getOwner() instanceof TileCreativeEnergyCell) {
            ++this.creativeEnergyCellCount;
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

}
