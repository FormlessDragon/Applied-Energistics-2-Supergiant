/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2026, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ae2.me;

import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.GridFlags;
import ae2.api.networking.GridHelper;
import ae2.api.networking.GridServices;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridConnection;
import ae2.api.networking.IGridConnectionVisitor;
import ae2.api.networking.IGridMultiblock;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.IGridServiceProvider;
import ae2.api.networking.energy.IEnergyService;
import ae2.api.networking.pathing.ChannelMode;
import ae2.api.networking.pathing.ControllerState;
import ae2.api.networking.pathing.IPathingService;
import ae2.api.stacks.AEItemKey;
import ae2.helpers.patternprovider.PatternContainer;
import ae2.me.pathfinding.ChannelFinalizer;
import ae2.me.pathfinding.PathingCalculation;
import ae2.me.pathfinding.PathingCalculationTestAccess;
import ae2.me.service.ActivePatternProviderDirectory;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridPathingTest {

    private final List<GridNode> nodes = new ArrayList<>();
    private final Reference2IntOpenHashMap<IGridNode> channelNotifications = new Reference2IntOpenHashMap<>();

    @BeforeAll
    static void registerGridService() {
        GridServices.register(IPathingService.class, TestPathingService.class);
        GridServices.register(IEnergyService.class, TestEnergyService.class);
        GridServices.register(ActivePatternProviderDirectory.class, ActivePatternProviderDirectory.class);
    }

    @AfterEach
    void destroyNodes() {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            nodes.get(i).destroy();
        }
    }

    @Test
    void deletingLeavesDoesNotSearchTheSurvivingNetwork() {
        var topology = new Topology(ChannelMode.DEFAULT);
        topology.controller.markReady();
        var visits = new AtomicInteger();
        var leaves = new ArrayList<GridNode>();
        GridNode previous = topology.controller;
        for (int i = 0; i < 128; i++) {
            var cable = new SearchCountingNode(visits);
            var leaf = new SearchCountingNode(visits);
            nodes.add(cable);
            nodes.add(leaf);
            cable.markReady();
            leaf.markReady();
            topology.connect(previous, cable);
            // Exercise both endpoint orientations, including a leaf on side A.
            if ((i & 1) == 0) {
                topology.connect(leaf, cable);
            } else {
                topology.connect(cable, leaf);
            }
            previous = cable;
            leaves.add(leaf);
        }
        var grid = topology.controller.grid();
        visits.set(0);
        for (int i = 0; i < leaves.size(); i++) {
            leaves.get(i).destroy();
        }
        assertTrue(visits.get() <= 16 * leaves.size(), "Leaf removal visited " + visits.get() + " nodes");
        assertEquals(129, grid.size());
        assertSame(grid, previous.grid());
        assertSame(grid, topology.controller.grid());
    }

    @Test
    void deletingTwoConnectionBranchRootsOnlySearchesDetachedBranches() {
        var topology = new Topology(ChannelMode.DEFAULT);
        topology.controller.markReady();
        var visits = new AtomicInteger();
        var roots = new ArrayList<GridNode>();
        var leaves = new ArrayList<GridNode>();
        GridNode previous = topology.controller;
        for (int i = 0; i < 128; i++) {
            var cable = new SearchCountingNode(visits);
            var root = new SearchCountingNode(visits);
            var leaf = new SearchCountingNode(visits);
            nodes.add(cable);
            nodes.add(root);
            nodes.add(leaf);
            cable.markReady();
            root.markReady();
            leaf.markReady();
            topology.connect(previous, cable);
            topology.connect(cable, root);
            topology.connect(root, leaf);
            roots.add(root);
            leaves.add(leaf);
            previous = cable;
        }
        var retained = topology.controller.grid();
        visits.set(0);
        for (int i = 0; i < roots.size(); i++) {
            roots.get(i).destroy();
            assertEquals(1, leaves.get(i).grid().size());
            assertNotSame(retained, leaves.get(i).grid());
        }
        assertTrue(visits.get() <= 24 * roots.size(), "Branch removal visited " + visits.get() + " nodes");
        assertEquals(129, retained.size());
        assertSame(retained, previous.grid());
    }

    @Test
    void batchDisconnectCanStopAtResolvedRegionWithoutMissingDetachedComponents() {
        var topology = new Topology(ChannelMode.DEFAULT);
        topology.controller.markReady();
        var first = topology.node(new Object());
        var second = topology.node(new Object());
        var isolated = topology.node(new Object());
        first.markReady();
        second.markReady();
        isolated.markReady();
        topology.connect(topology.controller, first);
        topology.connect(topology.controller, second);
        var crossEdge = topology.connect(first, second);
        var branchEdge = topology.connect(first, isolated);
        var oldGrid = topology.controller.grid();
        GridHelper.destroyConnections(List.of(crossEdge, branchEdge));
        assertSame(oldGrid, first.grid());
        assertSame(oldGrid, second.grid());
        assertEquals(3, oldGrid.size());
        assertEquals(1, isolated.grid().size());
        assertNotSame(oldGrid, isolated.grid());
    }

    @Test
    void batchDisconnectMatchesIndependentConnectivityAcrossRandomGraphs() {
        var random = new Random(41278);
        for (int sample = 0; sample < 16; sample++) {
            var topology = new Topology(ChannelMode.DEFAULT);
            topology.controller.markReady();
            var graphNodes = new ArrayList<GridNode>();
            graphNodes.add(topology.controller);
            var edges = new ArrayList<GridConnection>();
            boolean[][] connected = new boolean[24][24];
            for (int i = 1; i < connected.length; i++) {
                var node = topology.node(new Object());
                node.markReady();
                graphNodes.add(node);
                int parent = random.nextInt(i);
                edges.add(topology.connect(graphNodes.get(parent), node));
                connected[parent][i] = connected[i][parent] = true;
            }
            for (int i = 0; i < connected.length; i++) {
                for (int j = i + 1; j < connected.length; j++) {
                    if (!connected[i][j] && random.nextInt(30) == 0) {
                        edges.add(topology.connect(graphNodes.get(i), graphNodes.get(j)));
                        connected[i][j] = connected[j][i] = true;
                    }
                }
            }
            var removed = new ArrayList<IGridConnection>();
            for (int i = 0; i < edges.size(); i++) {
                if (random.nextBoolean()) {
                    var edge = edges.get(i);
                    removed.add(edge);
                    int a = graphNodes.indexOf(edge.a());
                    int b = graphNodes.indexOf(edge.b());
                    connected[a][b] = connected[b][a] = false;
                }
            }
            var retained = topology.controller.grid();
            GridHelper.destroyConnections(removed);
            // Transitive closure is independent of AE's component search and pivot selection.
            for (int i = 0; i < connected.length; i++) {
                connected[i][i] = true;
            }
            for (int via = 0; via < connected.length; via++) {
                for (int i = 0; i < connected.length; i++) {
                    for (int j = 0; j < connected.length; j++) {
                        connected[i][j] |= connected[i][via] && connected[via][j];
                    }
                }
            }
            assertSame(retained, topology.controller.grid());
            for (int i = 0; i < connected.length; i++) {
                int componentSize = 0;
                for (int j = 0; j < connected.length; j++) {
                    assertEquals(connected[i][j], graphNodes.get(i).grid() == graphNodes.get(j).grid());
                    if (connected[i][j]) {
                        componentSize++;
                    }
                }
                assertEquals(componentSize, graphNodes.get(i).grid().size());
            }
        }
    }

    @Test
    void leafRemovalCallbackCanSplitTheSurvivingNetwork() {
        var topology = new Topology(ChannelMode.DEFAULT);
        topology.controller.markReady();
        var cable = topology.node(new Object());
        cable.markReady();
        var bridge = topology.connect(topology.controller, cable);
        var armed = new AtomicBoolean();
        var leaf = new GridNode(null, new Object(), new IGridNodeListener<>() {
            public void onSaveChanges(Object owner, IGridNode node) {
            }

            @Override
            public void onInWorldConnectionChanged(Object owner, IGridNode node) {
                if (armed.getAndSet(false)) {
                    assertFalse(cable.hasConnection(node));
                    assertFalse(((GridNode) node).hasConnection(cable));
                    bridge.destroy();
                }
            }
        }, EnumSet.noneOf(GridFlags.class));
        nodes.add(leaf);
        leaf.markReady();
        GridConnection.create(leaf, cable, EnumFacing.EAST);
        armed.set(true);
        leaf.destroy();
        assertNotSame(topology.controller.grid(), cable.grid());
        assertEquals(1, cable.grid().size());
        assertEquals(1, topology.controller.grid().size());
    }

    @Test
    void batchDisconnectKeepsPivotComponentAndSeparatesAllOtherComponents() {
        var topology = new Topology(ChannelMode.DEFAULT);
        topology.controller.markReady();
        var removed = new ArrayList<IGridConnection>();
        var leaves = new ArrayList<GridNode>();
        for (int i = 0; i < 32; i++) {
            var root = topology.node(new Object());
            var leaf = topology.node(new Object());
            root.markReady();
            leaf.markReady();
            removed.add(topology.connect(topology.controller, root));
            topology.connect(root, leaf);
            leaves.add(leaf);
        }
        var oldGrid = topology.controller.grid();
        removed.add(removed.getFirst());
        GridHelper.destroyConnections(removed);
        assertSame(oldGrid, topology.controller.grid());
        assertEquals(1, oldGrid.size());
        var components = new ReferenceOpenHashSet<IGrid>();
        for (int i = 0; i < leaves.size(); i++) {
            assertEquals(2, leaves.get(i).grid().size());
            assertTrue(components.add(leaves.get(i).grid()));
        }
        GridHelper.destroyConnections(removed);
        assertEquals(1, oldGrid.size());
    }

    @Test
    void batchDisconnectValidatesAllEntriesBeforeMutation() {
        var topology = new Topology(ChannelMode.DEFAULT);
        var leaf = topology.node(new Object());
        var connection = topology.connect(topology.controller, leaf);
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> GridHelper.destroyConnections(Arrays.asList(connection, null)));
        assertTrue(topology.controller.hasConnection(leaf));
        assertTrue(leaf.hasConnection(topology.controller));
    }

    @Test
    void disconnectCallbacksSeeBothEndpointsDetachedAndCanReconnect() {
        var topology = new Topology(ChannelMode.DEFAULT);
        topology.controller.markReady();
        var armed = new AtomicBoolean();
        var leaf = new GridNode(null, new Object(), new IGridNodeListener<>() {
            public void onSaveChanges(Object owner, IGridNode node) {
            }

            @Override
            public void onInWorldConnectionChanged(Object owner, IGridNode node) {
                if (armed.getAndSet(false)) {
                    assertFalse(topology.controller.hasConnection(node));
                    assertFalse(((GridNode) node).hasConnection(topology.controller));
                    GridConnection.create(topology.controller, node, null);
                }
            }
        }, EnumSet.noneOf(GridFlags.class));
        nodes.add(leaf);
        leaf.markReady();
        var connection = GridConnection.create(topology.controller, leaf, EnumFacing.EAST);
        armed.set(true);
        GridHelper.destroyConnections(List.of(connection));
        assertSame(topology.controller.grid(), leaf.grid());
        assertTrue(leaf.hasConnection(topology.controller));
    }

    @Test
    void assignsAndPropagatesChannelsAlongChain() {
        var topology = new Topology(ChannelMode.DEFAULT);
        var cable = topology.node(new Object(), GridFlags.PREFERRED);
        var device = topology.node(new Object(), GridFlags.REQUIRE_CHANNEL);
        var first = topology.connect(topology.controller, cable);
        var second = topology.connect(cable, device);

        var calculation = topology.calculate();

        assertEquals(1, calculation.getChannelsInUse());
        assertEquals(4, calculation.getChannelsByBlocks());
        assertEquals(1, cable.getUsedChannels());
        assertEquals(1, device.getUsedChannels());
        assertEquals(1, first.getUsedChannels());
        assertEquals(1, second.getUsedChannels());
        assertEquals(1, channelNotifications.getOrDefault(topology.controller, 0));
        assertEquals(1, channelNotifications.getOrDefault(cable, 0));
        assertEquals(1, channelNotifications.getOrDefault(device, 0));

        topology.calculate();

        assertEquals(1, channelNotifications.getOrDefault(topology.controller, 0));
        assertEquals(1, channelNotifications.getOrDefault(cable, 0));
        assertEquals(1, channelNotifications.getOrDefault(device, 0));
    }

    @Test
    void batchDisconnectRechecksComponentsAfterMigrationCallbackReconnects() {
        var topology = new Topology(ChannelMode.DEFAULT);
        topology.controller.markReady();
        var armed = new AtomicBoolean();
        var branch = new GridNode(null, new Object(), new IGridNodeListener<>() {
            public void onSaveChanges(Object owner, IGridNode node) {
            }

            @Override
            public void onGridChanged(Object owner, IGridNode node) {
                if (armed.getAndSet(false)) {
                    GridConnection.create(topology.controller, node, null);
                }
            }
        }, EnumSet.noneOf(GridFlags.class));
        nodes.add(branch);
        branch.markReady();
        var leaf = topology.node(new Object());
        leaf.markReady();
        var connection = topology.connect(topology.controller, branch);
        topology.connect(branch, leaf);
        armed.set(true);
        GridHelper.destroyConnections(List.of(connection));
        assertSame(topology.controller.grid(), branch.grid());
        assertSame(branch.grid(), leaf.grid());
        assertEquals(3, branch.grid().size());
    }

    @Test
    void updatesThirdPartyPatternContainerForEachNodeStateNotificationWithoutScanning() {
        var container = new ThirdPartyPatternContainer();
        var providerNode = trackingNode(container, true);
        var grid = Grid.create(providerNode);
        var directory = grid.getService(ActivePatternProviderDirectory.class);

        assertEquals(List.of(container), directory.getActiveProviders());
        providerNode.resetActiveQueries();

        for (var state : IGridNodeListener.State.values()) {
            providerNode.active = false;
            providerNode.notifyStatusChange(state);
            assertEquals(1, providerNode.resetActiveQueries());
            assertTrue(directory.getActiveProviders().isEmpty());
            assertEquals(0, providerNode.resetActiveQueries());

            providerNode.active = true;
            providerNode.notifyStatusChange(state);
            assertEquals(1, providerNode.resetActiveQueries());
            assertEquals(List.of(container), directory.getActiveProviders());
            assertEquals(0, providerNode.resetActiveQueries());
        }

        var unrelatedNode = trackingNode(new Object(), true);
        unrelatedNode.setGrid(grid);
        unrelatedNode.resetActiveQueries();
        providerNode.resetActiveQueries();

        for (var state : IGridNodeListener.State.values()) {
            unrelatedNode.notifyStatusChange(state);
        }
        assertEquals(List.of(container), directory.getActiveProviders());
        assertEquals(0, unrelatedNode.resetActiveQueries());
        assertEquals(0, providerNode.resetActiveQueries());
    }

    @Test
    void visitsEveryConnectionInRingExactlyOnce() {
        var topology = new Topology(ChannelMode.DEFAULT);
        var first = topology.node(new Object(), GridFlags.PREFERRED);
        var second = topology.node(new Object(), GridFlags.PREFERRED);
        topology.connect(topology.controller, first);
        topology.connect(first, second);
        topology.connect(second, topology.controller);

        var visitedNodes = new ReferenceOpenHashSet<IGridNode>();
        var visitedConnections = new ReferenceOpenHashSet<IGridConnection>();
        topology.controller.beginVisit(new IGridConnectionVisitor() {
            @Override
            public boolean visitNode(IGridNode node) {
                assertTrue(visitedNodes.add(node));
                return true;
            }

            @Override
            public void visitConnection(IGridConnection connection) {
                assertTrue(visitedConnections.add(connection));
            }
        });

        assertEquals(3, visitedNodes.size());
        assertEquals(3, visitedConnections.size());
        assertTrue(GridSplitDetector.isConnected(second, topology.controller));
    }

    @Test
    void splitsOnlyDisconnectedComponents() {
        var topology = new Topology(ChannelMode.DEFAULT);
        var first = topology.node(new Object(), GridFlags.PREFERRED);
        var second = topology.node(new Object(), GridFlags.PREFERRED);
        topology.connect(topology.controller, first);
        var bridge = topology.connect(first, second);
        topology.controller.markReady();
        first.markReady();
        second.markReady();

        assertTrue(GridSplitDetector.isConnected(second, topology.controller));
        bridge.destroy();
        assertFalse(GridSplitDetector.isConnected(second, topology.controller));

        assertSame(topology.controller.getMyGrid(), first.getMyGrid());
        assertNotSame(first.getMyGrid(), second.getMyGrid());
    }

    @Test
    void highFanoutRemovalPreservesRoutesAndPublicSnapshots() {
        var topology = new Topology(ChannelMode.INFINITE);
        var hub = topology.node(new Object(), GridFlags.PREFERRED, GridFlags.DENSE_CAPACITY);
        var edges = new ArrayList<GridConnection>();
        var leaves = new ArrayList<GridNode>();
        for (int i = 0; i < 256; i++) {
            var leaf = topology.node(new Object(), GridFlags.REQUIRE_CHANNEL);
            leaves.add(leaf);
            edges.add(topology.connect(hub, leaf));
        }
        var rootEdge = topology.connect(topology.controller, hub);
        var before = hub.getConnections();
        topology.calculate();
        assertSame(rootEdge, hub.getControllerRoute());
        assertSame(edges.getFirst(), before.getFirst());
        for (int i = 0; i < edges.size(); i += 2) {
            edges.get(i).destroy();
            assertFalse(hub.hasConnection(leaves.get(i)));
        }
        assertEquals(128, topology.calculate().getChannelsInUse());
        assertEquals(257, before.size());
        var after = hub.getConnections();
        assertEquals(129, after.size());
        assertSame(rootEdge, after.getFirst());
        for (int i = 0; i < 128; i++) {
            assertSame(edges.get(i * 2 + 1), after.get(i + 1));
        }
    }

    @Test
    void meshAssignmentIsIndependentOfConnectionInsertionOrder() {
        var first = createMesh(false);
        var second = createMesh(true);

        assertEquals(first.channelsInUse, second.channelsInUse);
        assertEquals(first.channelsByBlocks, second.channelsByBlocks);
        assertEquals(6, first.channelsInUse);
    }

    @Test
    void allocatesAcrossMultipleControllers() {
        var topology = new Topology(ChannelMode.DEFAULT);
        var otherController = topology.controller();
        var cable = topology.node(new Object(), GridFlags.PREFERRED);
        topology.connect(topology.controller, cable);
        topology.connect(otherController, cable);
        for (int i = 0; i < 8; i++) {
            topology.connect(cable, topology.node(new Object(), GridFlags.REQUIRE_CHANNEL));
        }

        var calculation = topology.calculate();

        assertEquals(8, calculation.getChannelsInUse());
        for (int i = 0, count = nodes.size(); i < count; i++) {
            var node = nodes.get(i);
            if (node.hasFlag(GridFlags.REQUIRE_CHANNEL)) {
                assertEquals(1, node.getUsedChannels());
            }
        }
    }

    @Test
    void enforcesNormalAndDenseCableCapacity() {
        var normal = createCapacityTopology(GridFlags.PREFERRED, 9);
        var dense = createCapacityTopology(GridFlags.DENSE_CAPACITY, 33);

        assertEquals(8, normal);
        assertEquals(32, dense);
    }

    @Test
    void prioritizesDenseThenNormalCableRoutesBeforeDevices() {
        var topology = new Topology(ChannelMode.DEFAULT);
        var dense = topology.node(new Object(), GridFlags.DENSE_CAPACITY);
        var normal = topology.node(new Object(), GridFlags.PREFERRED);
        var deviceBranch = topology.node(new Object());
        var denseTarget = topology.node(new Object(), GridFlags.REQUIRE_CHANNEL);
        var normalTarget = topology.node(new Object(), GridFlags.REQUIRE_CHANNEL);

        // Insert lower-priority branches first to verify queue priority, not connection insertion order.
        topology.connect(topology.controller, deviceBranch);
        topology.connect(topology.controller, normal);
        topology.connect(topology.controller, dense);
        topology.connect(deviceBranch, denseTarget);
        topology.connect(normal, denseTarget);
        var denseRoute = topology.connect(dense, denseTarget);
        topology.connect(deviceBranch, normalTarget);
        var normalRoute = topology.connect(normal, normalTarget);

        topology.calculate();

        assertSame(denseRoute, denseTarget.getControllerRoute());
        assertSame(normalRoute, normalTarget.getControllerRoute());
    }

    @Test
    void rejectsCompressedChannelAcrossIncompatibleSubtree() {
        var topology = new Topology(ChannelMode.DEFAULT);
        var cable = topology.node(new Object(), GridFlags.PREFERRED, GridFlags.CANNOT_CARRY_COMPRESSED);
        var compressedDevice = topology.node(new Object(), GridFlags.REQUIRE_CHANNEL,
            GridFlags.COMPRESSED_CHANNEL);
        topology.connect(topology.controller, cable);
        topology.connect(cable, compressedDevice);

        var calculation = topology.calculate();

        assertEquals(0, calculation.getChannelsInUse());
        assertEquals(0, compressedDevice.getUsedChannels());
    }

    @Test
    void sharesOneChannelAcrossMultiblockNodes() {
        var topology = new Topology(ChannelMode.DEFAULT);
        var cable = topology.node(new Object(), GridFlags.PREFERRED);
        var first = topology.node(new Object(), GridFlags.REQUIRE_CHANNEL, GridFlags.MULTIBLOCK);
        var second = topology.node(new Object(), GridFlags.REQUIRE_CHANNEL, GridFlags.MULTIBLOCK);
        List<IGridNode> members = List.of(first, second);
        first.addService(IGridMultiblock.class, members::iterator);
        second.addService(IGridMultiblock.class, members::iterator);
        topology.connect(topology.controller, cable);
        topology.connect(cable, first);
        topology.connect(cable, second);

        var calculation = topology.calculate();

        assertEquals(1, calculation.getChannelsInUse());
        assertEquals(1, first.getUsedChannels());
        assertEquals(1, second.getUsedChannels());
    }

    private MeshResult createMesh(boolean reverseInsertionOrder) {
        var topology = new Topology(ChannelMode.DEFAULT);
        var left = topology.node(new Object(), GridFlags.PREFERRED);
        var right = topology.node(new Object(), GridFlags.PREFERRED);
        if (reverseInsertionOrder) {
            topology.connect(topology.controller, right);
            topology.connect(topology.controller, left);
        } else {
            topology.connect(topology.controller, left);
            topology.connect(topology.controller, right);
        }
        topology.connect(left, right);
        for (int i = 0; i < 3; i++) {
            topology.connect(left, topology.node(new Object(), GridFlags.REQUIRE_CHANNEL));
            topology.connect(right, topology.node(new Object(), GridFlags.REQUIRE_CHANNEL));
        }
        var calculation = topology.calculate();
        return new MeshResult(calculation.getChannelsInUse(), calculation.getChannelsByBlocks());
    }

    private int createCapacityTopology(GridFlags cableFlag, int deviceCount) {
        var topology = new Topology(ChannelMode.DEFAULT);
        var cable = topology.node(new Object(), cableFlag);
        topology.connect(topology.controller, cable);
        for (int i = 0; i < deviceCount; i++) {
            topology.connect(cable, topology.node(new Object(), GridFlags.REQUIRE_CHANNEL));
        }
        return topology.calculate().getChannelsInUse();
    }

    private TrackingGridNode trackingNode(Object owner, boolean active) {
        var node = new TrackingGridNode(owner, active);
        nodes.add(node);
        return node;
    }

    private static final class SearchCountingNode extends GridNode {
        private final AtomicInteger visits;

        private SearchCountingNode(AtomicInteger visits) {
            super(null, new Object(), (owner, node) -> {
            }, EnumSet.noneOf(GridFlags.class));
            this.visits = visits;
        }

        @Override
        Grid getMyGrid() {
            this.visits.incrementAndGet();
            return super.getMyGrid();
        }

        @Override
        boolean markVisited(Object marker) {
            this.visits.incrementAndGet();
            return super.markVisited(marker);
        }
    }

    private static final class TrackingGridNode extends GridNode {
        private boolean active;
        private int activeQueries;

        private TrackingGridNode(Object owner, boolean active) {
            super(null, owner, (nodeOwner, node) -> {
            }, EnumSet.noneOf(GridFlags.class));
            this.active = active;
        }

        @Override
        public boolean isOnline() {
            activeQueries++;
            return active;
        }

        private int resetActiveQueries() {
            int result = activeQueries;
            activeQueries = 0;
            return result;
        }
    }

    private static final class ThirdPartyPatternContainer implements PatternContainer {
        @Override
        public IGrid getGrid() {
            return null;
        }

        @Override
        public InternalInventory getTerminalPatternInventory() {
            return InternalInventory.empty();
        }

        @Override
        public boolean containsPattern(AEItemKey pattern) {
            return false;
        }

        @Override
        public PatternContainerGroup getTerminalGroup() {
            return PatternContainerGroup.nothing();
        }
    }

    private record MeshResult(int channelsInUse, int channelsByBlocks) {
    }

    @SuppressWarnings("ClassCanBeRecord")
    public static final class TestPathingService implements IPathingService, IGridServiceProvider {
        private static ChannelMode nextMode = ChannelMode.DEFAULT;
        private final ChannelMode channelMode;

        public TestPathingService(IGrid grid) {
            this.channelMode = nextMode;
        }

        @Override
        public ChannelMode channelMode() {
            return channelMode;
        }

        @Override
        public boolean isNetworkBooting() {
            return false;
        }

        @Override
        public ControllerState getControllerState() {
            return ControllerState.CONTROLLER_ONLINE;
        }

        @Override
        public void repath() {
        }

        @Override
        public int getUsedChannels() {
            return 0;
        }
    }

    public static final class TestEnergyService implements IEnergyService, IGridServiceProvider {
        public TestEnergyService(IGrid grid) {
        }

        @Override
        public double getIdlePowerUsage() {
            return 0;
        }

        @Override
        public double getChannelPowerUsage() {
            return 0;
        }

        @Override
        public double getAvgPowerUsage() {
            return 0;
        }

        @Override
        public double getAvgPowerInjection() {
            return 0;
        }

        @Override
        public boolean isNetworkPowered() {
            return true;
        }

        @Override
        public double injectPower(double amount, Actionable mode) {
            return amount;
        }

        @Override
        public double getStoredPower() {
            return 0;
        }

        @Override
        public double getMaxStoredPower() {
            return 0;
        }

        @Override
        public double getEnergyDemand(double maxRequired) {
            return 0;
        }

        @Override
        public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
            return 0;
        }
    }

    private final class Topology {
        private final List<GridNode> controllers = new ArrayList<>();
        private final GridNode controller;
        private final Grid grid;
        private final PathingCalculation calculation;

        private Topology(ChannelMode channelMode) {
            TestPathingService.nextMode = channelMode;
            this.controller = controller();
            this.grid = Grid.create(controller);
            this.calculation = new PathingCalculation(grid);
        }

        private GridNode controller() {
            var node = node(new Object(), GridFlags.CANNOT_CARRY, GridFlags.DENSE_CAPACITY);
            controllers.add(node);
            return node;
        }

        private GridNode node(Object owner, GridFlags... flags) {
            var flagSet = EnumSet.noneOf(GridFlags.class);
            Collections.addAll(flagSet, flags);
            var node = new GridNode(null, owner, new IGridNodeListener<>() {
                @Override
                public void onSaveChanges(Object nodeOwner, IGridNode gridNode) {
                }

                @Override
                public void onStateChanged(Object nodeOwner, IGridNode gridNode, State state) {
                    if (state == State.CHANNEL) {
                        channelNotifications.addTo(gridNode, 1);
                    }
                }
            }, flagSet);
            nodes.add(node);
            return node;
        }

        private GridConnection connect(GridNode first, GridNode second) {
            return GridConnection.create(first, second, null);
        }

        private PathingCalculation calculate() {
            PathingCalculationTestAccess.compute(calculation, controllers);
            var finalizer = new ChannelFinalizer(grid);
            calculation.finalizeChannels(finalizer);
            finalizer.notifyChangedNodes();
            return calculation;
        }
    }
}
