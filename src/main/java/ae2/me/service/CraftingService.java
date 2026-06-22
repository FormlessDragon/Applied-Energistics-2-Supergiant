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
import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridServiceProvider;
import ae2.api.networking.crafting.CalculationStrategy;
import ae2.api.networking.crafting.ICraftingCPU;
import ae2.api.networking.crafting.ICraftingLink;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.networking.crafting.ICraftingRequester;
import ae2.api.networking.crafting.ICraftingService;
import ae2.api.networking.crafting.ICraftingSimulationRequester;
import ae2.api.networking.crafting.ICraftingSubmitResult;
import ae2.api.networking.crafting.ICraftingWatcherNode;
import ae2.api.networking.crafting.UnsuitableCpus;
import ae2.api.networking.energy.IEnergyService;
import ae2.api.networking.events.GridCraftingCpuChange;
import ae2.api.networking.security.IActionSource;
import ae2.api.networking.storage.IStorageService;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.AEKeyFilter;
import ae2.crafting.CraftingCalculation;
import ae2.crafting.CraftingLink;
import ae2.crafting.CraftingLinkNexus;
import ae2.crafting.execution.CraftingSubmitResult;
import ae2.hooks.ticking.TickHandler;
import ae2.me.cluster.implementations.CraftingCPUCluster;
import ae2.me.helpers.InterestManager;
import ae2.me.helpers.StackWatcher;
import ae2.me.service.helpers.CraftingServiceStorage;
import ae2.me.service.helpers.NetworkCraftingProviders;
import ae2.tile.crafting.ICraftingCPUTileEntity;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

public class CraftingService implements ICraftingService, IGridServiceProvider {
    private static final String TAG_RECURSIVE_INGREDIENT_RESERVE_AMOUNT = "recursiveIngredientReserveAmount";
    private static final long DEFAULT_RECURSIVE_INGREDIENT_RESERVE_AMOUNT = 1;

    private static final Comparator<CraftingCPUCluster> FAST_FIRST_COMPARATOR = Comparator
        .comparingInt(CraftingCPUCluster::getCoProcessors)
        .reversed()
        .thenComparingLong(CraftingCPUCluster::getAvailableStorage);

    private static final Comparator<CraftingCPUCluster> FAST_LAST_COMPARATOR = Comparator
        .comparingInt(CraftingCPUCluster::getCoProcessors)
        .thenComparingLong(CraftingCPUCluster::getAvailableStorage);

    private static final ExecutorService CRAFTING_POOL;

    static {
        final ThreadFactory factory = runnable -> {
            final Thread crafting = new Thread(runnable, "AE Crafting Calculator");
            crafting.setDaemon(true);
            return crafting;
        };

        CRAFTING_POOL = Executors.newCachedThreadPool(factory);

        GridHelper.addGridServiceEventHandler(GridCraftingCpuChange.class, ICraftingService.class,
            (service, ignoredEvent) -> ((CraftingService) service).updateList = true);
    }

    private final ObjectSet<CraftingCPUCluster> craftingCPUClusters = new ObjectOpenHashSet<>();
    private final Reference2ObjectMap<IGridNode, StackWatcher<ICraftingWatcherNode>> craftingWatchers =
        new Reference2ObjectOpenHashMap<>();
    private final IGrid grid;
    private final NetworkCraftingProviders craftingProviders = new NetworkCraftingProviders();
    private final Object2ObjectMap<UUID, CraftingLinkNexus> craftingLinks = new Object2ObjectOpenHashMap<>();
    private final Multimap<AEKey, StackWatcher<ICraftingWatcherNode>> interests = HashMultimap.create();
    private final InterestManager<StackWatcher<ICraftingWatcherNode>> interestManager = new InterestManager<>(
        this.interests);
    private final IEnergyService energyGrid;
    private final ObjectSet<AEKey> currentlyCrafting = new ObjectOpenHashSet<>();
    private final ObjectSet<AEKey> currentlyCraftable = new ObjectOpenHashSet<>();
    private long lastProcessedCraftingLogicChangeTick;
    private long lastProcessedCraftableChangeTick;
    private long recursiveIngredientReserveAmount = DEFAULT_RECURSIVE_INGREDIENT_RESERVE_AMOUNT;
    private boolean recursiveIngredientReserveAmountRestored;
    private boolean updateList = false;

    public CraftingService(IGrid grid, IStorageService storageGrid, IEnergyService energyGrid) {
        this.grid = grid;
        this.energyGrid = energyGrid;
        this.lastProcessedCraftingLogicChangeTick = TickHandler.instance().getCurrentTick();
        this.lastProcessedCraftableChangeTick = TickHandler.instance().getCurrentTick();

        storageGrid.addGlobalStorageProvider(new CraftingServiceStorage(this));
    }

    private static <T> void addSetDifference(Set<T> output, Set<T> left, Set<T> right) {
        for (var element : left) {
            if (!right.contains(element)) {
                output.add(element);
            }
        }
    }

    private static <T> Set<T> copySet(Set<T> source) {
        return source.isEmpty() ? Set.of() : new ObjectOpenHashSet<>(source);
    }

    private static int compareCandidateCpu(CraftingCPUCluster first, CraftingCPUCluster second, boolean prioritizePower,
                                           IActionSource src) {
        var firstPreferred = first.isPreferredFor(src);
        var secondPreferred = second.isPreferredFor(src);
        if (firstPreferred != secondPreferred) {
            return Boolean.compare(secondPreferred, firstPreferred);
        }

        return prioritizePower
            ? FAST_FIRST_COMPARATOR.compare(first, second)
            : FAST_LAST_COMPARATOR.compare(first, second);
    }

    @Override
    public void removeNode(IGridNode gridNode) {
        var craftingWatcher = this.craftingWatchers.remove(gridNode);
        if (craftingWatcher != null) {
            craftingWatcher.destroy();
        }

        var requester = gridNode.getService(ICraftingRequester.class);
        if (requester != null) {
            for (CraftingLinkNexus link : this.craftingLinks.values()) {
                if (link.isRequester(requester)) {
                    link.removeNode();
                }
            }
        }

        this.craftingProviders.removeProvider(gridNode);

        if (gridNode.getOwner() instanceof ICraftingCPUTileEntity) {
            this.updateList = true;
        }
    }

    @Override
    public void addNode(IGridNode gridNode, @Nullable NBTTagCompound savedData) {
        if (savedData != null) {
            this.restoreRecursiveIngredientReserveAmount(savedData);
        }

        this.craftingProviders.removeProvider(gridNode);
        this.craftingProviders.addProvider(gridNode);

        var watchingNode = gridNode.getService(ICraftingWatcherNode.class);
        if (watchingNode != null) {
            var watcher = new StackWatcher<>(this.interestManager, watchingNode);
            this.craftingWatchers.put(gridNode, watcher);
            watchingNode.updateWatcher(watcher);
        }

        var craftingRequester = gridNode.getService(ICraftingRequester.class);
        if (craftingRequester != null) {
            for (ICraftingLink link : craftingRequester.getRequestedJobs()) {
                if (link instanceof CraftingLink craftingLink) {
                    this.addLink(craftingLink);
                }
            }
        }

        if (gridNode.getOwner() instanceof ICraftingCPUTileEntity) {
            this.updateList = true;
        }
    }

    private static long clampRecursiveIngredientReserveAmount(long amount) {
        return Math.clamp(amount, 0, PatternDetailsHelper.MAX_PROCESSING_PATTERN_AMOUNT);
    }

    @Override
    public void saveNodeData(IGridNode gridNode, NBTTagCompound savedData) {
        savedData.setLong(TAG_RECURSIVE_INGREDIENT_RESERVE_AMOUNT, this.recursiveIngredientReserveAmount);
    }

    @Override
    public Set<AEKey> getCraftables(AEKeyFilter filter) {
        return this.craftingProviders.getCraftables(filter);
    }

    private void updateCPUClusters() {
        this.craftingCPUClusters.clear();

        for (var node : this.grid.getNodes()) {
            if (!(node.getOwner() instanceof ICraftingCPUTileEntity tile)) {
                continue;
            }

            final CraftingCPUCluster cluster = tile.getCluster();
            if (cluster != null) {
                this.craftingCPUClusters.add(cluster);

                ICraftingLink maybeLink = cluster.craftingLogic.getLastLink();
                if (maybeLink instanceof CraftingLink craftingLink) {
                    this.addLink(craftingLink);
                }
            }
        }
    }

    public void addLink(CraftingLink link) {
        if (link.isStandalone()) {
            return;
        }

        CraftingLinkNexus nexus = this.craftingLinks.computeIfAbsent(link.getCraftingID(), CraftingLinkNexus::new);

        link.setNexus(nexus);
    }

    public long insertIntoCpus(AEKey what, long amount, Actionable type) {
        long inserted = 0;
        for (var cpu : this.craftingCPUClusters) {
            inserted += cpu.craftingLogic.insert(what, amount - inserted, type);
        }

        return inserted;
    }

    @Override
    public Collection<IPatternDetails> getCraftingFor(AEKey whatToCraft) {
        return this.craftingProviders.getCraftingFor(whatToCraft);
    }

    @Override
    public boolean isKnownPattern(AEItemKey patternDefinition) {
        return this.craftingProviders.isKnownPattern(patternDefinition);
    }

    @Override
    public void refreshNodeCraftingProvider(IGridNode node) {
        this.craftingProviders.removeProvider(node);
        this.craftingProviders.addProvider(node);
    }

    @Override
    public void addGlobalCraftingProvider(ICraftingProvider provider) {
        this.craftingProviders.addProvider(provider);
    }

    @Override
    public void removeGlobalCraftingProvider(ICraftingProvider provider) {
        this.craftingProviders.removeProvider(provider);
    }

    @Override
    public void refreshGlobalCraftingProvider(ICraftingProvider provider) {
        this.craftingProviders.removeProvider(provider);
        this.craftingProviders.addProvider(provider);
    }

    @Nullable
    @Override
    public AEKey getFuzzyCraftable(AEKey whatToCraft, AEKeyFilter filter) {
        return this.craftingProviders.getFuzzyCraftable(whatToCraft, filter);
    }

    @Override
    public Future<ICraftingPlan> beginCraftingCalculation(World world, ICraftingSimulationRequester simRequester,
                                                          AEKey what, long amount, CalculationStrategy strategy) {
        if (world == null || simRequester == null) {
            throw new IllegalArgumentException("Invalid Crafting Job Request");
        }

        final CraftingCalculation job = new CraftingCalculation(world, this.grid, simRequester,
            new GenericStack(what, amount), strategy);

        return CRAFTING_POOL.submit(job::run);
    }

    @Override
    public ICraftingSubmitResult submitJob(ICraftingPlan job, @Nullable ICraftingRequester requestingMachine,
                                           @Nullable ICraftingCPU target, boolean prioritizePower, IActionSource src) {
        return submitJob(job, requestingMachine, target, prioritizePower, src, false);
    }

    @Override
    public ICraftingSubmitResult submitJob(ICraftingPlan job, @Nullable ICraftingRequester requestingMachine,
                                           @Nullable ICraftingCPU target, boolean prioritizePower, IActionSource src,
                                           boolean forceStart) {
        return submitJob(job, requestingMachine, target, prioritizePower, src, forceStart, false);
    }

    @Override
    public ICraftingSubmitResult submitJob(ICraftingPlan job, @Nullable ICraftingRequester requestingMachine,
                                           @Nullable ICraftingCPU target, boolean prioritizePower, IActionSource src,
                                           boolean forceStart, boolean skipMerge) {
        if (job.simulation()) {
            return CraftingSubmitResult.INCOMPLETE_PLAN;
        }
        if (!forceStart && !job.missingItems().isEmpty()) {
            return CraftingSubmitResult.INCOMPLETE_PLAN;
        }
        if (!job.missingItems().isEmpty() && job.patternTimes().isEmpty() && job.emittedItems().isEmpty()) {
            return CraftingSubmitResult.NO_CRAFTING_PATTERN;
        }

        if (requestingMachine == null && target == null && !skipMerge) {
            var mergeTarget = this.findMergeableCraftingCPU(job, src);
            if (mergeTarget != null) {
                return mergeTarget.mergeJob(this.grid, job, src);
            }
        }

        CraftingCPUCluster cpuCluster;

        if (target instanceof CraftingCPUCluster craftingCPUCluster) {
            cpuCluster = craftingCPUCluster;
        } else {
            var unsuitableCpusResult = new AtomicReference<UnsuitableCpus>();
            cpuCluster = this.findSuitableCraftingCPU(job, prioritizePower, src, unsuitableCpusResult);
            if (cpuCluster == null) {
                var unsuitableCpus = unsuitableCpusResult.get();
                if (unsuitableCpus == null) {
                    return CraftingSubmitResult.NO_CPU_FOUND;
                }
                return CraftingSubmitResult.noSuitableCpu(unsuitableCpus);
            }
        }

        return cpuCluster.submitJob(this.grid, job, src, requestingMachine);
    }

    @Override
    public boolean canMergeJob(ICraftingPlan job, IActionSource src) {
        return findMergeableCraftingCPU(job, src) != null;
    }

    @Override
    public void onServerEndTick() {
        if (this.updateList) {
            this.updateList = false;
            this.updateCPUClusters();
            this.lastProcessedCraftingLogicChangeTick = -1;
        }

        this.craftingLinks.values().removeIf(nexus -> nexus.isDead(this.grid, this));

        long latestChange = 0;
        for (var cpu : this.craftingCPUClusters) {
            cpu.craftingLogic.tickCraftingLogic(this.energyGrid, this);
            latestChange = Math.max(latestChange, cpu.craftingLogic.getLastModifiedOnTick());
        }

        if (latestChange != this.lastProcessedCraftingLogicChangeTick) {
            this.lastProcessedCraftingLogicChangeTick = latestChange;

            boolean hasInterests = !this.interests.isEmpty();
            Set<AEKey> previouslyCrafting = hasInterests
                ? copySet(this.currentlyCrafting)
                : Set.of();
            this.currentlyCrafting.clear();

            for (var cpu : this.craftingCPUClusters) {
                cpu.craftingLogic.getAllWaitingFor(this.currentlyCrafting);
            }

            if (hasInterests && !(previouslyCrafting.isEmpty() && this.currentlyCrafting.isEmpty())) {
                var changed = new ObjectOpenHashSet<AEKey>();
                addSetDifference(changed, previouslyCrafting, this.currentlyCrafting);
                addSetDifference(changed, this.currentlyCrafting, previouslyCrafting);
                for (var what : changed) {
                    for (var watcher : this.interestManager.get(what)) {
                        watcher.getHost().onRequestChange(what);
                    }
                    for (var watcher : this.interestManager.getAllStacksWatchers()) {
                        watcher.getHost().onRequestChange(what);
                    }
                }
            }
        }

        if (this.lastProcessedCraftableChangeTick != this.craftingProviders.getLastModifiedOnTick()) {
            this.lastProcessedCraftableChangeTick = this.craftingProviders.getLastModifiedOnTick();

            if (!this.currentlyCraftable.isEmpty() || !this.craftingProviders.getCraftableKeys().isEmpty()
                || !this.craftingProviders.getEmittableKeys().isEmpty()) {
                boolean hasInterests = !this.interests.isEmpty();
                Set<AEKey> previouslyCraftable = hasInterests
                    ? copySet(this.currentlyCraftable)
                    : Set.of();
                this.currentlyCraftable.clear();
                this.currentlyCraftable.addAll(this.craftingProviders.getCraftableKeys());
                this.currentlyCraftable.addAll(this.craftingProviders.getEmittableKeys());

                if (hasInterests) {
                    var changedCraftable = new ObjectOpenHashSet<AEKey>();
                    addSetDifference(changedCraftable, previouslyCraftable, this.currentlyCraftable);
                    addSetDifference(changedCraftable, this.currentlyCraftable, previouslyCraftable);
                    for (var what : changedCraftable) {
                        for (var watcher : this.interestManager.get(what)) {
                            watcher.getHost().onCraftableChange(what);
                        }
                        for (var watcher : this.interestManager.getAllStacksWatchers()) {
                            watcher.getHost().onCraftableChange(what);
                        }
                    }
                }
            }
        }
    }

    private void restoreRecursiveIngredientReserveAmount(NBTTagCompound savedData) {
        if (this.recursiveIngredientReserveAmountRestored
            || !savedData.hasKey(TAG_RECURSIVE_INGREDIENT_RESERVE_AMOUNT, Constants.NBT.TAG_LONG)) {
            return;
        }

        this.recursiveIngredientReserveAmount = clampRecursiveIngredientReserveAmount(
            savedData.getLong(TAG_RECURSIVE_INGREDIENT_RESERVE_AMOUNT));
        this.recursiveIngredientReserveAmountRestored = true;
    }

    @Override
    public ImmutableSet<ICraftingCPU> getCpus() {
        var cpus = ImmutableSet.<ICraftingCPU>builder();
        for (CraftingCPUCluster cpu : this.craftingCPUClusters) {
            if (cpu.isActive() && !cpu.isDestroyed()) {
                cpus.add(cpu);
            }
        }
        return cpus.build();
    }

    @Override
    public boolean canEmitFor(AEKey what) {
        return this.craftingProviders.canEmitFor(what);
    }

    @Override
    public boolean isRequesting(AEKey what) {
        return this.currentlyCrafting.contains(what);
    }

    @Override
    public long getRequestedAmount(AEKey what) {
        long requested = 0;

        for (CraftingCPUCluster cluster : this.craftingCPUClusters) {
            requested += cluster.craftingLogic.getWaitingFor(what);
        }

        return requested;
    }

    @Override
    public boolean isRequestingAny() {
        return !this.currentlyCrafting.isEmpty();
    }

    @Override
    public long getRecursiveIngredientReserveAmount() {
        return this.recursiveIngredientReserveAmount;
    }

    @Nullable
    private CraftingCPUCluster findSuitableCraftingCPU(ICraftingPlan job, boolean prioritizePower, IActionSource src,
                                                       AtomicReference<UnsuitableCpus> unsuitableCpus) {
        CraftingCPUCluster bestCpu = null;
        int offline = 0;
        int busy = 0;
        int tooSmall = 0;
        int excluded = 0;

        for (var cpu : this.craftingCPUClusters) {
            if (!cpu.isActive()) {
                offline++;
                continue;
            }
            if (cpu.isBusy()) {
                busy++;
                continue;
            }
            if (cpu.getAvailableStorage() < job.bytes()) {
                tooSmall++;
                continue;
            }
            if (!cpu.canBeAutoSelectedFor(src)) {
                excluded++;
                continue;
            }
            if (bestCpu == null || compareCandidateCpu(cpu, bestCpu, prioritizePower, src) < 0) {
                bestCpu = cpu;
            }
        }

        if (bestCpu == null) {
            if (offline > 0 || busy > 0 || tooSmall > 0 || excluded > 0) {
                unsuitableCpus.set(new UnsuitableCpus(offline, busy, tooSmall, excluded));
            }
            return null;
        }

        return bestCpu;
    }

    @Nullable
    private CraftingCPUCluster findMergeableCraftingCPU(ICraftingPlan job, IActionSource src) {
        if (job.simulation()) {
            return null;
        }

        CraftingCPUCluster bestCpu = null;
        for (var cpu : this.craftingCPUClusters) {
            if (!cpu.isActive() || cpu.isDestroyed() || !cpu.canBeAutoSelectedFor(src)) {
                continue;
            }
            if (!cpu.canMergeJob(job)) {
                continue;
            }
            if (bestCpu == null || compareCandidateCpu(cpu, bestCpu, true, src) < 0) {
                bestCpu = cpu;
            }
        }
        return bestCpu;
    }

    @Override
    public void setRecursiveIngredientReserveAmount(long amount) {
        this.recursiveIngredientReserveAmount = clampRecursiveIngredientReserveAmount(amount);
    }

    public Iterable<ICraftingProvider> getProviders(IPatternDetails key) {
        return this.craftingProviders.getMediums(key);
    }

    public List<ICraftingProvider> getProvidersSnapshot(IPatternDetails key) {
        return this.craftingProviders.getMediumsSnapshot(key);
    }

    public IGrid getGrid() {
        return this.grid;
    }

    public boolean hasCpu(ICraftingCPU cpu) {
        for (CraftingCPUCluster cluster : this.craftingCPUClusters) {
            if (cluster == cpu) {
                return true;
            }
        }
        return false;
    }
}
