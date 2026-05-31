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

package ae2.tile.networking;

import ae2.api.implementations.tiles.IColorableTile;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IInWorldGridNodeHost;
import ae2.api.parts.IFacadeContainer;
import ae2.api.parts.IPart;
import ae2.api.parts.IPartHost;
import ae2.api.parts.IPartItem;
import ae2.api.parts.RegisterPartCapabilitiesEventInternal;
import ae2.api.parts.SelectedPart;
import ae2.api.util.AECableType;
import ae2.api.util.AEColor;
import ae2.api.util.DimensionalBlockPos;
import ae2.client.render.cablebus.CableBusRenderState;
import ae2.parts.CableBusContainer;
import ae2.tile.AEBaseTile;
import ae2.tile.ClientTickingTile;
import ae2.util.Platform;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nullable;
import java.util.List;

public class TileCableBus extends AEBaseTile implements IPartHost, IInWorldGridNodeHost, IColorableTile, ClientTickingTile {

    private CableBusContainer cableBus = new CableBusContainer(this);
    private int oldLightValue = -1;
    private boolean dropItems = true;
    private int pendingClientRenderRefreshTicks;

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.cableBus.readFromNBT(data);
        if (this.world != null && this.world.isRemote) {
            this.queueClientRenderRefresh();
            this.onVisualStateUpdated();
        }
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        this.cableBus.writeToNBT(data);
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        this.cableBus.writeToStream(new PacketBuffer(data));
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        boolean changed = super.readFromStream(data);
        changed |= this.cableBus.readFromStream(new PacketBuffer(data));

        int newLightValue = this.cableBus.getLightValue();
        if (newLightValue != this.oldLightValue && this.world != null) {
            this.oldLightValue = newLightValue;
            this.world.checkLight(this.pos);
            changed = true;
        }

        return changed;
    }

    @Override
    public void onReady() {
        super.onReady();
        if (this.cableBus.isEmpty()) {
            if (this.world != null && this.world.getTileEntity(this.pos) == this) {
                this.world.destroyBlock(this.pos, true);
            }
        } else {
            this.cableBus.addToWorld();
            if (this.world != null && this.world.isRemote) {
                this.queueClientRenderRefresh();
                this.onVisualStateUpdated();
            }
        }
    }

    @Override
    protected void setRemoved() {
        this.cableBus.removeFromWorld();
    }

    @Override
    protected void clearRemoved() {
        scheduleInit();
        if (this.world != null && this.world.isRemote) {
            this.queueClientRenderRefresh();
        }
    }

    @Override
    protected void onChunkUnloaded() {
        this.cableBus.removeFromWorld();
    }

    @Nullable
    @Override
    public IGridNode getGridNode(EnumFacing dir) {
        return this.cableBus.getGridNode(dir);
    }

    @Override
    public AECableType getCableConnectionType(EnumFacing dir) {
        return this.cableBus.getCableConnectionType(dir);
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 900.0;
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return this.cableBus.getCableConnectionLength(cable);
    }

    @Override
    public IFacadeContainer getFacadeContainer() {
        return this.cableBus.getFacadeContainer();
    }

    @Nullable
    @Override
    public IPart getPart(@Nullable EnumFacing side) {
        return this.cableBus.getPart(side);
    }

    @Override
    public boolean canAddPart(ItemStack part, @Nullable EnumFacing side) {
        return this.cableBus.canAddPart(part, side);
    }

    @Nullable
    @Override
    public <T extends IPart> T addPart(IPartItem<T> partItem, @Nullable EnumFacing side, @Nullable EntityPlayer owner) {
        return this.cableBus.addPart(partItem, side, owner);
    }

    @Nullable
    @Override
    public <T extends IPart> T replacePart(IPartItem<T> partItem, @Nullable EnumFacing side, @Nullable EntityPlayer owner,
                                           @Nullable EnumHand hand) {
        return this.cableBus.replacePart(partItem, side, owner, hand);
    }

    @Override
    public void removePartFromSide(@Nullable EnumFacing side) {
        this.cableBus.removePartFromSide(side);
    }

    @Override
    public void markForUpdate() {
        int newLightValue = this.cableBus.getLightValue();
        if (this.world != null && newLightValue != this.oldLightValue) {
            this.oldLightValue = newLightValue;
            this.world.checkLight(this.pos);
        }

        super.markForUpdate();
    }

    @Override
    public DimensionalBlockPos getLocation() {
        return new DimensionalBlockPos(this);
    }

    @Override
    public TileEntity getTileEntity() {
        return this;
    }

    @Override
    public AEColor getColor() {
        return this.cableBus.getColor();
    }

    @Override
    public void clearContainer() {
        this.cableBus = new CableBusContainer(this);
    }

    @Override
    public boolean isBlocked(EnumFacing side) {
        return false;
    }

    @Override
    public SelectedPart selectPartLocal(Vec3d pos) {
        return this.cableBus.selectPartLocal(pos);
    }

    @Override
    public Iterable<AxisAlignedBB> getCollisionShape(@Nullable Entity entity) {
        return this.cableBus.getCollisionShape(entity);
    }

    @Override
    public boolean removePart(IPart part) {
        return this.cableBus.removePart(part);
    }

    @Override
    public void markForSave() {
        this.saveChanges();
    }

    @Override
    public void partChanged() {
        this.notifyNeighbors();
    }

    @Override
    public boolean hasRedstone() {
        return this.cableBus.hasRedstone();
    }

    @Override
    public boolean isEmpty() {
        return this.cableBus.isEmpty();
    }

    @Override
    public void cleanup() {
        if (this.world != null) {
            this.world.setBlockToAir(this.pos);
        }
    }

    @Override
    public void notifyNeighbors() {
        if (this.world != null && this.world.isBlockLoaded(this.pos) && !CableBusContainer.isLoading()) {
            Platform.notifyBlocksOfNeighbors(this.world, this.pos);
        }
    }

    @Override
    public void notifyNeighborNow(EnumFacing side) {
        if (this.world == null) {
            return;
        }

        BlockPos targetPos = this.pos.offset(side);
        if (!this.world.isBlockLoaded(targetPos)) {
            return;
        }

        if (CableBusContainer.isLoading()) {
            return;
        }

        IBlockState targetState = this.world.getBlockState(targetPos);
        if (!targetState.getBlock().isAir(targetState, this.world, targetPos)) {
            targetState.neighborChanged(this.world, targetPos, this.world.getBlockState(this.pos).getBlock(), this.pos);
        }
    }

    public boolean recolourBlock(EnumFacing side, AEColor colour, EntityPlayer who) {
        return this.cableBus.recolourBlock(side, colour, who);
    }

    @Override
    public boolean isInWorld() {
        return this.cableBus.isInWorld();
    }

    public boolean isRequiresDynamicRender() {
        return this.cableBus.isRequiresDynamicRender();
    }

    public CableBusContainer getCableBus() {
        return this.cableBus;
    }

    public CableBusRenderState getRenderState() {
        CableBusRenderState renderState = this.cableBus.getRenderState();
        renderState.setPos(this.pos);
        return renderState;
    }

    public void disableDrops() {
        this.dropItems = false;
    }

    public boolean shouldDropItems() {
        return this.dropItems;
    }

    public void addPartDrops(List<ItemStack> drops) {
        this.cableBus.addPartDrops(drops);
    }

    public boolean onUseItemOn(ItemStack heldItem, EntityPlayer player, EnumHand hand, Vec3d localPos) {
        return this.cableBus.onUseItemOn(heldItem, player, hand, localPos);
    }

    public boolean onUseWithoutItem(EntityPlayer player, Vec3d localPos) {
        return this.cableBus.onUseWithoutItem(player, localPos);
    }

    public boolean onWrenched(EntityPlayer player, Vec3d localPos) {
        return this.cableBus.onWrenched(player, localPos);
    }

    public boolean onClicked(EntityPlayer player, Vec3d localPos) {
        return this.cableBus.onClicked(player, localPos);
    }

    public void onNeighborChanged(World world, BlockPos pos, BlockPos neighbor) {
        this.cableBus.onNeighborChanged(world, pos, neighbor);
    }

    public void onUpdateShape(EnumFacing side) {
        this.cableBus.onUpdateShape(side);
    }

    @Override
    public void clientTick() {
        if (this.pendingClientRenderRefreshTicks <= 0 || this.world == null || !this.world.isRemote) {
            return;
        }

        if (!this.world.isBlockLoaded(this.pos)) {
            return;
        }

        this.pendingClientRenderRefreshTicks--;
        this.onVisualStateUpdated();
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (RegisterPartCapabilitiesEventInternal.hasCapability(this, capability, facing)) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        T result = RegisterPartCapabilitiesEventInternal.getCapability(this, capability, facing);
        if (result != null) {
            return result;
        }
        return super.getCapability(capability, facing);
    }

    private void queueClientRenderRefresh() {
        this.pendingClientRenderRefreshTicks = Math.max(this.pendingClientRenderRefreshTicks, 3);
    }
}
