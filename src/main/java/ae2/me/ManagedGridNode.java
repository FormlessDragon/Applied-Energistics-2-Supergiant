/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package ae2.me;

import ae2.api.features.IPlayerRegistry;
import ae2.api.networking.GridFlags;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.IGridNodeService;
import ae2.api.networking.IManagedGridNode;
import ae2.api.stacks.AEItemKey;
import ae2.api.util.AEColor;
import ae2.core.AELog;
import com.google.common.base.Preconditions;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MutableClassToInstanceMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Manages the lifecycle of a {@link IGridNode}.
 */
@SuppressWarnings("UnusedReturnValue")
public class ManagedGridNode implements IManagedGridNode {
    @Nullable
    private InitData<?> initData;
    private String tagName = "gn";
    @Nullable
    private GridNode node = null;

    public <T> ManagedGridNode(T nodeOwner, IGridNodeListener<? super T> listener) {
        this.initData = new InitData<>(nodeOwner, listener);
    }

    @Override
    public ManagedGridNode setInWorldNode(boolean accessible) {
        getInitData().inWorldNode = accessible;
        return this;
    }

    @Override
    public ManagedGridNode setTagName(String tagName) {
        if (getInitData().data != null) {
            throw new IllegalStateException("Cannot change tag name after NBT has already been read.");
        }
        this.tagName = Objects.requireNonNull(tagName);
        return this;
    }

    @Override
    public void destroy() {
        if (this.node != null) {
            this.node.destroy();
            this.node = null;
        }
    }

    @Override
    public void create(World world, @Nullable BlockPos blockPos) {
        var initData = getInitData();
        initData.level = world;
        initData.pos = blockPos;

        if (!initData.level.isRemote) {
            this.initData = null;
            createNode(initData);
        }
    }

    private void createNode(InitData<?> initData) {
        Preconditions.checkState(node == null);

        var node = initData.createNode();
        if (initData.data != null) {
            node.loadFromNBT(this.tagName, initData.data);
        }
        this.node = node;
        this.node.markReady();
    }

    @Override
    public IGridNode getNode() {
        return this.node;
    }

    @Override
    public void loadFromNBT(NBTTagCompound tag) {
        if (node == null) {
            getInitData().data = tag;
        } else {
            this.node.loadFromNBT(this.tagName, tag);
        }
    }

    @Override
    public void saveToNBT(NBTTagCompound tag) {
        if (this.node != null) {
            this.node.saveToNBT(this.tagName, tag);
        }
    }

    @Override
    public boolean isReady() {
        return initData == null && node != null;
    }

    @Override
    public boolean isActive() {
        if (this.node == null) {
            return false;
        }
        return this.node.isActive();
    }

    @Override
    public boolean isOnline() {
        if (this.node == null) {
            return false;
        }
        return this.node.isOnline();
    }

    @Override
    public boolean isPowered() {
        var grid = getGrid();
        return grid != null && grid.getEnergyService().isNetworkPowered();
    }

    @Override
    public boolean hasGridBooted() {
        if (this.node == null) {
            return false;
        }
        return this.node.hasGridBooted();
    }

    @Override
    public void setOwningPlayerId(int ownerPlayerId) {
        if (this.initData != null) {
            getInitData().owner = ownerPlayerId;
        } else {
            if (node != null) {
                node.setOwningPlayerId(ownerPlayerId);
            }
        }
    }

    @Override
    public void setOwningPlayer(EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            setOwningPlayerId(IPlayerRegistry.getPlayerId((EntityPlayerMP) player));
        }
    }

    @Override
    public ManagedGridNode setFlags(GridFlags... flags) {
        var flagSet = EnumSet.noneOf(GridFlags.class);
        Collections.addAll(flagSet, flags);
        getInitData().flags = flagSet;
        return this;
    }

    @Override
    public ManagedGridNode setExposedOnSides(Set<EnumFacing> directions) {
        if (node == null) {
            getInitData().exposedOnSides = ImmutableSet.copyOf(directions);
        } else if (node instanceof InWorldGridNode) {
            ((InWorldGridNode) node).setExposedOnSides(directions);
        }
        return this;
    }

    @Override
    public ManagedGridNode setVisualRepresentation(@Nullable AEItemKey visualRepresentation) {
        if (node == null) {
            getInitData().visualRepresentation = visualRepresentation;
        } else {
            node.setVisualRepresentation(visualRepresentation);
        }
        return this;
    }

    @Override
    public ManagedGridNode setIdlePowerUsage(double usagePerTick) {
        Preconditions.checkArgument(usagePerTick >= 0, "usagePerTick must be >= 0");

        if (node == null) {
            getInitData().idlePowerUsage = usagePerTick;
        } else {
            node.setIdlePowerUsage(usagePerTick);
        }
        return this;
    }

    private InitData<?> getInitData() {
        if (initData == null) {
            throw new IllegalStateException(
                "The node has already been initialized. Initialization data cannot be changed anymore.");
        }
        return initData;
    }

    @Override
    public <T extends IGridNodeService> ManagedGridNode addService(Class<T> serviceClass, T service) {
        var initData = getInitData();
        if (initData.services == null) {
            initData.services = MutableClassToInstanceMap.create();
        }
        initData.services.putInstance(serviceClass, service);
        return this;
    }

    @Override
    public ManagedGridNode setGridColor(AEColor gridColor) {
        if (this.node == null) {
            getInitData().gridColor = gridColor;
        } else {
            node.setGridColor(gridColor);
        }
        return this;
    }

    private static class InitData<T> {
        private final T logicalHost;
        private final IGridNodeListener<T> listener;
        public ClassToInstanceMap<IGridNodeService> services;
        private NBTTagCompound data = null;

        // The following values are used until the node is constructed, and then are applied to the node
        private AEColor gridColor = AEColor.TRANSPARENT;
        private Set<EnumFacing> exposedOnSides = EnumSet.allOf(EnumFacing.class);
        private AEItemKey visualRepresentation = null;
        private EnumSet<GridFlags> flags = EnumSet.noneOf(GridFlags.class);
        private double idlePowerUsage = 1.0;
        private int owner = -1; // ME player id of owner
        private World level;
        private BlockPos pos;
        private boolean inWorldNode;

        public InitData(T logicalHost, IGridNodeListener<T> listener) {
            this.logicalHost = Objects.requireNonNull(logicalHost);
            this.listener = Objects.requireNonNull(listener);
        }

        public GridNode createNode() {
            GridNode node;
            if (inWorldNode) {
                Preconditions.checkState(pos != null, "No position was set for an in-world node");
                InWorldGridNode inWorld = new InWorldGridNode((WorldServer) level, pos, logicalHost, listener, flags);
                inWorld.setExposedOnSides(exposedOnSides);
                node = inWorld;
            } else {
                node = new GridNode((WorldServer) level, logicalHost, listener, flags);
            }
            node.setGridColor(gridColor);
            node.setOwningPlayerId(owner);
            node.setIdlePowerUsage(idlePowerUsage);
            node.setVisualRepresentation(visualRepresentation);
            if (services != null) {
                for (var serviceClass : services.keySet()) {
                    addService(node, serviceClass);
                }
            }
            AELog.grid("Created node %s", node);
            return node;
        }

        private <SC extends IGridNodeService> void addService(GridNode node, Class<SC> serviceClass) {
            node.addService(serviceClass, services.getInstance(serviceClass));
        }
    }

}
