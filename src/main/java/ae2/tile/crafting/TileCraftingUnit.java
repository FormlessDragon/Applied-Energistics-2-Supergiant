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

package ae2.tile.crafting;

import ae2.api.ids.AEBlockIds;
import ae2.api.implementations.IPowerChannelState;
import ae2.api.networking.GridFlags;
import ae2.api.networking.IGridMultiblock;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.orientation.BlockOrientation;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigurableObject;
import ae2.block.crafting.AbstractCraftingUnitBlock;
import ae2.me.cluster.IAEMultiBlock;
import ae2.me.cluster.implementations.CraftingCPUCalculator;
import ae2.me.cluster.implementations.CraftingCPUCluster;
import ae2.tile.grid.AENetworkedTile;
import ae2.util.NullConfigManager;
import ae2.util.Platform;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class TileCraftingUnit extends AENetworkedTile
    implements IAEMultiBlock<CraftingCPUCluster>, IPowerChannelState, IConfigurableObject {

    private static final long KILOBYTE = 1024L;

    private final CraftingCPUCalculator calc = new CraftingCPUCalculator(this);
    private NBTTagCompound previousState;
    private boolean coreBlock;
    private CraftingCPUCluster cluster;
    private ClientState clientState = ClientState.DEFAULT;

    public TileCraftingUnit() {
        this.getMainNode().setFlags(GridFlags.MULTIBLOCK, GridFlags.REQUIRE_CHANNEL)
            .addService(IGridMultiblock.class, this::getMultiblockNodes);
    }

    private static int encodeConnections(EnumSet<EnumFacing> connections) {
        int mask = 0;
        for (EnumFacing facing : connections) {
            mask |= 1 << facing.getIndex();
        }
        return mask;
    }

    private static EnumSet<EnumFacing> decodeConnections(int mask) {
        EnumSet<EnumFacing> connections = EnumSet.noneOf(EnumFacing.class);
        for (EnumFacing facing : EnumFacing.values()) {
            if ((mask & (1 << facing.getIndex())) != 0) {
                connections.add(facing);
            }
        }
        return connections;
    }

    @Override
    public ItemStack getItemFromTile() {
        if (this.world == null) {
            return ItemStack.EMPTY;
        }

        Block block = this.world.getBlockState(this.pos).getBlock();
        Item item = Item.getItemFromBlock(block);
        return item == null ? ItemStack.EMPTY : new ItemStack(block);
    }

    public void setName(String name) {
        this.setCustomName(name);
        if (this.cluster != null) {
            this.cluster.updateName();
        }
    }

    @Nullable
    private ResourceLocation getCraftingBlockId() {
        if (this.world == null) {
            return null;
        }
        return this.world.getBlockState(this.pos).getBlock().getRegistryName();
    }

    public long getStorageBytes() {
        ResourceLocation id = getCraftingBlockId();
        if (AEBlockIds.CRAFTING_STORAGE_1K.equals(id)) {
            return KILOBYTE;
        } else if (AEBlockIds.CRAFTING_STORAGE_4K.equals(id)) {
            return 4 * KILOBYTE;
        } else if (AEBlockIds.CRAFTING_STORAGE_16K.equals(id)) {
            return 16 * KILOBYTE;
        } else if (AEBlockIds.CRAFTING_STORAGE_64K.equals(id)) {
            return 64 * KILOBYTE;
        } else if (AEBlockIds.CRAFTING_STORAGE_256K.equals(id)) {
            return 256 * KILOBYTE;
        }
        return 0;
    }

    public int getAcceleratorThreads() {
        return AEBlockIds.CRAFTING_ACCELERATOR.equals(getCraftingBlockId()) ? 1 : 0;
    }

    @Override
    public void onReady() {
        super.onReady();
        this.getMainNode().setVisualRepresentation(this.getItemFromTile());
        if (this.world != null && !this.world.isRemote) {
            this.calc.calculateMultiblock(this.world, this.pos);
            this.recalculateDisplay();
        }
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);

        ClientState state = this.getRenderState();
        data.writeBoolean(state.formed());
        data.writeBoolean(state.powered());
        data.writeByte(encodeConnections(state.connections()));
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        boolean changed = super.readFromStream(data);

        ClientState nextState = new ClientState(data.readBoolean(), data.readBoolean(),
            decodeConnections(data.readUnsignedByte()));
        changed |= !nextState.equals(this.clientState);
        this.clientState = nextState;
        return changed;
    }

    public void updateMultiBlock(BlockPos changedPos) {
        if (this.world != null && !this.world.isRemote) {
            this.calc.updateMultiblockAfterNeighborUpdate(this.world, this.pos, changedPos);
        }
    }

    public void updateStatus(@Nullable CraftingCPUCluster cluster) {
        if (this.cluster != null && this.cluster != cluster) {
            this.cluster.breakCluster();
        }

        this.cluster = cluster;
        this.updateSubType(true);
    }

    @Override
    protected void saveVisualState(NBTTagCompound data) {
        super.saveVisualState(data);

        ClientState state = this.getRenderState();
        data.setBoolean("formed", state.formed());
        data.setBoolean("powered", state.powered());
        data.setByte("connections", (byte) encodeConnections(state.connections()));
    }

    private IBlockState setBooleanProperty(IBlockState state, String propertyName, boolean value) {
        for (IProperty<?> property : state.getPropertyKeys()) {
            if (property instanceof PropertyBool boolProperty && property.getName().equals(propertyName)) {
                return state.withProperty(boolProperty, value);
            }
        }
        return state;
    }

    @Override
    public Set<EnumFacing> getGridConnectableSides(BlockOrientation orientation) {
        if (isFormed()) {
            return EnumSet.allOf(EnumFacing.class);
        }
        return EnumSet.noneOf(EnumFacing.class);
    }

    @Override
    protected void loadVisualState(NBTTagCompound data) {
        super.loadVisualState(data);

        this.clientState = new ClientState(data.getBoolean("formed"), data.getBoolean("powered"),
            decodeConnections(data.getByte("connections") & 0xFF));
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        data.setBoolean("core", this.isCoreBlock());
        if (this.isCoreBlock() && this.cluster != null) {
            this.cluster.writeToNBT(data);
        }
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.setCoreBlock(data.getBoolean("core"));
        if (this.isCoreBlock()) {
            if (this.cluster != null) {
                this.cluster.readFromNBT(data);
            } else {
                this.setPreviousState(data.copy());
            }
        }
    }

    @Override
    public void disconnect(boolean update) {
        if (this.cluster != null) {
            this.cluster.destroy();
            if (update) {
                this.updateSubType(true);
            }
        }
    }

    @Override
    public CraftingCPUCluster getCluster() {
        return this.cluster;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (reason != IGridNodeListener.State.GRID_BOOT) {
            this.updateSubType(false);
        }
    }

    public void breakCluster() {
        if (this.cluster == null) {
            return;
        }

        this.cluster.cancelJob();
        var inv = this.cluster.craftingLogic.getInventory();

        if (this.world == null) {
            inv.clear();
            this.cluster.destroy();
            return;
        }

        var places = new ObjectArrayList<BlockPos>();
        Iterator<TileCraftingUnit> it = this.cluster.getBlockEntities();
        while (it.hasNext()) {
            TileCraftingUnit blockEntity = it.next();
            if (this == blockEntity) {
                places.add(this.pos);
            } else {
                for (EnumFacing facing : EnumFacing.values()) {
                    BlockPos place = blockEntity.pos.offset(facing);
                    if (this.world.isAirBlock(place)) {
                        places.add(place);
                    }
                }
            }
        }

        if (places.isEmpty()) {
            throw new IllegalStateException(this.cluster + " does not contain any kind of blocks, which were destroyed.");
        }

        for (var entry : inv.list) {
            var position = places.get(this.world.rand.nextInt(places.size()));
            var stacks = new ObjectArrayList<ItemStack>();
            entry.getKey().addDrops(entry.getLongValue(), stacks, this.world, position);
            Platform.spawnDrops(this.world, position, stacks);
        }

        inv.clear();
        this.cluster.destroy();
    }

    public void updateSubType(boolean updateFormed) {
        if (this.world == null || this.isInvalid()) {
            return;
        }

        final boolean formed = this.isFormed();
        boolean power = this.getMainNode().isOnline();

        final IBlockState current = this.world.getBlockState(this.pos);
        IBlockState newState = setBooleanProperty(current, "powered", power);
        newState = setBooleanProperty(newState, "formed", formed);

        if (current != newState) {
            this.world.setBlockState(this.pos, newState, 2);
        }

        if (updateFormed) {
            onGridConnectableSidesChanged();
        }

        this.recalculateDisplay();
    }

    @Override
    public boolean isActive() {
        if (this.world == null || !this.world.isRemote) {
            return this.getMainNode().isActive();
        }
        return this.isPowered() && this.isFormed();
    }

    public boolean isCoreBlock() {
        return this.coreBlock;
    }

    public void setCoreBlock(boolean coreBlock) {
        this.coreBlock = coreBlock;
    }

    @Nullable
    public NBTTagCompound getPreviousState() {
        return this.previousState;
    }

    public void setPreviousState(@Nullable NBTTagCompound previousState) {
        this.previousState = previousState;
    }

    public boolean isFormed() {
        if (this.world != null && this.world.isRemote) {
            return this.clientState.formed();
        }
        return this.cluster != null;
    }

    @Override
    public boolean isPowered() {
        if (this.world != null && this.world.isRemote) {
            return this.clientState.powered();
        }
        return this.getMainNode().isActive();
    }

    public ClientState getRenderState() {
        if (this.world != null && !this.world.isRemote) {
            return this.createRenderState();
        }
        return this.clientState;
    }

    private void recalculateDisplay() {
        if (this.world == null || this.world.isRemote) {
            return;
        }

        ClientState nextState = this.createRenderState();
        if (!nextState.equals(this.clientState)) {
            this.clientState = nextState;
            this.markForUpdate();
        }
    }

    private ClientState createRenderState() {
        return new ClientState(this.isFormed(), this.isPowered(), this.getConnections());
    }

    private EnumSet<EnumFacing> getConnections() {
        EnumSet<EnumFacing> connections = EnumSet.noneOf(EnumFacing.class);
        if (this.world == null) {
            return connections;
        }

        for (EnumFacing facing : EnumFacing.values()) {
            if (this.world.getBlockState(this.pos.offset(facing)).getBlock() instanceof AbstractCraftingUnitBlock) {
                connections.add(facing);
            }
        }
        return connections;
    }

    private Iterator<IGridNode> getMultiblockNodes() {
        if (this.getCluster() == null) {
            return Collections.emptyIterator();
        }

        List<IGridNode> nodes = new ObjectArrayList<>();
        Iterator<TileCraftingUnit> it = this.getCluster().getBlockEntities();
        while (it.hasNext()) {
            var node = it.next().getGridNode();
            if (node != null) {
                nodes.add(node);
            }
        }

        return nodes.iterator();
    }

    @Override
    public IConfigManager getConfigManager() {
        var cluster = this.getCluster();
        if (cluster != null) {
            return cluster.getConfigManager();
        }
        return NullConfigManager.INSTANCE;
    }

    public record ClientState(boolean formed, boolean powered, EnumSet<EnumFacing> connections) {
        public static final ClientState DEFAULT = new ClientState(false, false, EnumSet.noneOf(EnumFacing.class));

        public ClientState {
            connections = connections.clone();
        }
    }
}
