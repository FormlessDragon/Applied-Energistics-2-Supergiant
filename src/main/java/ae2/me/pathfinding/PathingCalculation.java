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

package ae2.me.pathfinding;

import ae2.api.networking.GridFlags;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridMultiblock;
import ae2.api.networking.IGridNode;
import ae2.me.GridConnection;
import ae2.me.GridNode;
import ae2.tile.networking.TileController;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Calculation to assign channels starting from the controllers. The full computation is split in two steps, each linear
 * time.
 * <p>
 * First, a BFS is performed starting from the controllers. This establishes a tree that connects all path items to a
 * controller. As nodes that require channels are visited, they are assigned a channel if possible. This is done by
 * checking the channel count of a few key nodes (max 3) along the path.
 * <p>
 * Second, the BFS visitation order is traversed backwards to propagate the channel count upwards.
 */
public class PathingCalculation {
    private static final Logger LOG = LoggerFactory.getLogger(PathingCalculation.class);
    private final IGrid grid;
    /**
     * Path items that are part of a multiblock that was already granted a channel.
     */
    private final ReferenceSet<GridNode> multiblocksWithChannel = new ReferenceOpenHashSet<>();
    /**
     * The BFS queues: all the path items that need to be visited on the next tick. Dense queue is prioritized to have
     * the behavior of dense cables extending the controller faces, then cables, then normal devices.
     */
    private final Queue<IPathItem>[] queues = createQueues();
    /**
     * Path items that are either in a queue, or have been processed already.
     */
    private final ReferenceSet<IPathItem> visited = new ReferenceOpenHashSet<>();
    /**
     * Controllers in the order exposed by the grid. This order affects which equally short route wins.
     */
    private final ObjectArrayList<GridNode> controllerNodes = new ObjectArrayList<>();
    /**
     * Parent-before-child order produced by the BFS. Reversing it is a post-order traversal of the selected path tree.
     */
    private final ObjectArrayList<IPathItem> visitationOrder = new ObjectArrayList<>();
    /**
     * Every path item whose computed channel state must be committed after the grid finishes booting.
     */
    private final ObjectArrayList<IPathItem> finalizationOrder = new ObjectArrayList<>();
    /**
     * Tracks the number of channels assigned to each path item during the BFS pass. Only a few key nodes along any path
     * are checked and updated.
     */
    private final Reference2IntOpenHashMap<GridNode> channelBottlenecks = new Reference2IntOpenHashMap<>();
    /**
     * Nodes that have been granted a channel during the BFS pass.
     */
    private final ReferenceSet<GridNode> channelNodes = new ReferenceOpenHashSet<>();
    /**
     * Tracks the total number of used channels.
     */
    private int channelsInUse = 0;
    /**
     * Tracks the total number of channels for each path item is using.
     */
    private int channelsByBlocks = 0;
    private boolean awaitingFinalization;

    /**
     * Create a new pathing calculation from the passed grid.
     */
    public PathingCalculation(IGrid grid) {
        this.grid = grid;
    }

    public void compute() {
        compute(grid.getMachineNodes(TileController.class));
    }

    void compute(Iterable<? extends IGridNode> controllers) {
        if (awaitingFinalization) {
            throw new IllegalStateException("Previous pathing calculation has not been finalized");
        }

        channelsInUse = 0;
        channelsByBlocks = 0;

        try {
            initializeControllers(controllers);

            // BFS pass
            for (int i = 0; i < queues.length; ++i) {
                processQueue(queues[i], i);
            }

            // Reverse BFS pass
            propagateAssignments();
            awaitingFinalization = true;
        } catch (RuntimeException | Error e) {
            clearWorkspace();
            throw e;
        }
    }

    private void initializeControllers(Iterable<? extends IGridNode> controllers) {
        // Add every outgoing connection of the controllers (that doesn't point to another controller) to the list.
        for (var node : controllers) {
            var controller = (GridNode) node;
            controller.prepareControllerRouteRoot();
            if (visited.add(controller)) {
                controllerNodes.add(controller);
                finalizationOrder.add(controller);
            }
        }

        for (int controllerIndex = 0, controllerCount = controllerNodes.size();
             controllerIndex < controllerCount;
             controllerIndex++) {
            var controller = controllerNodes.get(controllerIndex);
            for (int i = 0, size = controller.getPossibleOptionCount(); i < size; i++) {
                var gc = (GridConnection) controller.getPossibleOption(i);
                if (visited.add(gc)) {
                    finalizationOrder.add(gc);
                    if (!visited.contains(gc.getOtherSide(controller))) {
                        enqueue(gc, 0);
                        gc.setControllerRoute(controller);
                    } else {
                        gc.setAdHocChannels(0);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Queue<IPathItem>[] createQueues() {
        return (Queue<IPathItem>[]) new Queue<?>[]{
            new ArrayDeque<IPathItem>(), // 0: dense cable queue
            new ArrayDeque<IPathItem>(), // 1: normal cable queue
            new ArrayDeque<IPathItem>() // 2: non-cable queue
        };
    }

    private void enqueue(IPathItem pathItem, int queueIndex) {
        int possibleIndex;

        if (pathItem instanceof GridConnection) {
            // Grid connection does not have flags, allow any queue.
            possibleIndex = 0;
        } else if (pathItem.hasFlag(GridFlags.DENSE_CAPACITY)) {
            // Dense queue if possible.
            possibleIndex = 0;
        } else if (pathItem.hasFlag(GridFlags.PREFERRED)) {
            // Cable queue if possible.
            possibleIndex = 1;
        } else {
            possibleIndex = 2;
        }

        int index = Math.max(possibleIndex, queueIndex);
        queues[index].add(pathItem);
    }

    private void processQueue(Queue<IPathItem> oldOpen, int queueIndex) {
        while (!oldOpen.isEmpty()) {
            IPathItem i = oldOpen.poll();
            visitationOrder.add(i);
            for (int optionIndex = 0, size = i.getPossibleOptionCount(); optionIndex < size; optionIndex++) {
                IPathItem pi = i.getPossibleOption(optionIndex);
                if (this.visited.add(pi)) {
                    // Set BFS parent.
                    pi.setControllerRoute(i);

                    if (pi.hasFlag(GridFlags.REQUIRE_CHANNEL)) {
                        if (!(pi instanceof GridNode gridNode)) {
                            continue;
                        }
                        if (!this.multiblocksWithChannel.contains(gridNode)) {
                            // Try to use the channel along the path.
                            boolean worked = tryUseChannel(gridNode);

                            if (worked && pi.hasFlag(GridFlags.MULTIBLOCK)) {
                                var multiblock = ((IGridNode) pi).getService(IGridMultiblock.class);
                                if (multiblock != null) {
                                    var oni = multiblock.getMultiblockNodes();
                                    while (oni.hasNext()) {
                                        final IGridNode otherNodes = oni.next();
                                        if (otherNodes == null) {
                                            // Only a log for now until addons are fixed too. See
                                            // https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/8295
                                            LOG.error("Skipping null node returned by grid multiblock node {}",
                                                multiblock);
                                        } else if (otherNodes != pi) {
                                            this.multiblocksWithChannel.add((GridNode) otherNodes);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    finalizationOrder.add(pi);
                    enqueue(pi, queueIndex);
                }
            }
        }
    }

    /**
     * Try to allocate a channel along the path from {@code start} to the controller.
     *
     * @return true if allocation was successful
     */
    private boolean tryUseChannel(GridNode start) {
        if (start.hasFlag(GridFlags.COMPRESSED_CHANNEL) && !start.getSubtreeAllowsCompressedChannels()) {
            // Don't send a compressed channel through this item.
            return false;
        }

        // Check that the allocation is possible.
        GridNode pi = start;
        while (pi != null) {
            if (channelBottlenecks.getOrDefault(pi, 0) >= pi.getMaxChannels()) {
                return false;
            }

            pi = pi.getHighestSimilarAncestor();
        }

        // Allocate the channel along the path.
        pi = start;
        while (pi != null) {
            channelBottlenecks.addTo(pi, 1);
            pi = pi.getHighestSimilarAncestor();
        }

        channelNodes.add(start);
        return true;
    }

    /**
     * Propagates assignments in reverse BFS order, which is post-order for the selected controller-route tree.
     */
    private void propagateAssignments() {
        for (int i = visitationOrder.size() - 1; i >= 0; i--) {
            IPathItem item = visitationOrder.get(i);
            if (item instanceof GridNode node) {
                boolean hasChannel = channelNodes.contains(item);
                channelsByBlocks += node.propagateChannelsUpwards(hasChannel);
                if (hasChannel) {
                    channelsInUse++;
                }
            } else {
                channelsByBlocks += ((GridConnection) item).propagateChannelsUpwards();
            }
        }

        // Give a channel to all nodes that are a part of a multiblock that was given a channel before.
        for (var multiblockNode : multiblocksWithChannel) {
            multiblockNode.incrementChannelCount(1);
        }
    }

    /**
     * Commits all path-item state without traversing the graph again.
     */
    public void finalizeChannels(ChannelFinalizer finalizer) {
        if (!awaitingFinalization) {
            throw new IllegalStateException("Pathing calculation has not completed");
        }

        try {
            for (int i = 0, size = finalizationOrder.size(); i < size; i++) {
                finalizer.finalizeItem(finalizationOrder.get(i));
            }
        } finally {
            clearWorkspace();
        }
    }

    private void clearWorkspace() {
        for (Queue<IPathItem> queue : queues) {
            queue.clear();
        }
        multiblocksWithChannel.clear();
        visited.clear();
        controllerNodes.clear();
        visitationOrder.clear();
        finalizationOrder.clear();
        channelBottlenecks.clear();
        channelNodes.clear();
        awaitingFinalization = false;
    }

    public int getChannelsInUse() {
        return channelsInUse;
    }

    public int getChannelsByBlocks() {
        return channelsByBlocks;
    }
}
