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

package ae2.parts;

import ae2.api.implementations.IPowerChannelState;
import ae2.api.implementations.items.IMemoryCard;
import ae2.api.implementations.items.MemoryCardMessages;
import ae2.api.inventories.ISegmentedInventory;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.security.IActionHost;
import ae2.api.parts.IPart;
import ae2.api.parts.IPartHost;
import ae2.api.parts.IPartItem;
import ae2.api.stacks.AEItemKey;
import ae2.api.util.AECableType;
import ae2.api.util.AEColor;
import ae2.api.util.ICustomName;
import ae2.container.GuiIds;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEParts;
import ae2.core.gui.GuiOpener;
import ae2.items.tools.MemoryCardItem;
import ae2.items.tools.quartz.QuartzCuttingKnifeItem;
import ae2.util.CustomNameUtil;
import ae2.util.Platform;
import ae2.util.SettingsFrom;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Objects;

public abstract class AEBasePart implements IPart, IActionHost, ISegmentedInventory, IPowerChannelState, ICustomName {

    private static final String VISUAL_STATE_TAG = "visual";
    private static final String POWERED_TAG = "powered";
    private static final String MISSING_CHANNEL_TAG = "missingChannel";
    private static final String MEMORY_CARD_SETTINGS_TAG = "exported_settings";
    private static final String MEMORY_CARD_SOURCE_TAG = "exported_settings_source";
    private static final String MEMORY_CARD_CUSTOM_NAME_TAG = "exported_custom_name";

    private final IManagedGridNode mainNode;
    private IPartItem<?> partItem;
    private TileEntity tileEntity;
    private IPartHost host;
    @Nullable
    private EnumFacing side;
    @Nullable
    private String customName;
    private boolean clientSidePowered;
    private boolean clientSideMissingChannel;

    public AEBasePart(IPartItem<?> partItem) {
        this.partItem = Objects.requireNonNull(partItem, "partItem");
        this.mainNode = createMainNode()
            .setVisualRepresentation(AEItemKey.of(this.partItem.asItem()))
            .setExposedOnSides(EnumSet.noneOf(EnumFacing.class));
    }

    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, NodeListener.INSTANCE);
    }

    @MustBeInvokedByOverriders
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (reason != IGridNodeListener.State.GRID_BOOT) {
            markForUpdateIfClientFlagsChanged();
        } else if (this.mainNode.hasGridBooted() && this.getLightLevel() > 0 && this.host != null) {
            this.host.markForUpdate();
        }
    }

    public final boolean isClientSide() {
        return this.tileEntity == null || this.tileEntity.getWorld() == null || this.tileEntity.getWorld().isRemote;
    }

    public final IManagedGridNode getMainNode() {
        return this.mainNode;
    }

    public final IPartHost getHost() {
        return this.host;
    }

    public final TileEntity getTileEntity() {
        return this.tileEntity;
    }

    public final World getLevel() {
        return this.tileEntity.getWorld();
    }

    public final @Nullable EnumFacing getSide() {
        return this.side;
    }

    protected AEColor getColor() {
        if (this.host == null) {
            return AEColor.TRANSPARENT;
        }
        return this.host.getColor();
    }

    @Override
    public final IPartItem<?> getPartItem() {
        return this.partItem;
    }

    protected void setPartItem(IPartItem<?> partItem) {
        if (partItem != this.partItem) {
            this.partItem = Objects.requireNonNull(partItem, "partItem");
            this.mainNode.setVisualRepresentation(AEItemKey.of(partItem.asItem()));
        }
    }

    @Override
    public IGridNode getActionableNode() {
        return this.mainNode.getNode();
    }

    @Override
    public IGridNode getGridNode() {
        return this.mainNode.getNode();
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        this.mainNode.loadFromNBT(data);

        if (data.hasKey(CustomNameUtil.CUSTOM_NAME_TAG, Constants.NBT.TAG_STRING)) {
            this.setCustomName(data.getString(CustomNameUtil.CUSTOM_NAME_TAG));
        } else {
            this.customName = null;
        }

        if (data.hasKey(VISUAL_STATE_TAG, Constants.NBT.TAG_COMPOUND)) {
            readVisualStateFromNBT(data.getCompoundTag(VISUAL_STATE_TAG));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        this.mainNode.saveToNBT(data);

        if (this.customName != null) {
            data.setString(CustomNameUtil.CUSTOM_NAME_TAG, this.customName);
        }
    }

    @MustBeInvokedByOverriders
    @Override
    public void writeToStream(PacketBuffer data) {
        this.clientSidePowered = this.isPowered();
        this.clientSideMissingChannel = this.isMissingChannel();

        int flags = 0;
        if (this.clientSidePowered) {
            flags |= 1;
        }
        if (this.clientSideMissingChannel) {
            flags |= 2;
        }
        data.writeByte(flags);
        CustomNameUtil.writeNullableString(data, this.customName);
    }

    @MustBeInvokedByOverriders
    @Override
    public boolean readFromStream(PacketBuffer data) {
        int flags = data.readUnsignedByte();

        boolean wasPowered = this.clientSidePowered;
        boolean wasMissingChannel = this.clientSideMissingChannel;
        String oldCustomName = this.customName;

        this.clientSidePowered = (flags & 1) != 0;
        this.clientSideMissingChannel = (flags & 2) != 0;
        this.customName = CustomNameUtil.readNullableString(data);

        return shouldSendPowerStateToClient() && this.clientSidePowered != wasPowered
            || shouldSendMissingChannelStateToClient() && this.clientSideMissingChannel != wasMissingChannel
            || !Objects.equals(this.customName, oldCustomName);
    }

    @MustBeInvokedByOverriders
    @Override
    public void writeVisualStateToNBT(NBTTagCompound data) {
        data.setBoolean(POWERED_TAG, this.isPowered());
        data.setBoolean(MISSING_CHANNEL_TAG, this.isMissingChannel());
    }

    @MustBeInvokedByOverriders
    @Override
    public void readVisualStateFromNBT(NBTTagCompound data) {
        this.clientSidePowered = data.getBoolean(POWERED_TAG);
        this.clientSideMissingChannel = data.getBoolean(MISSING_CHANNEL_TAG);
    }

    @Override
    public void removeFromWorld() {
        this.mainNode.destroy();
    }

    @Override
    public void addToWorld() {
        this.mainNode.create(getLevel(), this.tileEntity.getPos());
    }

    @Override
    public void setPartHostInfo(@Nullable EnumFacing side, IPartHost host, TileEntity blockEntity) {
        this.side = side;
        this.tileEntity = blockEntity;
        this.host = host;
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 3;
    }

    @Override
    @MustBeInvokedByOverriders
    public void importSettings(SettingsFrom mode, NBTTagCompound input, @Nullable EntityPlayer player) {
        if (mode == SettingsFrom.DISMANTLE_ITEM && input.hasKey(CustomNameUtil.CUSTOM_NAME_TAG, 8)) {
            setCustomName(input.getString(CustomNameUtil.CUSTOM_NAME_TAG));
        } else if (mode == SettingsFrom.MEMORY_CARD && input.hasKey(MEMORY_CARD_CUSTOM_NAME_TAG, 8)) {
            setCustomName(input.getString(MEMORY_CARD_CUSTOM_NAME_TAG));
        }

        MemoryCardItem.importGenericSettings(this, input, player);
    }

    @Override
    @MustBeInvokedByOverriders
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

    public boolean useStandardMemoryCard() {
        return true;
    }

    @Override
    public boolean onUseItemOn(ItemStack heldItem, EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (useMemoryCard(heldItem, player)) {
            return true;
        }
        if (heldItem.getItem() instanceof QuartzCuttingKnifeItem) {
            if (!isClientSide()) {
                GuiOpener.openPartGui(player, GuiIds.GuiKey.RENAMER, this);
            }
            return true;
        }
        return IPart.super.onUseItemOn(heldItem, player, hand, pos);
    }

    @Override
    public void onPlacement(EntityPlayer player) {
        this.mainNode.setOwningPlayer(player);
    }

    @Nullable
    @Override
    @MustBeInvokedByOverriders
    public InternalInventory getSubInventory(ResourceLocation id) {
        return null;
    }

    @Override
    public void addEntityCrashInfo(CrashReportCategory section) {
        section.addCrashSection("Part Side", this.side);
        if (this.tileEntity != null) {
            this.tileEntity.addInfoToCrashReport(section);
        }
    }

    public boolean isPowered() {
        if (isClientSide()) {
            return this.clientSidePowered;
        }

        IGridNode node = getGridNode();
        return node != null && node.isPowered();
    }

    public boolean isMissingChannel() {
        if (isClientSide()) {
            return this.clientSideMissingChannel;
        }

        IGridNode node = getGridNode();
        return node == null || !node.meetsChannelRequirements();
    }

    @Override
    public boolean isActive() {
        return isPowered() && !isMissingChannel();
    }

    public String getName() {
        ItemStack stack = this.partItem.asItemStack();
        return this.customName != null ? this.customName : stack.getTranslationKey();
    }

    public String getDisplayName() {
        if (this.customName != null) {
            return this.customName;
        }

        ItemStack stack = this.partItem.asItemStack();
        return stack.getDisplayName();
    }

    @Nullable
    public String getCustomName() {
        return this.customName;
    }

    public void setCustomName(@Nullable String customName) {
        this.customName = customName == null || customName.isEmpty() ? null : customName;
    }

    @Override
    public void onCustomNameChanged() {
        if (this.host != null) {
            this.host.markForSave();
            this.host.markForUpdate();
        }
    }

    public boolean hasCustomName() {
        return this.customName != null;
    }

    protected boolean shouldSendPowerStateToClient() {
        return true;
    }

    protected boolean shouldSendMissingChannelStateToClient() {
        return true;
    }

    private boolean useMemoryCard(ItemStack memoryCardStack, EntityPlayer player) {
        if (!this.useStandardMemoryCard() || !(memoryCardStack.getItem() instanceof IMemoryCard memoryCard)) {
            return false;
        }

        Item partItem = getSettingsSourceItem();
        String sourceId = getSettingsSourceName(partItem);
        NBTTagCompound settings = exportSettings(SettingsFrom.MEMORY_CARD);

        if (player.isSneaking()) {
            MemoryCardItem.clearCard(memoryCardStack);
            if (!Platform.isNbtEmpty(settings)) {
                NBTTagCompound tag = Platform.openNbtData(memoryCardStack);
                tag.setTag(MEMORY_CARD_SETTINGS_TAG, settings);
                tag.setString(MEMORY_CARD_SOURCE_TAG, sourceId);
                memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
            }
            return true;
        }

        NBTTagCompound tag = memoryCardStack.getTagCompound();
        if (tag == null || !tag.hasKey(MEMORY_CARD_SETTINGS_TAG, 10)) {
            return true;
        }

        if (sourceId.equals(tag.getString(MEMORY_CARD_SOURCE_TAG))) {
            importSettings(SettingsFrom.MEMORY_CARD, tag.getCompoundTag(MEMORY_CARD_SETTINGS_TAG), player);
            memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
        } else {
            MemoryCardItem.importGenericSettingsAndNotify(this, tag.getCompoundTag(MEMORY_CARD_SETTINGS_TAG), player);
        }

        return true;
    }

    private Item getSettingsSourceItem() {
        Item partItem = this.partItem.asItem();
        if (AEParts.INTERFACE.item() == partItem && AEBlocks.INTERFACE.item() != null) {
            return AEBlocks.INTERFACE.item();
        }
        if (AEParts.PATTERN_PROVIDER.item() == partItem && AEBlocks.PATTERN_PROVIDER.item() != null) {
            return AEBlocks.PATTERN_PROVIDER.item();
        }
        return partItem;
    }

    private String getSettingsSourceName(Item item) {
        return new ItemStack(item).getTranslationKey() + ".name";
    }

    private void markForUpdateIfClientFlagsChanged() {
        boolean changed = shouldSendPowerStateToClient() && isPowered() != this.clientSidePowered;

        if (!changed && shouldSendMissingChannelStateToClient()
            && isMissingChannel() != this.clientSideMissingChannel) {
            changed = true;
        }

        if (changed && this.host != null) {
            this.host.markForUpdate();
        }
    }

    public static class NodeListener<T extends AEBasePart> implements IGridNodeListener<T> {

        public static final NodeListener<AEBasePart> INSTANCE = new NodeListener<>();

        @Override
        public void onSaveChanges(T nodeOwner, IGridNode node) {
            if (nodeOwner.getHost() != null) {
                nodeOwner.getHost().markForSave();
            }
        }

        @Override
        public void onStateChanged(T nodeOwner, IGridNode node, State state) {
            nodeOwner.onMainNodeStateChanged(state);
        }
    }
}
