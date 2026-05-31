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

package ae2.parts.reporting;

import ae2.api.behaviors.ContainerItemStrategies;
import ae2.api.implementations.parts.IStorageMonitorPart;
import ae2.api.networking.IGrid;
import ae2.api.networking.IStackWatcher;
import ae2.api.networking.crafting.ICraftingWatcherNode;
import ae2.api.networking.storage.IStorageWatcherNode;
import ae2.api.orientation.BlockOrientation;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AmountFormat;
import ae2.client.render.BlockEntityRenderHelper;
import ae2.core.localization.PlayerMessages;
import ae2.util.InteractionUtil;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractMonitorPart extends AbstractDisplayPart implements IStorageMonitorPart {

    @Nullable
    private AEKey configuredItem;
    private long amount;
    private boolean canCraft;
    private String lastHumanReadableText;
    private boolean locked;
    private IStackWatcher storageWatcher;
    private IStackWatcher craftingWatcher;

    public AbstractMonitorPart(IPartItem<?> partItem, boolean requireChannel) {
        super(partItem, requireChannel);

        getMainNode().addService(IStorageWatcherNode.class, new IStorageWatcherNode() {
            @Override
            public void updateWatcher(IStackWatcher newWatcher) {
                storageWatcher = newWatcher;
                configureWatchers();
            }

            @Override
            public void onStackChange(AEKey what, long amount) {
                if (what.equals(configuredItem)) {
                    AbstractMonitorPart.this.amount = amount;

                    var humanReadableText = amount == 0 && canCraft ? "Craft"
                        : what.formatAmount(amount, AmountFormat.SLOT);

                    if (!humanReadableText.equals(lastHumanReadableText)) {
                        lastHumanReadableText = humanReadableText;
                        getHost().markForUpdate();
                    }
                }
            }
        });

        getMainNode().addService(ICraftingWatcherNode.class, new ICraftingWatcherNode() {
            @Override
            public void updateWatcher(IStackWatcher newWatcher) {
                craftingWatcher = newWatcher;
                configureWatchers();
            }

            @Override
            public void onRequestChange(AEKey what) {
            }

            @Override
            public void onCraftableChange(AEKey what) {
                getMainNode().ifPresent(AbstractMonitorPart.this::updateReportingValue);
            }
        });
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.locked = data.getBoolean("isLocked");

        if (data.hasKey("configuredItem", 10)) {
            this.configuredItem = AEKey.fromTagGeneric(data.getCompoundTag("configuredItem"));
        } else {
            this.configuredItem = null;
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("isLocked", this.locked);

        if (this.configuredItem != null) {
            data.setTag("configuredItem", this.configuredItem.toTagGeneric());
        }
    }

    @Override
    public void writeToStream(PacketBuffer data) {
        super.writeToStream(data);
        data.writeBoolean(this.locked);
        AEKey.writeOptionalKey(data, this.configuredItem);
        if (this.configuredItem != null) {
            data.writeVarLong(this.amount);
            data.writeBoolean(this.canCraft);
        }
    }

    @Override
    public boolean readFromStream(PacketBuffer data) {
        boolean needRedraw = super.readFromStream(data);

        boolean locked = data.readBoolean();
        needRedraw |= this.locked != locked;
        this.locked = locked;

        this.configuredItem = AEKey.readOptionalKey(data);
        if (this.configuredItem != null) {
            this.amount = data.readVarLong();
            this.canCraft = data.readBoolean();
        } else {
            this.amount = 0;
            this.canCraft = false;
        }

        return needRedraw;
    }

    @Override
    public void writeVisualStateToNBT(NBTTagCompound data) {
        super.writeVisualStateToNBT(data);
        data.setLong("amount", this.amount);
        data.setBoolean("canCraft", this.canCraft);
    }

    @Override
    public void readVisualStateFromNBT(NBTTagCompound data) {
        super.readVisualStateFromNBT(data);
        this.amount = data.getLong("amount");
        this.canCraft = data.getBoolean("canCraft");
    }

    @Override
    public boolean onUseWithoutItem(EntityPlayer player, Vec3d pos) {
        if (InteractionUtil.isInAlternateUseMode(player)) {
            if (isClientSide()) {
                return true;
            }

            if (!this.getMainNode().isActive()) {
                return false;
            }

            this.locked = !this.locked;
            player.sendStatusMessage((this.locked ? PlayerMessages.isNowLocked : PlayerMessages.isNowUnlocked).text(),
                true);
            this.getHost().markForSave();
            this.getHost().markForUpdate();
            return true;
        }

        return super.onUseWithoutItem(player, pos);
    }

    @Override
    public boolean onUseItemOn(ItemStack heldItem, EntityPlayer player, net.minecraft.util.EnumHand hand, Vec3d pos) {
        if (!this.locked && !InteractionUtil.isInAlternateUseMode(player)) {
            if (isClientSide()) {
                return true;
            }

            if (!this.getMainNode().isActive()) {
                return false;
            }

            if (AEItemKey.matches(this.configuredItem, heldItem)) {
                var containedStack = ContainerItemStrategies.getContainedStack(heldItem);
                if (containedStack != null) {
                    this.configuredItem = containedStack.what();
                }
            } else {
                this.configuredItem = AEItemKey.of(heldItem);
            }

            this.configureWatchers();
            this.getHost().markForSave();
            this.getHost().markForUpdate();
            return true;
        }

        return super.onUseItemOn(heldItem, player, hand, pos);
    }

    protected void configureWatchers() {
        if (this.storageWatcher != null) {
            this.storageWatcher.reset();
        }

        if (this.craftingWatcher != null) {
            this.craftingWatcher.reset();
        }

        if (this.configuredItem != null) {
            if (this.storageWatcher != null) {
                this.storageWatcher.add(this.configuredItem);
            }

            if (this.craftingWatcher != null) {
                this.craftingWatcher.add(this.configuredItem);
            }

            getMainNode().ifPresent(this::updateReportingValue);
        }
    }

    protected void updateReportingValue(IGrid grid) {
        this.lastHumanReadableText = null;
        if (this.configuredItem != null) {
            this.amount = grid.getStorageService().getCachedInventory().get(this.configuredItem);
            this.canCraft = grid.getCraftingService().isCraftable(this.configuredItem);
        } else {
            this.amount = 0;
            this.canCraft = false;
        }
        getHost().markForUpdate();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderDynamic(double x, double y, double z, float partialTicks, int destroyStage) {
        if (!isActive()) {
            return;
        }

        if (this.configuredItem == null) {
            return;
        }

        EnumFacing side = this.getSide();
        if (side == null) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);
        BlockEntityRenderHelper.rotateToFace(BlockOrientation.get(side, this.getSpin()));
        GlStateManager.translate(0, 0.05, 0.5);
        BlockEntityRenderHelper.renderItem2dWithAmount(this.configuredItem, this.amount, this.canCraft,
            0.4f, -0.23f, getColor().contrastTextColor);
        GlStateManager.popMatrix();
    }

    @Override
    public boolean requireDynamicRender() {
        return true;
    }

    @Nullable
    @Override
    public AEKey getDisplayed() {
        return this.configuredItem;
    }

    public void setConfiguredItem(@Nullable AEKey configuredItem) {
        this.configuredItem = configuredItem;
        getHost().markForUpdate();
    }

    @Override
    public long getAmount() {
        return this.amount;
    }

    public boolean canCraft() {
        return this.canCraft;
    }

    @Override
    public boolean isLocked() {
        return this.locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
        getHost().markForUpdate();
    }

    @Override
    public boolean showNetworkInfo(EntityPlayer player, World world, BlockPos pos, EnumHand hand, ItemStack stack,
                                   RayTraceResult hit) {
        return false;
    }

    protected IPartModel selectModel(IPartModel off, IPartModel on, IPartModel hasChannel, IPartModel lockedOff,
                                     IPartModel lockedOn, IPartModel lockedHasChannel) {
        if (this.isActive()) {
            return this.isLocked() ? lockedHasChannel : hasChannel;
        } else if (this.isPowered()) {
            return this.isLocked() ? lockedOn : on;
        } else if (this.isLocked()) {
            return lockedOff;
        } else {
            return off;
        }
    }
}
