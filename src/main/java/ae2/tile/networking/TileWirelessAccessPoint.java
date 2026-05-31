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
package ae2.tile.networking;

import ae2.api.implementations.IPowerChannelState;
import ae2.api.implementations.blockentities.IWirelessAccessPoint;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.GridFlags;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNodeListener;
import ae2.api.orientation.BlockOrientation;
import ae2.api.orientation.RelativeSide;
import ae2.api.util.AECableType;
import ae2.api.util.DimensionalBlockPos;
import ae2.core.AEConfig;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEItems;
import ae2.tile.grid.AENetworkedInvTile;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.filter.AEItemDefinitionFilter;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

import java.util.EnumSet;

public class TileWirelessAccessPoint extends AENetworkedInvTile
    implements IWirelessAccessPoint, IPowerChannelState {

    public static final int POWERED_FLAG = 1;
    public static final int CHANNEL_FLAG = 2;

    private final AppEngInternalInventory inv = new AppEngInternalInventory(this, 1);

    private int clientFlags = 0;

    public TileWirelessAccessPoint() {
        this.inv.setFilter(new AEItemDefinitionFilter(AEItems.WIRELESS_BOOSTER));
        this.getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL);
    }

    @Override
    public ItemStack getItemFromTile() {
        return AEBlocks.WIRELESS_ACCESS_POINT.stack();
    }

    @Override
    public EnumSet<EnumFacing> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.of(orientation.getSide(RelativeSide.BACK));
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        this.markForUpdate();
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        final boolean changed = super.readFromStream(data);
        final int oldFlags = this.getClientFlags();
        this.setClientFlags(data.readByte());
        return oldFlags != this.getClientFlags() || changed;
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        this.setClientFlags(getCurrentClientFlags());
        data.writeByte((byte) this.getClientFlags());
    }

    @Override
    public AECableType getCableConnectionType(EnumFacing dir) {
        return super.getCableConnectionType(dir);
    }

    @Override
    public DimensionalBlockPos getLocation() {
        return new DimensionalBlockPos(this);
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.inv;
    }

    @Override
    public void onReady() {
        this.updatePower();
        super.onReady();
        this.markForUpdate();
    }

    private void updatePower() {
        this.getMainNode().setIdlePowerUsage(AEConfig.instance().wireless_getPowerDrain(this.getBoosters()));
    }

    private int getBoosters() {
        final ItemStack boosters = this.inv.getStackInSlot(0);
        return boosters.isEmpty() ? 0 : boosters.getCount();
    }

    @Override
    protected void saveVisualState(NBTTagCompound data) {
        super.saveVisualState(data);
        data.setInteger("clientFlags", getCurrentClientFlags());
    }

    @Override
    protected void loadVisualState(NBTTagCompound data) {
        super.loadVisualState(data);
        if (data.hasKey("clientFlags")) {
            setClientFlags(data.getInteger("clientFlags"));
        }
    }

    @Override
    public void saveChanges() {
        this.updatePower();
        super.saveChanges();
    }

    @Override
    public double getRange() {
        return AEConfig.instance().wireless_getMaxRange(this.getBoosters());
    }

    @Override
    public boolean isActive() {
        if (isClientSide()) {
            return this.isPowered() && CHANNEL_FLAG == (this.getClientFlags() & CHANNEL_FLAG);
        }

        return this.getMainNode().isOnline();
    }

    @Override
    public IGrid getGrid() {
        return getMainNode().getGrid();
    }

    @Override
    public boolean isPowered() {
        if (!isClientSide()) {
            IGrid grid = this.getMainNode().getGrid();
            return grid != null && grid.getEnergyService().isNetworkPowered();
        }

        return POWERED_FLAG == (this.getClientFlags() & POWERED_FLAG);
    }

    private int getCurrentClientFlags() {
        int flags = 0;
        IGrid grid = this.getMainNode().getGrid();
        if (grid != null && grid.getEnergyService().isNetworkPowered()) {
            flags |= POWERED_FLAG;
        }

        var node = this.getMainNode().getNode();
        if (node != null && node.meetsChannelRequirements()) {
            flags |= CHANNEL_FLAG;
        }
        return flags;
    }

    public int getClientFlags() {
        return this.clientFlags;
    }

    private void setClientFlags(int clientFlags) {
        this.clientFlags = clientFlags;
    }
}
