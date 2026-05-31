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

import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.core.AppEng;
import ae2.core.settings.TickRates;
import ae2.items.parts.PartModels;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

public class LightP2PTunnelPart extends P2PTunnelPart<LightP2PTunnelPart> implements IGridTickable {

    private static final P2PModels MODELS = new P2PModels(AppEng.makeId("part/p2p/p2p_tunnel_light"));
    private int lastValue = 0;
    private int opacity = -1;

    public LightP2PTunnelPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().addService(IGridTickable.class, this);
    }

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    @Override
    protected float getPowerDrainPerTick() {
        return 0.5f;
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (getMainNode().hasGridBooted()) {
            this.onTunnelNetworkChange();
        }
    }

    @Override
    public void writeToStream(PacketBuffer data) {
        super.writeToStream(data);
        data.writeInt(this.isOutput() ? this.lastValue : 0);
    }

    @Override
    public boolean readFromStream(PacketBuffer data) {
        boolean changed = super.readFromStream(data);
        final int oldValue = this.lastValue;

        this.lastValue = data.readInt();

        this.setOutput(this.lastValue > 0);
        return changed || this.lastValue != oldValue;
    }

    private boolean doWork() {
        if (this.isOutput()) {
            return false;
        }

        final TileEntity te = this.getTileEntity();
        final World level = te.getWorld();
        final int newLevel = level.getLightFromNeighbors(te.getPos().offset(this.getSide()));

        if (this.lastValue != newLevel && this.getMainNode().isActive()) {
            this.lastValue = newLevel;
            for (LightP2PTunnelPart out : this.getOutputs()) {
                out.setLightLevel(this.lastValue);
            }
            return true;
        }
        return false;
    }

    @Override
    public void onNeighborChanged(IBlockAccess level, BlockPos pos, BlockPos neighbor) {
        if (this.isOutput() && pos.offset(this.getSide()).equals(neighbor)) {
            this.opacity = -1;
            this.getHost().markForUpdate();
        } else {
            this.doWork();
        }
    }

    @Override
    public int getLightLevel() {
        if (this.isOutput() && this.isPowered() && this.getInput() != null) {
            return this.blockLight(this.lastValue);
        }

        return 0;
    }

    private void setLightLevel(int out) {
        this.lastValue = out;
        this.getHost().markForUpdate();
    }

    private int blockLight(int emit) {
        if (this.opacity == -1) {
            TileEntity be = getHost().getTileEntity();
            World level = be.getWorld();
            BlockPos pos = be.getPos();
            this.opacity = level.getLightFromNeighbors(pos.offset(getSide()));
        }

        return Math.max(0, emit - this.opacity);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.lastValue = tag.getInteger("lastValue");
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("lastValue", this.lastValue);
    }

    @Override
    public void onTunnelConfigChange() {
        this.onTunnelNetworkChange();
    }

    @Override
    public void onTunnelNetworkChange() {
        if (this.isOutput()) {
            final LightP2PTunnelPart src = this.getInput();
            if (src != null && src.getMainNode().isActive()) {
                this.setLightLevel(src.lastValue);
            } else {
                this.getHost().markForUpdate();
            }
        } else {
            this.doWork();
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.LightTunnel, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        return this.doWork() ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(this.isPowered(), this.isActive());
    }
}


