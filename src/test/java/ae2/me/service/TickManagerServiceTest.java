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

import ae2.api.networking.GridFlags;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridConnection;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeService;
import ae2.api.networking.IGridVisitor;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickSnapshot;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.stacks.AEItemKey;
import ae2.api.util.AEColor;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.WorldServer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickManagerServiceTest {

    private static void assertAwakeAndQueued(TickManagerService.NodeStatus status) {
        assertTrue(status.awake());
        assertTrue(status.queued());
        assertFalse(status.sleeping());
    }

    private static void assertSleepingAndNotQueued(TickManagerService.NodeStatus status) {
        assertTrue(status.sleeping());
        assertFalse(status.awake());
        assertFalse(status.queued());
    }

    private static void assertAbsent(TickManagerService.NodeStatus status) {
        assertFalse(status.alertable());
        assertFalse(status.sleeping());
        assertFalse(status.awake());
        assertFalse(status.queued());
    }

    @Test
    void stableTicksKeepEveryEqualDeadlineDeviceQueuedExactlyOnce() {
        var service = new TickManagerService();
        var nodes = new ArrayList<TestNode>();
        var ticks = new int[256];
        for (int i = 0; i < ticks.length; i++) {
            int index = i;
            var node = new TestNode(new IGridTickable() {
                @Override
                public TickingRequest getTickingRequest(IGridNode node) {
                    return new TickingRequest(1, 20, false, 1);
                }

                @Override
                public TickRateModulation tickingRequest(IGridNode node, int elapsed) {
                    assertEquals(1, elapsed);
                    ticks[index]++;
                    return TickRateModulation.SAME;
                }
            });
            nodes.add(node);
            service.addNode(node, null);
        }
        for (int tick = 1; tick <= 100; tick++) {
            service.onServerStartTick();
            service.onServerEndTick();
            for (int i = 0; i < nodes.size(); i++) {
                assertEquals(tick, ticks[i]);
                assertAwakeAndQueued(service.getStatus(nodes.get(i)));
            }
        }
    }

    @Test
    void migrationInsideTickDoesNotRequeueOrSleepTheOldTracker() {
        boolean wasMonitoring = TickManagerService.MONITORING_ENABLED;
        TickManagerService.MONITORING_ENABLED = true;
        try {
            for (var modulation : List.of(TickRateModulation.SLEEP, TickRateModulation.SAME)) {
                var source = new TickManagerService();
                var destination = new TickManagerService();
                var tickable = new IGridTickable() {
                    @Override
                    public TickingRequest getTickingRequest(IGridNode node) {
                        return new TickingRequest(1, 20, false, 1);
                    }

                    @Override
                    public TickRateModulation tickingRequest(IGridNode node, int elapsed) {
                        source.removeNode(node);
                        destination.addNode(node, null);
                        return modulation;
                    }
                };
                var node = new TestNode(tickable);
                source.addNode(node, null);
                source.onServerStartTick();
                source.onServerEndTick();
                assertAbsent(source.getStatus(node));
                assertAwakeAndQueued(destination.getStatus(node));
                assertEquals(0, source.getStatistics().cpuMax());
                assertEquals(0, source.getStatistics().cpuAverage());
            }
        } finally {
            TickManagerService.MONITORING_ENABLED = wasMonitoring;
        }
    }

    @Test
    void tickStatisticsRemainAccurateAfterBatchRemovalAndUpdates() {
        var snapshot = new TickSnapshot();
        var nodes = new ArrayList<TestNode>();
        for (int i = 0; i < 256; i++) {
            var node = new TestNode(null);
            nodes.add(node);
            snapshot.update(node, TickSnapshot.Category.TICK, 2, 100);
        }
        for (int i = 0; i < 255; i++) {
            snapshot.remove(nodes.get(i));
        }
        assertEquals(100, snapshot.cpuMax());
        assertEquals(2, snapshot.cpuAverage());
        snapshot.update(nodes.getLast(), TickSnapshot.Category.STORAGE, 3, 40);
        assertEquals(40, snapshot.cpuMax());
        assertEquals(0, snapshot.tick());
        assertEquals(3, snapshot.storage());
        snapshot.remove(nodes.getLast());
        assertEquals(0, snapshot.cpuMax());
        snapshot.reset();
        assertEquals(0, snapshot.cpuAverage());
    }

    @Test
    void polledSleepWakeAndGridMigrationKeepQueueMembershipConsistent() {
        var tickCount = new AtomicInteger();
        var tickable = new IGridTickable() {
            @Override
            public TickingRequest getTickingRequest(IGridNode node) {
                return new TickingRequest(1, 20, false, 1);
            }

            @Override
            public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
                tickCount.incrementAndGet();
                return TickRateModulation.SLEEP;
            }
        };
        var node = new TestNode(tickable);
        var source = new TickManagerService();

        source.addNode(node, null);
        assertAwakeAndQueued(source.getStatus(node));

        source.onServerStartTick();
        source.onServerEndTick();

        assertEquals(1, tickCount.get());
        assertSleepingAndNotQueued(source.getStatus(node));

        source.wakeDevice(node);
        assertAwakeAndQueued(source.getStatus(node));
        assertTrue(source.sleepDevice(node));
        assertSleepingAndNotQueued(source.getStatus(node));

        source.wakeDevice(node);
        source.removeNode(node);
        assertAbsent(source.getStatus(node));

        var destination = new TickManagerService();
        destination.addNode(node, null);
        assertAwakeAndQueued(destination.getStatus(node));
    }

    private record TestNode(IGridTickable tickable) implements IGridNode {

        @Override
            public <T extends IGridNodeService> T getService(Class<T> serviceClass) {
                return serviceClass == IGridTickable.class ? serviceClass.cast(this.tickable) : null;
            }

            @Override
            public Object getOwner() {
                return this;
            }

            @Override
            public void beginVisit(IGridVisitor visitor) {
            }

            @Override
            public IGrid grid() {
                throw new UnsupportedOperationException();
            }

            @Override
            public WorldServer getLevel() {
                return null;
            }

            @Override
            public Set<EnumFacing> getConnectedSides() {
                return Set.of();
            }

            @Override
            public Map<EnumFacing, IGridConnection> getInWorldConnections() {
                return Map.of();
            }

            @Override
            public List<IGridConnection> getConnections() {
                return List.of();
            }

            @Override
            public boolean hasGridBooted() {
                return true;
            }

            @Override
            public boolean isPowered() {
                return true;
            }

            @Override
            public boolean meetsChannelRequirements() {
                return true;
            }

            @Override
            public boolean hasFlag(GridFlags flag) {
                return false;
            }

            @Override
            public int getOwningPlayerId() {
                return -1;
            }

            @Override
            public UUID getOwningPlayerProfileId() {
                return null;
            }

            @Override
            public double getIdlePowerUsage() {
                return 0;
            }

            @Override
            public AEItemKey getVisualRepresentation() {
                return null;
            }

            @Override
            public AEColor getGridColor() {
                return AEColor.TRANSPARENT;
            }

            @Override
            public void fillCrashReportCategory(CrashReportCategory category) {
            }

            @Override
            public int getMaxChannels() {
                return 0;
            }

            @Override
            public int getUsedChannels() {
                return 0;
            }
        }
}
