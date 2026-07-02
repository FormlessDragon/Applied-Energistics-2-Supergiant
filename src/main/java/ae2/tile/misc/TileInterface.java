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
package ae2.tile.misc;

import ae2.api.AECapabilities;
import ae2.api.inventories.ISegmentedInventory;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.IManagedGridNode;
import ae2.api.upgrades.IUpgradeableObject;
import ae2.api.util.AECableType;
import ae2.api.util.IConfigurableObject;
import ae2.core.definitions.AEBlocks;
import ae2.helpers.IPriorityHost;
import ae2.helpers.InterfaceLogic;
import ae2.helpers.InterfaceLogicHost;
import ae2.tile.grid.AENetworkedInvTile;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.Nullable;
import java.util.List;

public class TileInterface extends AENetworkedInvTile
    implements InterfaceLogicHost, IPriorityHost, IUpgradeableObject, IConfigurableObject {
    private static final String CELL_TERMINAL_SUBNET_ID_TAG = "cellTerminalSubnetId";

    private static final IGridNodeListener<TileInterface> NODE_LISTENER = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(TileInterface nodeOwner, IGridNode node) {
            nodeOwner.saveChanges();
        }

        @Override
        public void onGridChanged(TileInterface nodeOwner, IGridNode node) {
            nodeOwner.logic.gridChanged();
        }

        @Override
        public void onStateChanged(TileInterface nodeOwner, IGridNode node, State state) {
            nodeOwner.onMainNodeStateChanged(state);
        }
    };

    private final InterfaceLogic logic = new InterfaceLogic(this.getMainNode(), this, AEBlocks.INTERFACE.item());
    @Nullable
    private String cellTerminalSubnetId;

    @Override
    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, NODE_LISTENER);
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (this.getMainNode().hasGridBooted()) {
            this.logic.notifyNeighbors();
        }
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        this.logic.writeToNBT(data);
        if (this.cellTerminalSubnetId != null && !this.cellTerminalSubnetId.isEmpty()) {
            data.setString(CELL_TERMINAL_SUBNET_ID_TAG, this.cellTerminalSubnetId);
        }
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.logic.readFromNBT(data);
        this.cellTerminalSubnetId = data.hasKey(CELL_TERMINAL_SUBNET_ID_TAG, Constants.NBT.TAG_STRING)
            ? data.getString(CELL_TERMINAL_SUBNET_ID_TAG)
            : null;
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops) {
        super.addAdditionalDrops(drops);
        this.logic.addDrops(drops);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.logic.clearContent();
    }

    @Override
    public InterfaceLogic getInterfaceLogic() {
        return this.logic;
    }

    @Override
    public @Nullable String getCellTerminalSubnetId() {
        return this.cellTerminalSubnetId;
    }

    @Override
    public void setCellTerminalSubnetId(String subnetId) {
        if (subnetId == null || subnetId.isEmpty()) {
            throw new IllegalArgumentException("subnetId must not be empty");
        }
        if (!subnetId.equals(this.cellTerminalSubnetId)) {
            this.cellTerminalSubnetId = subnetId;
            this.saveChanges();
        }
    }

    @Override
    public TileEntity getTileEntity() {
        return this;
    }

    @Override
    public ItemStack getItemFromTile() {
        return new ItemStack(AEBlocks.INTERFACE.block());
    }

    @Override
    public AECableType getCableConnectionType(EnumFacing dir) {
        return this.logic.getCableConnectionType(dir);
    }

    @Override
    public ItemStack getMainContainerIcon() {
        return new ItemStack(AEBlocks.INTERFACE.block());
    }

    @Nullable
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (ISegmentedInventory.UPGRADES.equals(id)) {
            return this.logic.getUpgrades();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == AECapabilities.ME_STORAGE) {
            return (T) this.logic.getInventory();
        }
        if (capability == AECapabilities.GENERIC_INTERNAL_INV) {
            return (T) this.logic.getStorage();
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == AECapabilities.ME_STORAGE) {
            return true;
        }
        if (capability == AECapabilities.GENERIC_INTERNAL_INV) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

}
