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

package ae2.tile.qnb;

import ae2.api.inventories.InternalInventory;
import ae2.api.networking.GridFlags;
import ae2.api.networking.IGridNodeListener;
import ae2.api.orientation.BlockOrientation;
import ae2.api.util.AECableType;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEItems;
import ae2.me.cluster.IAEMultiBlock;
import ae2.me.cluster.implementations.QuantumCalculator;
import ae2.me.cluster.implementations.QuantumCluster;
import ae2.tile.ServerTickingTile;
import ae2.tile.grid.AENetworkedInvTile;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.FilteredInternalInventory;
import ae2.util.inv.filter.IAEItemFilter;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import java.util.Date;
import java.util.EnumSet;

public class TileQuantumBridge extends AENetworkedInvTile
    implements IAEMultiBlock<QuantumCluster>, ServerTickingTile {

    private static final String ENTANGLED_SINGULARITY_ID = "entangled_singularity_id";
    private static int singularitySeed = 0;
    private final byte corner = 16;
    private final byte hasSingularity = 32;
    private final byte powered = 64;

    private final AppEngInternalInventory internalInventory = new AppEngInternalInventory(this, 1, 1);
    private final FilteredInternalInventory externalInventory = new FilteredInternalInventory(
        this.internalInventory,
        new EntangledSingularityFilter());

    private final QuantumCalculator calc = new QuantumCalculator(this);

    private byte constructed = -1;
    private QuantumCluster cluster;
    private boolean updateStatus;
    private boolean suppressBlockUpdates;

    public TileQuantumBridge() {
        this.getMainNode().setFlags(GridFlags.DENSE_CAPACITY);
        this.getMainNode().setIdlePowerUsage(22);
        this.onGridConnectableSidesChanged();
    }

    public static boolean isValidEntangledSingularity(ItemStack stack) {
        if (stack.isEmpty()
            || stack.getItem() != AEItems.QUANTUM_ENTANGLED_SINGULARITY.item()
            || !stack.hasTagCompound()) {
            return false;
        }

        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(ENTANGLED_SINGULARITY_ID, Constants.NBT.TAG_LONG);
    }

    public static void assignFrequency(ItemStack stack) {
        final long frequency = new Date().getTime() * 100L + singularitySeed++ % 100;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setLong(ENTANGLED_SINGULARITY_ID, frequency);
    }

    @Override
    public EnumSet<EnumFacing> getGridConnectableSides(BlockOrientation orientation) {
        if (!this.isFormed()) {
            return EnumSet.noneOf(EnumFacing.class);
        }

        if (this.isCorner() || this.isCenter()) {
            return this.getAdjacentQuantumBridges();
        }

        return EnumSet.allOf(EnumFacing.class);
    }

    @Override
    public void serverTick() {
        if (this.updateStatus) {
            this.updateStatus = false;

            if (this.cluster != null) {
                this.cluster.updateStatus(true);
            }

            this.markForUpdate();
            this.neighborUpdate(this.pos);
        }
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);

        int out = this.constructed;

        if (!this.internalInventory.getStackInSlot(0).isEmpty() && this.constructed != -1) {
            out |= this.hasSingularity;
        }

        if (this.getMainNode().isPowered() && this.constructed != -1) {
            out |= this.powered;
        }

        data.writeByte((byte) out);
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        final boolean changed = super.readFromStream(data);
        final int oldValue = this.constructed;
        this.constructed = data.readByte();
        return oldValue != this.constructed || changed;
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.internalInventory;
    }

    @Override
    protected InternalInventory getExposedInventoryForSide(EnumFacing side) {
        return this.isCenter() ? this.externalInventory : InternalInventory.empty();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (this.cluster != null) {
            this.cluster.updateStatus(true);
        }
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        this.updateStatus = true;
    }

    @Override
    public void onChunkUnloaded() {
        this.disconnect(false);
        super.onChunkUnloaded();
    }

    @Override
    public void onReady() {
        super.onReady();

        if (this.world != null) {
            if (this.world.getBlockState(this.pos).getBlock() == AEBlocks.QUANTUM_RING.block()) {
                this.getMainNode().setVisualRepresentation(AEBlocks.QUANTUM_RING.stack());
            } else if (this.world.getBlockState(this.pos).getBlock() == AEBlocks.QUANTUM_LINK.block()) {
                this.getMainNode().setVisualRepresentation(AEBlocks.QUANTUM_LINK.stack());
            }

            if (!this.world.isRemote) {
                this.calc.calculateMultiblock(this.world, this.pos);
            }
        }

        this.updateStatus = true;
    }

    @Override
    public void disconnect(boolean affectWorld) {
        if (this.cluster != null) {
            if (!affectWorld) {
                this.cluster.setUpdateStatus(false);
            }

            this.cluster.destroy();
        }

        this.cluster = null;

        if (affectWorld && !this.suppressBlockUpdates) {
            this.onGridConnectableSidesChanged();
        }
    }

    @Override
    public QuantumCluster getCluster() {
        return this.cluster;
    }

    @Override
    public boolean isValid() {
        return !this.isInvalid();
    }

    public void updateStatus(QuantumCluster cluster, byte flags, boolean affectWorld) {
        this.cluster = cluster;

        if (affectWorld && !this.suppressBlockUpdates) {
            if (this.constructed != flags) {
                this.constructed = flags;
                this.markForUpdate();
            }

            this.onGridConnectableSidesChanged();
        } else {
            this.constructed = flags;
        }
    }

    public boolean isCorner() {
        return this.constructed != -1 && (this.constructed & this.corner) == this.corner;
    }

    public EnumSet<EnumFacing> getAdjacentQuantumBridges() {
        final EnumSet<EnumFacing> result = EnumSet.noneOf(EnumFacing.class);

        if (this.world != null) {
            for (EnumFacing direction : EnumFacing.values()) {
                if (this.world.getTileEntity(this.pos.offset(direction)) instanceof TileQuantumBridge) {
                    result.add(direction);
                }
            }
        }

        return result;
    }

    public long getQEFrequency() {
        final ItemStack stack = this.internalInventory.getStackInSlot(0);
        if (isValidEntangledSingularity(stack)) {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag != null) {
                NBTBase frequencyTag = tag.getTag(ENTANGLED_SINGULARITY_ID);
                if (frequencyTag instanceof NBTTagLong) {
                    return ((NBTTagLong) frequencyTag).getLong();
                }
            }
        }

        return 0;
    }

    public boolean isPowered() {
        if (this.isClientSide()) {
            return this.constructed != -1 && (this.constructed & this.powered) == this.powered;
        }

        return this.getMainNode().isPowered();
    }

    public boolean isFormed() {
        return this.constructed != -1;
    }

    @Override
    public AECableType getCableConnectionType(EnumFacing dir) {
        return AECableType.DENSE_SMART;
    }

    public void neighborUpdate(BlockPos fromPos) {
        if (this.world != null && !this.world.isRemote) {
            this.calc.updateMultiblockAfterNeighborUpdate(this.world, this.pos, fromPos);
        }
    }

    public boolean hasQES() {
        if (this.constructed == -1) {
            return false;
        }

        return (this.constructed & this.hasSingularity) == this.hasSingularity;
    }

    public void breakClusterOnRemove() {
        if (this.cluster != null) {
            this.suppressBlockUpdates = true;
            try {
                this.cluster.destroy();
            } finally {
                this.suppressBlockUpdates = false;
            }
        }
    }

    public byte getCorner() {
        return this.corner;
    }

    private boolean isCenter() {
        return this.world != null && this.world.getBlockState(this.pos).getBlock() == AEBlocks.QUANTUM_LINK.block();
    }

    private static class EntangledSingularityFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return isValidEntangledSingularity(stack);
        }
    }
}
