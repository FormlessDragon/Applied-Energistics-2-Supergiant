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

package ae2.parts.p2p;

import ae2.api.networking.GridFlags;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IGridConnection;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.parts.IPartHost;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.util.AECableType;
import ae2.core.AppEng;
import ae2.core.settings.TickRates;
import ae2.hooks.ticking.TickHandler;
import ae2.items.parts.PartModels;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import org.jetbrains.annotations.Nullable;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MEP2PTunnelPart extends P2PTunnelPart<MEP2PTunnelPart> implements IGridTickable {

    private static final P2PModels MODELS = new P2PModels(AppEng.makeId("part/p2p/p2p_tunnel_me"));
    private final Reference2ObjectMap<MEP2PTunnelPart, IGridConnection> connections = new Reference2ObjectOpenHashMap<>();
    private final IManagedGridNode outerNode = GridHelper
        .createManagedNode(this, NodeListener.INSTANCE)
        .setTagName("outer")
        .setInWorldNode(true)
        .setFlags(GridFlags.DENSE_CAPACITY, GridFlags.CANNOT_CARRY_COMPRESSED);
    private ConnectionUpdate pendingUpdate = ConnectionUpdate.NONE;

    public MEP2PTunnelPart(IPartItem<?> partItem) {
        super(partItem);
        this.getMainNode()
            .setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.COMPRESSED_CHANNEL)
            .addService(IGridTickable.class, this);
    }

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    @Override
    protected float getPowerDrainPerTick() {
        return 2.0f;
    }

    @Override
    public void readFromNBT(NBTTagCompound extra) {
        super.readFromNBT(extra);
        this.outerNode.loadFromNBT(extra);
    }

    @Override
    public void writeToNBT(NBTTagCompound extra) {
        super.writeToNBT(extra);
        this.outerNode.saveToNBT(extra);
    }

    @Override
    public void onTunnelNetworkChange() {
        super.onTunnelNetworkChange();
        if (!this.isOutput() || !this.connections.isEmpty()) {
            getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
        }
    }

    @Override
    public AECableType getExternalCableConnectionType() {
        return AECableType.DENSE_SMART;
    }

    @Override
    public void removeFromWorld() {
        super.removeFromWorld();
        this.outerNode.destroy();
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        this.outerNode.create(getLevel(), getTileEntity().getPos());
    }

    @Override
    public void setPartHostInfo(@Nullable EnumFacing side, IPartHost host, TileEntity blockEntity) {
        super.setPartHostInfo(side, host, blockEntity);
        if (side != null) {
            this.outerNode.setExposedOnSides(EnumSet.of(side));
        }
    }

    @Override
    public IGridNode getExternalFacingNode() {
        return this.outerNode.getNode();
    }

    @Override
    public void onPlacement(EntityPlayer player) {
        super.onPlacement(player);
        this.outerNode.setOwningPlayer(player);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.METunnel, true);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        this.pendingUpdate = node.isOnline() ? ConnectionUpdate.CONNECT : ConnectionUpdate.DISCONNECT;
        TickHandler.instance().addCallable(getLevel(), this::updateConnections);
        return TickRateModulation.SLEEP;
    }

    private void updateConnections() {
        ConnectionUpdate operation = this.pendingUpdate;
        this.pendingUpdate = ConnectionUpdate.NONE;

        IGridNode mainNode = getMainNode().getNode();
        Object mainGrid = mainNode != null ? mainNode.grid() : null;

        if (isOutput()) {
            operation = ConnectionUpdate.DISCONNECT;
        } else if (mainGrid == null) {
            operation = ConnectionUpdate.DISCONNECT;
        }

        if (operation == ConnectionUpdate.DISCONNECT) {
            for (IGridConnection connection : this.connections.values()) {
                connection.destroy();
            }
            this.connections.clear();
        } else if (operation == ConnectionUpdate.CONNECT) {
            List<MEP2PTunnelPart> outputs = getOutputs();
            Iterator<Map.Entry<MEP2PTunnelPart, IGridConnection>> it = this.connections.entrySet()
                                                                                       .iterator();
            while (it.hasNext()) {
                Map.Entry<MEP2PTunnelPart, IGridConnection> entry = it.next();
                MEP2PTunnelPart output = entry.getKey();
                IGridConnection connection = entry.getValue();
                if (output.getMainNode().getGrid() != mainGrid
                    || !output.getMainNode().isOnline()
                    || !outputs.contains(output)) {
                    connection.destroy();
                    it.remove();
                }
            }

            for (MEP2PTunnelPart output : outputs) {
                if (!output.getMainNode().isOnline() || this.connections.containsKey(output)) {
                    continue;
                }

                IGridConnection connection = GridHelper.createConnection(getExternalFacingNode(),
                    output.getExternalFacingNode());
                this.connections.put(output, connection);
            }
        }
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(this.isPowered(), this.isActive());
    }

    private enum ConnectionUpdate {
        NONE,
        DISCONNECT,
        CONNECT
    }
}
