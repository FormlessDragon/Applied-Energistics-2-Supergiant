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

package ae2.hooks.ticking;

import ae2.container.PendingGridFills;
import ae2.core.AEConfig;
import ae2.core.AELog;
import ae2.crafting.CraftingCalculation;
import ae2.me.Grid;
import ae2.me.GridNode;
import ae2.server.services.compass.ServerCompassService;
import ae2.util.ChunkPosUtils;
import ae2.util.ILevelRunnable;
import ae2.util.Platform;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.crash.CrashReport;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class TickHandler {
    private static final int TIME_LIMIT_PROCESS_QUEUE_MILLISECONDS = 25;
    private static final TickHandler INSTANCE = new TickHandler();
    private final Queue<ILevelRunnable> serverQueue = new ConcurrentLinkedQueue<>();
    private final Object queueSizeLock = new Object();
    private final Reference2IntOpenHashMap<Queue<ILevelRunnable>> queueSizes = new Reference2IntOpenHashMap<>();
    private final CraftingJobRegistry<CraftingCalculation> craftingJobs = new CraftingJobRegistry<>();
    private final Map<World, Queue<ILevelRunnable>> callQueue = new Reference2ObjectOpenHashMap<>();
    private final Set<World> closedLevels = Collections.newSetFromMap(new IdentityHashMap<>());
    private final ServerBlockEntityRepo blockEntities = new ServerBlockEntityRepo();
    private final ServerGridRepo grids = new ServerGridRepo();
    private final Stopwatch stopWatch;
    private boolean processingQueue;
    private World processingLevel;
    private long queueGeneration;
    private int processQueueElementsProcessed;
    private int processQueueElementsRemaining;
    private long tickCounter;

    private TickHandler() {
        this(Ticker.systemTicker());
    }

    TickHandler(Ticker ticker) {
        this.stopWatch = Stopwatch.createUnstarted(ticker);
        queueSizes.defaultReturnValue(Integer.MIN_VALUE);
    }

    public static TickHandler instance() {
        return INSTANCE;
    }

    public void init() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    public void addNetwork(Grid grid) {
        this.grids.addNetwork(grid);
    }

    public void removeNetwork(Grid grid) {
        this.grids.removeNetwork(grid);
    }

    public Collection<Grid> getGridList() {
        return Collections.unmodifiableSet(this.grids.getNetworks());
    }

    public void addCallable(World world, Runnable callable) {
        tryAddCallable(world, ignored -> callable.run());
    }

    public boolean tryAddCallable(World world, Runnable callable) {
        return tryAddCallable(world, ignored -> callable.run());
    }

    public void addCallable(World level, ILevelRunnable callable) {
        tryAddCallable(level, callable);
    }

    /**
     * Queues a callback when its owner is still accepting work.
     *
     * @return false when a world callback targets an unloaded world
     */
    public boolean tryAddCallable(World level, ILevelRunnable callable) {
        Preconditions.checkArgument(level == null || !level.isRemote, "Can only register server-side callbacks");

        if (level == null) {
            enqueue(this.serverQueue, callable);
            return true;
        } else {
            synchronized (this.callQueue) {
                if (this.closedLevels.contains(level)) {
                    return false;
                }
                Queue<ILevelRunnable> queue = this.callQueue.computeIfAbsent(level, ignored -> newQueue());
                enqueue(queue, callable);
                return true;
            }
        }
    }

    public <T extends TileEntity> void addInit(T tile, Consumer<? super T> callback) {
        if (tile.getWorld() == null || tile.getWorld().isRemote) {
            return;
        }
        this.blockEntities.addBlockEntity(tile, callback);
    }

    public void shutdown() {
        this.queueGeneration++;
        this.blockEntities.clear();
        this.grids.clear();
        synchronized (this.callQueue) {
            this.callQueue.clear();
            this.closedLevels.clear();
        }
        synchronized (this.queueSizeLock) {
            this.serverQueue.clear();
            this.queueSizes.clear();
        }
        for (var job : this.craftingJobs.shutdown()) {
            job.cancel();
        }
    }

    @SubscribeEvent
    public void onUnloadChunk(ChunkEvent.Unload event) {
        World level = event.getWorld();
        if (level == null || level.isRemote) {
            return;
        }
        var chunkPos = event.getChunk().getPos();
        this.blockEntities.removeChunk(level, ChunkPosUtils.asLong(chunkPos.x, chunkPos.z));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onUnloadWorld(WorldEvent.Unload event) {
        World level = event.getWorld();
        if (level == null || level.isRemote) {
            return;
        }

        PendingGridFills.clearWorld(level);

        if (this.processingQueue && this.processingLevel == level) {
            this.queueGeneration++;
        }
        for (var job : this.craftingJobs.closeWorld(level)) {
            job.cancel();
        }

        var toDestroy = new ObjectArrayList<GridNode>();

        this.grids.updateNetworks();
        for (Grid grid : this.grids.getNetworks()) {
            for (var node : grid.getNodes()) {
                if (node instanceof GridNode gridNode && gridNode.getLevel() == level) {
                    toDestroy.add(gridNode);
                }
            }
        }

        for (var node : toDestroy) {
            node.destroy();
        }

        this.blockEntities.removeLevel(level);
        synchronized (this.callQueue) {
            this.closedLevels.add(level);
            discardQueue(this.callQueue.remove(level));
        }
        if (level instanceof WorldServer serverLevel) {
            ServerCompassService.clearCache(serverLevel);
        }
    }

    @SubscribeEvent
    public void onServerStartTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || this.processingQueue) {
            return;
        }

        this.processQueueElementsProcessed = 0;
        this.processQueueElementsRemaining = 0;
        this.stopWatch.reset();
        this.craftingJobs.openSessionIfClosed();

        this.grids.updateNetworks();
        for (var grid : this.grids.getNetworks()) {
            try {
                grid.onServerStartTick();
            } catch (Throwable t) {
                CrashReport crashReport = CrashReport.makeCrashReport(t, "Ticking grid on start of server tick");
                grid.fillCrashReportCategory(crashReport.makeCategory("Grid being ticked"));
                throw new ReportedException(crashReport);
            }
        }
    }

    @SubscribeEvent
    public void onWorldStartTick(TickEvent.WorldTickEvent event) {
        World level = event.world;
        if (level == null || level.isRemote || event.phase != TickEvent.Phase.START || this.processingQueue) {
            return;
        }

        Queue<ILevelRunnable> queue;
        synchronized (this.callQueue) {
            queue = this.callQueue.remove(level);
        }
        long generation = this.queueGeneration;
        this.processQueueElementsRemaining += this.processQueue(queue, level);
        if (generation == this.queueGeneration && queue != null && !queue.isEmpty()) {
            synchronized (this.callQueue) {
                Queue<ILevelRunnable> newQueue = this.callQueue.put(level, queue);
                if (newQueue != null) {
                    appendQueue(queue, newQueue);
                }
            }
        } else if (queue != null) {
            discardQueue(queue);
        }

        this.grids.updateNetworks();
        for (var grid : this.grids.getNetworks()) {
            try {
                grid.onWorldStartTick(level);
            } catch (Throwable t) {
                CrashReport crashReport = CrashReport.makeCrashReport(t, "Ticking grid on start of world tick");
                grid.fillCrashReportCategory(crashReport.makeCategory("Grid being ticked"));
                level.addWorldInfoToCrashReport(crashReport);
                throw new ReportedException(crashReport);
            }
        }
    }

    @SubscribeEvent
    public void onWorldEndTick(TickEvent.WorldTickEvent event) {
        World level = event.world;
        if (level == null || level.isRemote || event.phase != TickEvent.Phase.END) {
            return;
        }

        this.simulateCraftingJobs(level);
        this.readyBlockEntities(level);

        for (var grid : this.grids.getNetworks()) {
            try {
                grid.onWorldEndTick(level);
            } catch (Throwable t) {
                CrashReport crashReport = CrashReport.makeCrashReport(t, "Ticking grid on end of world tick");
                grid.fillCrashReportCategory(crashReport.makeCategory("Grid being ticked"));
                level.addWorldInfoToCrashReport(crashReport);
                throw new ReportedException(crashReport);
            }
        }
    }

    @SubscribeEvent
    public void onServerEndTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.processingQueue) {
            return;
        }

        for (var grid : this.grids.getNetworks()) {
            try {
                grid.onServerEndTick();
            } catch (Throwable t) {
                CrashReport crashReport = CrashReport.makeCrashReport(t, "Ticking grid on end of server tick");
                grid.fillCrashReportCategory(crashReport.makeCategory("Grid being ticked"));
                throw new ReportedException(crashReport);
            }
        }

        this.processQueueElementsRemaining += this.processQueue(this.serverQueue, null);

        if (this.stopWatch.elapsed(TimeUnit.MILLISECONDS) > TIME_LIMIT_PROCESS_QUEUE_MILLISECONDS) {
            AELog.warn("Exceeded time limit of %d ms after processing %d queued tick callbacks (%d remain)",
                TIME_LIMIT_PROCESS_QUEUE_MILLISECONDS, this.processQueueElementsProcessed,
                this.processQueueElementsRemaining);
        }

        tickCounter++;
    }

    public long getCurrentTick() {
        return tickCounter;
    }

    public List<ITextComponent> getBlockEntityReport() {
        return this.blockEntities.getReport();
    }

    public long getCraftingSessionEpoch() {
        return this.craftingJobs.getSessionEpoch();
    }

    public void registerCraftingSimulation(World world, CraftingCalculation craftingCalculation) {
        registerCraftingSimulation(world, craftingCalculation, this.getCraftingSessionEpoch());
    }

    public boolean registerCraftingSimulation(World world, CraftingCalculation craftingCalculation, long sessionEpoch) {
        Preconditions.checkArgument(!world.isRemote, "Trying to register a crafting job for a client-level");

        if (!this.craftingJobs.register(world, craftingCalculation, sessionEpoch)) {
            craftingCalculation.cancel();
            return false;
        }
        return true;
    }

    public void unregisterCraftingSimulation(World world, CraftingCalculation craftingCalculation) {
        this.craftingJobs.unregister(world, craftingCalculation);
    }

    private int processQueue(Queue<ILevelRunnable> queue, World level) {
        if (queue == null || this.processingQueue) {
            return 0;
        }

        long generation = this.queueGeneration;
        this.processingQueue = true;
        this.processingLevel = level;
        this.stopWatch.start();
        try {
            while (generation == this.queueGeneration
                && this.stopWatch.elapsed(TimeUnit.MILLISECONDS) < TIME_LIMIT_PROCESS_QUEUE_MILLISECONDS) {
                ILevelRunnable runnable = poll(queue);
                if (runnable == null) {
                    break;
                }
                try {
                    runnable.call(level);
                } catch (Exception exception) {
                    AELog.warn(exception);
                } finally {
                    // An attempted callback consumes time even if it throws or reenters the handler.
                    this.processQueueElementsProcessed++;
                }
            }
        } finally {
            this.stopWatch.stop();
            this.processingQueue = false;
            this.processingLevel = null;
        }

        return queuedCount(queue);
    }

    private Queue<ILevelRunnable> newQueue() {
        var queue = new ArrayDeque<ILevelRunnable>();
        synchronized (this.queueSizeLock) {
            this.queueSizes.put(queue, 0);
        }
        return queue;
    }

    private void enqueue(Queue<ILevelRunnable> queue, ILevelRunnable runnable) {
        synchronized (this.queueSizeLock) {
            queue.add(runnable);
            this.queueSizes.addTo(queue, 1);
        }
    }

    @Nullable
    private ILevelRunnable poll(Queue<ILevelRunnable> queue) {
        synchronized (this.queueSizeLock) {
            var runnable = queue.poll();
            if (runnable != null) {
                int count = this.queueSizes.getInt(queue);
                if (count > 1) {
                    this.queueSizes.addTo(queue, -1);
                } else if (count != Integer.MIN_VALUE) {
                    this.queueSizes.put(queue, 0);
                }
            }
            return runnable;
        }
    }

    private int queuedCount(Queue<ILevelRunnable> queue) {
        synchronized (this.queueSizeLock) {
            var count = this.queueSizes.getInt(queue);
            return count != Integer.MIN_VALUE ? count : queue.size();
        }
    }

    private void appendQueue(Queue<ILevelRunnable> target, Queue<ILevelRunnable> source) {
        synchronized (this.queueSizeLock) {
            int sourceCount = this.queueSizes.getInt(source);
            if (sourceCount == Integer.MIN_VALUE) {
                sourceCount = source.size();
            }
            target.addAll(source);
            int targetCount = this.queueSizes.getInt(target);
            if (targetCount == Integer.MIN_VALUE) {
                this.queueSizes.put(target, sourceCount);
            } else {
                this.queueSizes.addTo(target, sourceCount);
            }
            source.clear();
            this.queueSizes.removeInt(source);
        }
    }

    private void discardQueue(@Nullable Queue<ILevelRunnable> queue) {
        if (queue == null) {
            return;
        }
        synchronized (this.queueSizeLock) {
            queue.clear();
            this.queueSizes.removeInt(queue);
        }
    }

    private void simulateCraftingJobs(World world) {
        final Collection<CraftingCalculation> jobSet = this.craftingJobs.snapshot(world);

        if (!jobSet.isEmpty()) {
            final int jobSize = jobSet.size();
            final int microSecondsPerTick = AEConfig.instance().getCraftingCalculationTimePerTick() * 1000;
            final int simTime = Math.max(1, microSecondsPerTick / jobSize);

            for (var job : jobSet) {
                if (!job.simulateFor(simTime)) {
                    this.craftingJobs.unregister(world, job);
                }
            }
        }
    }

    static final class CraftingJobRegistry<T> {
        private final Multimap<World, T> jobs = LinkedListMultimap.create();
        private final Set<World> closedWorlds = Collections.newSetFromMap(new IdentityHashMap<>());
        private boolean accepting = true;
        private long sessionEpoch;

        synchronized long getSessionEpoch() {
            return this.sessionEpoch;
        }

        synchronized void openSessionIfClosed() {
            if (!this.accepting) {
                this.accepting = true;
                this.sessionEpoch++;
                this.closedWorlds.clear();
            }
        }

        synchronized boolean register(World world, T job, long sessionEpoch) {
            if (!this.accepting || sessionEpoch != this.sessionEpoch || this.closedWorlds.contains(world)) {
                return false;
            }
            this.jobs.put(world, job);
            return true;
        }

        synchronized void unregister(World world, T job) {
            this.jobs.remove(world, job);
        }

        synchronized Collection<T> snapshot(World world) {
            return List.copyOf(this.jobs.get(world));
        }

        synchronized Collection<T> closeWorld(World world) {
            this.closedWorlds.add(world);
            var jobs = List.copyOf(this.jobs.get(world));
            this.jobs.removeAll(world);
            return jobs;
        }

        synchronized Collection<T> shutdown() {
            this.accepting = false;
            this.sessionEpoch++;
            var jobs = List.copyOf(this.jobs.values());
            this.jobs.clear();
            this.closedWorlds.clear();
            return jobs;
        }
    }

    private void readyBlockEntities(World world) {
        long[] workSet = this.blockEntities.getQueuedChunks(world);
        for (long packedChunkPos : workSet) {
            BlockPos checkPos = new BlockPos((ChunkPosUtils.getX(packedChunkPos) << 4), 0, (ChunkPosUtils.getZ(packedChunkPos) << 4));
            if (Platform.areBlockEntitiesTicking(world, checkPos)) {
                var chunkQueue = this.blockEntities.removeChunk(world, packedChunkPos);
                if (chunkQueue == null) {
                    AELog.warn("Chunk %s was unloaded while we were readying block entities",
                        new ChunkPos(ChunkPosUtils.getX(packedChunkPos), ChunkPosUtils.getZ(packedChunkPos)));
                    continue;
                }

                for (var info : chunkQueue) {
                    if (!info.blockEntity().isInvalid()) {
                        try {
                            info.callInit();
                        } catch (Throwable t) {
                            CrashReport crashReport = CrashReport.makeCrashReport(t, "Readying AE2 tile entity");
                            var category = crashReport.makeCategory("Tile entity being readied");
                            world.addWorldInfoToCrashReport(crashReport);
                            info.blockEntity().addInfoToCrashReport(category);
                            throw new ReportedException(crashReport);
                        }
                    }
                }
            }
        }
    }
}
