/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
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

package ae2.parts.automation;

import ae2.api.config.RedstoneMode;
import ae2.api.config.Setting;
import ae2.api.config.Settings;
import ae2.api.networking.IGridNodeListener;
import ae2.api.parts.IPartCollisionHelper;
import ae2.api.parts.IPartItem;
import ae2.api.util.AECableType;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigManagerBuilder;
import ae2.util.Platform;
import ae2.util.SettingsFrom;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

public abstract class AbstractLevelEmitterPart extends UpgradeablePart {
    protected long lastReportedValue;
    private boolean prevState;
    private long reportingValue;

    private boolean clientSideOn;

    public AbstractLevelEmitterPart(IPartItem<?> partItem) {
        super(partItem);

        // Level emitters do not require a channel to function
        getMainNode().setFlags();
    }

    @Override
    protected void registerSettings(IConfigManagerBuilder builder) {
        super.registerSettings(builder);
        builder.registerSetting(Settings.REDSTONE_EMITTER, RedstoneMode.HIGH_SIGNAL);
    }

    protected abstract void configureWatchers();

    protected abstract boolean hasDirectOutput();

    protected abstract boolean getDirectOutput();

    @Override
    protected final void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);

        if (getMainNode().hasGridBooted()) {
            updateState();
        }
    }

    @Override
    public void writeToStream(PacketBuffer data) {
        super.writeToStream(data);
        data.writeBoolean(prevState);
    }

    @Override
    public boolean readFromStream(PacketBuffer data) {
        var changed = super.readFromStream(data);
        var wasOn = this.clientSideOn;
        this.clientSideOn = data.readBoolean();
        return changed || wasOn != this.clientSideOn;
    }

    @Override
    public void writeVisualStateToNBT(NBTTagCompound data) {
        super.writeVisualStateToNBT(data);

        data.setBoolean("on", isLevelEmitterOn());
    }

    @Override
    public void readVisualStateFromNBT(NBTTagCompound data) {
        super.readVisualStateFromNBT(data);

        this.clientSideOn = data.getBoolean("on");
    }

    protected void updateState() {
        var isOn = this.isLevelEmitterOn();
        if (this.prevState != isOn) {
            this.getHost().markForUpdate();
            var te = this.getHost().getTileEntity();
            this.prevState = isOn;
            Platform.notifyBlocksOfNeighbors(te.getWorld(), te.getPos());
            var side = this.getSide();
            if (side != null) {
                Platform.notifyBlocksOfNeighbors(te.getWorld(), te.getPos().offset(side));
            }
        }
    }

    public final long getReportingValue() {
        return this.reportingValue;
    }

    public final void setReportingValue(long v) {
        this.reportingValue = v;
        onReportingValueChanged();
        this.updateState();
    }

    protected void onReportingValueChanged() {
    }

    @Override
    public final int isProvidingStrongPower() {
        return prevState ? 15 : 0;
    }

    @Override
    public final int isProvidingWeakPower() {
        return prevState ? 15 : 0;
    }

    @Override
    public final void animateTick(World level, BlockPos pos, Random r) {
        if (this.isLevelEmitterOn()) {
            final EnumFacing d = this.getSide();
            if (d == null) {
                return;
            }

            final double d0 = d.getXOffset() * 0.45F + (r.nextFloat() - 0.5F) * 0.2D;
            final double d1 = d.getYOffset() * 0.45F + (r.nextFloat() - 0.5F) * 0.2D;
            final double d2 = d.getZOffset() * 0.45F + (r.nextFloat() - 0.5F) * 0.2D;

            level.spawnParticle(EnumParticleTypes.REDSTONE, 0.5 + pos.getX() + d0, 0.5 + pos.getY() + d1,
                0.5 + pos.getZ() + d2, 0.0D, 0.0D, 0.0D);
        }
    }

    protected boolean isLevelEmitterOn() {
        if (isClientSide()) {
            return clientSideOn;
        }

        if (!this.getMainNode().isActive()) {
            return false;
        }

        if (hasDirectOutput()) {
            return getDirectOutput();
        }

        final boolean flipState = this.getConfigManager()
                                      .getSetting(Settings.REDSTONE_EMITTER) == RedstoneMode.LOW_SIGNAL;
        return flipState == (this.reportingValue >= this.lastReportedValue + 1);
    }

    @Override
    public final boolean canConnectRedstone() {
        return true;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.lastReportedValue = data.getLong("lastReportedValue");
        this.reportingValue = data.getLong("reportingValue");
        this.prevState = data.getBoolean("prevState");
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setLong("lastReportedValue", this.lastReportedValue);
        data.setLong("reportingValue", this.reportingValue);
        data.setBoolean("prevState", this.prevState);
    }

    @Override
    public final float getCableConnectionLength(AECableType cable) {
        return 16;
    }

    @Override
    public final void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(7, 7, 11, 9, 9, 16);
    }

    @Override
    public final AECableType getDesiredConnectionType() {
        return AECableType.SMART;
    }

    @Override
    public final void onSettingChanged(IConfigManager manager, Setting<?> setting) {
        this.configureWatchers();
    }

    @Override
    public void importSettings(SettingsFrom mode, NBTTagCompound input, @Nullable EntityPlayer player) {
        super.importSettings(mode, input, player);

        if (mode == SettingsFrom.MEMORY_CARD && input.hasKey("reportingValue")) {
            setReportingValue(input.getLong("reportingValue"));
        }
    }

    @Override
    public void exportSettings(SettingsFrom mode, NBTTagCompound builder) {
        super.exportSettings(mode, builder);

        if (mode == SettingsFrom.MEMORY_CARD) {
            builder.setLong("reportingValue", reportingValue);
        }
    }

    @Override
    protected boolean shouldSendPowerStateToClient() {
        return false; // We handle this completely in our enabled flag
    }

    @Override
    protected boolean shouldSendMissingChannelStateToClient() {
        return false; // We handle this completely in our enabled flag
    }
}

