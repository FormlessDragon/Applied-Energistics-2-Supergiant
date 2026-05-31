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

package ae2.me.cluster.implementations;

import ae2.api.features.Locatables;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IGridConnection;
import ae2.api.networking.IGridNode;
import ae2.api.networking.security.IActionHost;
import ae2.core.AELog;
import ae2.me.cluster.IAECluster;
import ae2.me.cluster.MBCalculator;
import ae2.me.service.helpers.ConnectionWrapper;
import ae2.tile.qnb.TileQuantumBridge;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

public class QuantumCluster implements IAECluster, IActionHost {

    private final BlockPos boundsMin;
    private final BlockPos boundsMax;
    private boolean destroyed;
    private boolean updateStatus = true;
    private TileQuantumBridge[] ring = new TileQuantumBridge[8];
    private boolean registered;
    private ConnectionWrapper connection;
    private long thisSide;
    private long otherSide;
    private TileQuantumBridge center;

    public QuantumCluster(BlockPos min, BlockPos max) {
        this.boundsMin = min;
        this.boundsMax = max;
    }

    @Override
    public BlockPos getBoundsMin() {
        return this.boundsMin;
    }

    @Override
    public BlockPos getBoundsMax() {
        return this.boundsMax;
    }

    @Override
    public void updateStatus(boolean updateGrid) {
        if (this.center == null) {
            return;
        }

        final long qe = this.center.getQEFrequency();

        if (this.thisSide != qe && this.thisSide != -qe) {
            if (this.thisSide != 0) {
                Locatables.quantumNetworkBridges().unregister(this.center.getWorld(), this.getLocatableKey());
            }

            this.thisSide = 0;
            this.otherSide = 0;

            if (qe != 0) {
                if (this.canUseNode(-qe)) {
                    this.otherSide = qe;
                    this.thisSide = -qe;
                } else if (this.canUseNode(qe)) {
                    this.thisSide = qe;
                    this.otherSide = -qe;
                }

                if (this.thisSide != 0) {
                    Locatables.quantumNetworkBridges().register(this.center.getWorld(), this.getLocatableKey(), this);
                }
            }
        }

        final Object myOtherSide = this.otherSide == 0 ? null
            : Locatables.quantumNetworkBridges().get(this.center.getWorld(), this.otherSide);

        boolean shutdown = false;

        if (myOtherSide instanceof QuantumCluster sideB) {
            final QuantumCluster sideA = this;

            if (sideA.isActive() && sideB.isActive()) {
                if (this.connection != null && this.connection.getConnection() != null) {
                    final IGridConnection existing = this.connection.getConnection();
                    final IGridNode a = existing.a();
                    final IGridNode b = existing.b();
                    final IGridNode sa = sideA.getNode();
                    final IGridNode sb = sideB.getNode();

                    if ((a == sa || b == sa) && (a == sb || b == sb)) {
                        return;
                    }
                }

                if (sideA.connection != null && sideA.connection.getConnection() != null) {
                    sideA.connection.getConnection().destroy();
                    sideA.connection = new ConnectionWrapper(null);
                }

                if (sideB.connection != null && sideB.connection.getConnection() != null) {
                    sideB.connection.getConnection().destroy();
                    sideB.connection = new ConnectionWrapper(null);
                }

                final ConnectionWrapper wrapper = new ConnectionWrapper(
                    GridHelper.createConnection(sideA.getNode(), sideB.getNode()));
                sideA.connection = wrapper;
                sideB.connection = wrapper;
            } else {
                shutdown = true;
            }
        } else {
            shutdown = true;
        }

        if (shutdown && this.connection != null && this.connection.getConnection() != null) {
            this.connection.getConnection().destroy();
            this.connection = new ConnectionWrapper(null);
        }
    }

    private boolean canUseNode(long qe) {
        final Object locatable = Locatables.quantumNetworkBridges().get(this.center.getWorld(), qe);
        if (locatable instanceof QuantumCluster qc) {
            final World level = qc.center.getWorld();

            if (!qc.destroyed) {
                if (level.isBlockLoaded(qc.center.getPos())) {
                    final MinecraftServer server = level.getMinecraftServer();
                    final World currentLevel = server != null ? server.getWorld(level.provider.getDimension()) : null;
                    final TileEntity te = level.getTileEntity(qc.center.getPos());
                    return te != qc.center || level != currentLevel;
                } else {
                    AELog.warn("Found a registered QNB with serial %s whose chunk seems unloaded: %s", qe, qc);
                }
            }
        }

        return true;
    }

    private boolean isActive() {
        return !this.destroyed && this.registered && this.hasQES() && this.getNode() != null;
    }

    @Nullable
    private IGridNode getNode() {
        return this.center == null ? null : this.center.getGridNode();
    }

    private boolean hasQES() {
        return this.thisSide != 0;
    }

    @Override
    public void destroy() {
        if (this.destroyed) {
            return;
        }

        this.destroyed = true;

        MBCalculator.setModificationInProgress(this);
        try {
            if (this.thisSide != 0 && this.center != null) {
                this.updateStatus(true);
                Locatables.quantumNetworkBridges().unregister(this.center.getWorld(), this.getLocatableKey());
            } else if (this.connection != null && this.connection.getConnection() != null) {
                this.connection.getConnection().destroy();
                this.connection = new ConnectionWrapper(null);
            }

            if (this.center != null) {
                this.center.updateStatus(null, (byte) -1, this.isUpdateStatus());
            }

            for (TileQuantumBridge bridge : this.ring) {
                if (bridge != null) {
                    bridge.updateStatus(null, (byte) -1, this.isUpdateStatus());
                }
            }

            this.center = null;
            this.ring = new TileQuantumBridge[8];
            this.registered = false;
            this.thisSide = 0;
            this.otherSide = 0;
        } finally {
            MBCalculator.setModificationInProgress(null);
        }
    }

    @Override
    public boolean isDestroyed() {
        return this.destroyed;
    }

    @Override
    public Iterator<TileQuantumBridge> getBlockEntities() {
        final ObjectList<TileQuantumBridge> result = new ObjectArrayList<>(9);
        for (TileQuantumBridge bridge : this.ring) {
            if (bridge != null) {
                result.add(bridge);
            }
        }
        if (this.center != null) {
            result.add(this.center);
        }
        return result.iterator();
    }

    private long getLocatableKey() {
        return this.thisSide;
    }

    public TileQuantumBridge getCenter() {
        return this.center;
    }

    void setCenter(TileQuantumBridge center) {
        this.registered = true;
        this.center = center;
    }

    boolean isUpdateStatus() {
        return this.updateStatus;
    }

    public void setUpdateStatus(boolean updateStatus) {
        this.updateStatus = updateStatus;
    }

    TileQuantumBridge[] getRing() {
        return this.ring;
    }

    @Override
    public String toString() {
        if (this.center == null) {
            return "QuantumCluster{no-center}";
        }

        return "QuantumCluster{" + this.center.getWorld() + "," + this.center.getPos() + "}";
    }

    @Nullable
    @Override
    public IGridNode getActionableNode() {
        return this.center == null ? null : this.center.getMainNode().getNode();
    }
}
