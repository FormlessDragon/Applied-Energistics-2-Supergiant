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

import ae2.api.networking.IGridNodeListener;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.core.AppEng;
import ae2.items.parts.PartModels;
import ae2.util.Platform;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

@SuppressWarnings("deprecation")
public class RedstoneP2PTunnelPart extends P2PTunnelPart<RedstoneP2PTunnelPart> {

    private static final P2PModels MODELS = new P2PModels(AppEng.makeId("part/p2p/p2p_tunnel_redstone"));
    private int power;
    private boolean recursive = false;

    public RedstoneP2PTunnelPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    @Override
    protected float getPowerDrainPerTick() {
        return 0.5f;
    }

    private void setNetworkReady() {
        if (this.isOutput()) {
            final RedstoneP2PTunnelPart in = this.getInput();
            if (in != null) {
                this.putInput(in.power);
            } else {
                this.putInput(0);
            }
        }
    }

    private void putInput(Object o) {
        if (this.recursive) {
            return;
        }

        this.recursive = true;
        if (this.isOutput()) {
            final int newPower = this.getMainNode().isActive() ? (Integer) o : 0;
            if (this.power != newPower) {
                this.power = newPower;
                this.notifyNeighbors();
            }
        }
        this.recursive = false;
    }

    private void notifyNeighbors() {
        final World level = this.getTileEntity().getWorld();

        Platform.notifyBlocksOfNeighbors(level, this.getTileEntity().getPos());

        for (EnumFacing face : EnumFacing.VALUES) {
            Platform.notifyBlocksOfNeighbors(level, this.getTileEntity().getPos().offset(face));
        }
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (getMainNode().hasGridBooted()) {
            this.setNetworkReady();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.power = tag.getInteger("power");
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("power", this.power);
    }

    @Override
    public void onTunnelNetworkChange() {
        this.setNetworkReady();
    }

    @Override
    public void onNeighborChanged(IBlockAccess level, BlockPos pos, BlockPos neighbor) {
        if (!this.isOutput()) {
            final BlockPos target = this.getTileEntity().getPos().offset(this.getSide());
            final World world = this.getTileEntity().getWorld();
            final IBlockState state = world.getBlockState(target);
            final Block block = state.getBlock();
            if (block != null) {
                EnumFacing srcSide = this.getSide();
                if (block instanceof BlockRedstoneWire) {
                    srcSide = EnumFacing.UP;
                }

                this.power = block.getWeakPower(state, world, target, srcSide);
                this.power = Math.max(this.power, block.getStrongPower(state, world, target, srcSide));
                this.sendToOutput(this.power);
            } else {
                this.sendToOutput(0);
            }
        }
    }

    @Override
    public boolean canConnectRedstone() {
        return true;
    }

    @Override
    public int isProvidingStrongPower() {
        return this.isOutput() ? this.power : 0;
    }

    @Override
    public int isProvidingWeakPower() {
        return this.isOutput() ? this.power : 0;
    }

    private void sendToOutput(int power) {
        for (RedstoneP2PTunnelPart rs : this.getOutputs()) {
            rs.putInput(power);
        }
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(this.isPowered(), this.isActive());
    }
}


