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

package ae2.tile;

import ae2.api.AECapabilities;
import ae2.api.behaviors.GenericInternalInventory;
import ae2.api.behaviors.GenericInternalInventoryAdapters;
import ae2.api.networking.GridHelper;
import ae2.api.orientation.BlockOrientation;
import ae2.api.orientation.IOrientableBlock;
import ae2.api.orientation.IOrientationStrategy;
import ae2.api.orientation.RelativeSide;
import ae2.api.util.ICustomName;
import ae2.block.AEBaseTileBlock;
import ae2.core.AELog;
import ae2.items.tools.MemoryCardItem;
import ae2.util.CustomNameUtil;
import ae2.util.Platform;
import ae2.util.SettingsFrom;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.Nullable;


import java.util.List;
import java.util.Objects;

public class AEBaseTile extends TileEntity implements ITickable, ICustomName {

    private static final String MEMORY_CARD_CUSTOM_NAME_TAG = "exported_custom_name";
    private static final String FORWARD_TAG = "forward";
    private static final String UP_TAG = "up";

    @Nullable
    private String customName;
    private boolean markDirtyQueued = false;
    private byte queuedForReady = 0;
    private byte readyInvoked = 0;
    private boolean readyScheduled = false;
    private EnumFacing forward = EnumFacing.NORTH;
    private EnumFacing up = EnumFacing.UP;
    private boolean orientationResolved = false;
    private BlockOrientation lastOrientation = BlockOrientation.NORTH_UP;
    private boolean orientationInitialized = false;
    private boolean pendingVisualStateUpdate = false;
    private boolean init = false;

    @Override
    public final void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);

        boolean changed = this.readUpdateData(compound, "Failed to read #upd payload");

        this.loadTag(compound);

        if (changed) {
            queueVisualStateUpdate();
        }
    }

    @Override
    public final NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        this.saveAdditional(compound);
        return compound;
    }

    public void loadTag(NBTTagCompound data) {
        if (data.hasKey(FORWARD_TAG, 8) && data.hasKey(UP_TAG, 8)) {
            try {
                this.setOrientationInternal(EnumFacing.valueOf(data.getString(FORWARD_TAG)),
                    EnumFacing.valueOf(data.getString(UP_TAG)));
                this.orientationResolved = true;
            } catch (IllegalArgumentException ignored) {
                this.forward = EnumFacing.NORTH;
                this.up = EnumFacing.UP;
                this.orientationResolved = false;
            }
        } else {
            this.orientationResolved = false;
        }

        if (data.hasKey("visual", 10)) {
            this.loadVisualState(data.getCompoundTag("visual"));
        }

        if (data.hasKey(CustomNameUtil.CUSTOM_NAME_TAG, 8)) {
            this.setCustomName(data.getString(CustomNameUtil.CUSTOM_NAME_TAG));
        } else {
            this.customName = null;
        }
    }

    public void saveAdditional(NBTTagCompound data) {
        if (this.canBeRotated()) {
            data.setString(FORWARD_TAG, this.getForward().name());
            data.setString(UP_TAG, this.getUp().name());
        }

        NBTTagCompound visualData = new NBTTagCompound();
        this.saveVisualState(visualData);
        if (!visualData.isEmpty()) {
            data.setTag("visual", visualData);
        }

        if (this.customName != null) {
            data.setString(CustomNameUtil.CUSTOM_NAME_TAG, this.customName);
        }
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound data = super.getUpdateTag();
        ByteBuf buf = Unpooled.buffer();
        try {
            this.writeToStream(buf);
        } catch (Throwable t) {
            AELog.error(t, "Failed to write update tag");
        }
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        data.setByteArray("#upd", bytes);
        return data;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        if (tag.hasKey("#upd")) {
            if (this.readUpdateData(tag, "Failed to handle update tag")) {
                this.queueVisualStateUpdate();
            }
            return;
        }

        super.readFromNBT(tag);
        this.loadTag(tag);
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        NBTTagCompound tag = new NBTTagCompound();
        ByteBuf buf = Unpooled.buffer();
        try {
            this.writeToStream(buf);
        } catch (Throwable t) {
            AELog.error(t, "Failed to write update packet");
        }
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        tag.setByteArray("#upd", bytes);
        return new SPacketUpdateTileEntity(this.pos, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        NBTTagCompound tag = pkt.getNbtCompound();
        if (this.readUpdateData(tag, "Failed to handle update packet")) {
            this.queueVisualStateUpdate();
        }
    }

    protected void writeToStream(ByteBuf data) {
        CustomNameUtil.writeNullableString(data, this.customName);
        if (this.canBeRotated()) {
            data.writeByte((this.getUp().ordinal() << 3) | this.getForward().ordinal());
        }
    }

    private static EnumFacing readPackedFacing(int ordinal) {
        if (ordinal >= 0 && ordinal < EnumFacing.VALUES.length) {
            return EnumFacing.VALUES[ordinal];
        }
        return null;
    }

    protected boolean readFromStream(ByteBuf data) {
        boolean init = this.init;
        this.init = false;
        String oldCustomName = this.customName;
        this.customName = CustomNameUtil.readNullableString(data);
        boolean changed = !Objects.equals(this.customName, oldCustomName);

        if (!this.canBeRotated()) {
            return changed;
        }

        if (data.readableBytes() < 1) {
            return changed;
        }
        byte orientation = data.readByte();
        int forwardOrdinal = orientation & 0x7;
        int upOrdinal = (orientation >> 3) & 0x7;
        EnumFacing newForward = readPackedFacing(forwardOrdinal);
        EnumFacing newUp = readPackedFacing(upOrdinal);
        if (newForward == null || newUp == null) {
            return changed;
        }
        this.orientationResolved = true;
        return !init || (changed | this.setOrientationInternal(newForward, newUp));
    }

    private boolean readUpdateData(NBTTagCompound tag, String failureMessage) {
        if (!tag.hasKey("#upd")) {
            return false;
        }

        byte[] payload = tag.getByteArray("#upd");
        try {
            return this.readFromStream(Unpooled.wrappedBuffer(payload));
        } catch (Throwable t) {
            AELog.error(t, failureMessage);
            return false;
        }
    }

    protected void saveVisualState(NBTTagCompound data) {
    }

    protected void loadVisualState(NBTTagCompound data) {
    }

    protected void onVisualStateUpdated() {
        if (this.world != null) {
            IBlockState state = this.getBlockState();
            if (state != null) {
                this.world.notifyBlockUpdate(this.pos, state, state, 3);
            }
            this.world.markBlockRangeForRenderUpdate(this.pos, this.pos);
        }
    }

    private void queueVisualStateUpdate() {
        if (this.world != null && this.world.isRemote) {
            this.onVisualStateUpdated();
        } else {
            this.pendingVisualStateUpdate = true;
        }
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return newState.getBlock() != oldState.getBlock();
    }

    public void onReady() {
        this.readyInvoked++;
    }

    protected void scheduleInit() {
        if (this.readyScheduled) {
            return;
        }
        this.readyScheduled = true;
        this.queuedForReady++;
        if (this.world != null && !this.world.isRemote) {
            GridHelper.onFirstTick(this, AEBaseTile::runReady);
        }
    }

    private void runReady() {
        if (!this.readyScheduled) {
            return;
        }
        this.readyScheduled = false;
        this.onReady();
    }

    @Nullable
    public String getCustomName() {
        return this.customName;
    }

    public void setCustomName(@Nullable String name) {
        this.customName = (name == null || name.isEmpty()) ? null : name;
    }

    @Override
    public void onCustomNameChanged() {
        saveChanges();
        markForUpdate();
    }

    public boolean hasCustomName() {
        return this.customName != null;
    }

    public void importSettings(SettingsFrom mode, NBTTagCompound input, @Nullable EntityPlayer player) {
        if (mode == SettingsFrom.DISMANTLE_ITEM && input.hasKey(CustomNameUtil.CUSTOM_NAME_TAG, 8)) {
            setCustomName(input.getString(CustomNameUtil.CUSTOM_NAME_TAG));
        } else if (mode == SettingsFrom.MEMORY_CARD && input.hasKey(MEMORY_CARD_CUSTOM_NAME_TAG, 8)) {
            setCustomName(input.getString(MEMORY_CARD_CUSTOM_NAME_TAG));
        }

        MemoryCardItem.importGenericSettings(this, input, player);
    }

    public void exportSettings(SettingsFrom mode, NBTTagCompound output) {
        if (mode == SettingsFrom.DISMANTLE_ITEM) {
            if (this.customName != null) {
                output.setString(CustomNameUtil.CUSTOM_NAME_TAG, this.customName);
            }
        } else if (mode == SettingsFrom.MEMORY_CARD) {
            if (this.customName != null) {
                output.setString(MEMORY_CARD_CUSTOM_NAME_TAG, this.customName);
            }
            MemoryCardItem.exportGenericSettings(this, output);
        }
    }

    public final NBTTagCompound exportSettings(SettingsFrom mode) {
        NBTTagCompound output = new NBTTagCompound();
        exportSettings(mode, output);
        return output;
    }

    public void addAdditionalDrops(List<ItemStack> drops) {
    }

    public void clearContent() {
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability != AECapabilities.GENERIC_INTERNAL_INV
            && GenericInternalInventoryAdapters.hasAdapter(capability)
            && this.getGenericInternalInventoryForAdapter(facing) != null
            || super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability != AECapabilities.GENERIC_INTERNAL_INV) {
            GenericInternalInventory genericInv = this.getGenericInternalInventoryForAdapter(facing);
            if (genericInv == null) {
                return super.getCapability(capability, facing);
            }
            T result = GenericInternalInventoryAdapters.getCapability(genericInv, capability);
            if (result != null) {
                return result;
            }
        }
        return super.getCapability(capability, facing);
    }

    @Nullable
    private GenericInternalInventory getGenericInternalInventoryForAdapter(@Nullable EnumFacing side) {
        return AECapabilities.GENERIC_INTERNAL_INV.cast(this.getCapability(AECapabilities.GENERIC_INTERNAL_INV, side));
    }

    public boolean canBeRotated() {
        return true;
    }

    public EnumFacing getForward() {
        return this.forward;
    }

    public EnumFacing getUp() {
        return this.up;
    }

    public void syncOrientationFromBlockState(@Nullable IBlockState state) {
        if (state == null) {
            return;
        }

        IOrientationStrategy strategy = IOrientationStrategy.get(state);
        EnumFacing facing = strategy.getFacing(state);
        BlockOrientation orientation = BlockOrientation.get(strategy, state);
        this.orientationResolved = true;
        this.setOrientationInternal(facing, orientation.getSide(RelativeSide.TOP));
    }

    public final void initializeOrientationFromBlockState(IBlockState state) {
        this.syncOrientationFromBlockState(state);
        this.refreshOrientation();
        this.saveChanges();
        this.markForUpdate();
    }

    public IBlockState applyOrientationToBlockState(IBlockState state) {
        if (!(state.getBlock() instanceof IOrientableBlock orientableBlock) || !this.canBeRotated()) {
            return state;
        }

        return orientableBlock.getOrientationStrategy().setOrientation(state, this.getForward(), this.getUp());
    }

    public void setOrientation(EnumFacing forward, EnumFacing up) {
        if (!this.setOrientationInternal(forward, up)) {
            return;
        }

        this.orientationResolved = true;
        this.refreshOrientation();
        this.saveChanges();
        this.markForUpdate();
        Platform.notifyBlocksOfNeighbors(this.world, this.pos);
    }

    public void markForClientUpdate() {
        if (this.world != null) {
            IBlockState state = this.getBlockState();
            if (state != null) {
                this.world.notifyBlockUpdate(this.pos, state, state, 2);
            }
        }
    }

    public void markForUpdate() {
        if (this.world != null && !this.world.isRemote) {
            IBlockState state = this.getBlockState();
            if (state == null) {
                return;
            }

            if (state.getBlock() instanceof AEBaseTileBlock<?> block) {
                IBlockState newState = block.getTileEntityBlockState(state, this);
                if (newState != state && this.world.setBlockState(this.pos, newState, 3)) {
                    return;
                }
            }

            this.world.notifyBlockUpdate(this.pos, state, state, 3);
        }
    }

    protected final void refreshBlockStateAfterReady() {
        IBlockState currentState = this.getBlockState();
        if (currentState != null && currentState.getBlock() instanceof AEBaseTileBlock<?> block) {
            IBlockState newState = block.getTileEntityBlockState(currentState, this);
            if (currentState != newState) {
                if (this.world != null && this.world.isRemote) {
                    this.world.setBlockState(this.pos, newState, 2);
                    this.world.markBlockRangeForRenderUpdate(this.pos, this.pos);
                } else {
                    this.markForUpdate();
                }
            }
        }
    }

    public void saveChanges() {
        if (this.world != null) {
            if (!this.markDirtyQueued) {
                this.markDirtyQueued = true;
                this.markDirty();
                this.markDirtyQueued = false;
            }
        }
    }

    @Override
    public void update() {
        if (this.world == null) {
            return;
        }
        if (this.world.isRemote && this.readyScheduled) {
            this.runReady();
        }
        if (this.world.isRemote) {
            if (this instanceof ClientTickingTile) {
                ((ClientTickingTile) this).clientTick();
            }
        } else {
            if (this instanceof ServerTickingTile) {
                ((ServerTickingTile) this).serverTick();
            }
        }
    }

    public TileEntity getTileEntity() {
        return this;
    }

    @Nullable
    protected IBlockState getBlockState() {
        if (this.world == null) {
            return null;
        }
        if (!this.world.isBlockLoaded(this.pos)) {
            return null;
        }
        return this.world.getBlockState(this.pos);
    }

    public BlockOrientation getOrientation() {
        return BlockOrientation.get(this.getForward(), this.getUp());
    }

    protected void onOrientationChanged(BlockOrientation orientation) {
    }

    protected final void refreshOrientation() {
        BlockOrientation orientation = this.getOrientation();
        if (!this.orientationInitialized || this.lastOrientation != orientation) {
            this.lastOrientation = orientation;
            this.orientationInitialized = true;
            this.onOrientationChanged(orientation);
        }
    }

    public ItemStack getItemFromTile() {
        return ItemStack.EMPTY;
    }

    @Override
    public final void invalidate() {
        super.invalidate();
        this.readyScheduled = false;
        this.setRemoved();
    }

    @Override
    public final void validate() {
        super.validate();
        this.clearRemoved();
        if (!this.orientationResolved) {
            this.syncOrientationFromBlockState(this.getBlockState());
        }
        this.refreshOrientation();
        if (this.world != null) {
            this.refreshBlockStateAfterReady();
        }
        if (this.pendingVisualStateUpdate && this.world != null && this.world.isRemote) {
            this.pendingVisualStateUpdate = false;
            this.onVisualStateUpdated();
        }
    }

    @Override
    public final void onChunkUnload() {
        super.onChunkUnload();
        this.readyScheduled = false;
        this.onChunkUnloaded();
    }

    protected void setRemoved() {
    }

    protected void clearRemoved() {
    }

    protected void onChunkUnloaded() {
    }

    public byte getQueuedForReady() {
        return queuedForReady;
    }

    public byte getReadyInvoked() {
        return readyInvoked;
    }

    @Override
    public void updateContainingBlockInfo() {
        super.updateContainingBlockInfo();
        if (this.world != null && this.world.isBlockLoaded(this.pos)) {
            this.refreshOrientation();
        }
    }

    private boolean setOrientationInternal(EnumFacing forward, EnumFacing up) {
        BlockOrientation orientation = BlockOrientation.get(
            forward == null ? EnumFacing.NORTH : forward,
            up == null ? EnumFacing.UP : up);
        EnumFacing normalizedForward = orientation.getSide(RelativeSide.FRONT);
        EnumFacing normalizedUp = orientation.getSide(RelativeSide.TOP);
        if (this.forward == normalizedForward && this.up == normalizedUp) {
            return false;
        }
        this.forward = normalizedForward;
        this.up = normalizedUp;
        return true;
    }
}
