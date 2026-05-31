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
import ae2.api.config.Settings;
import ae2.api.networking.energy.IEnergyService;
import ae2.api.networking.energy.IEnergyWatcher;
import ae2.api.networking.energy.IEnergyWatcherNode;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.util.IConfigManagerBuilder;
import ae2.container.GuiIds;
import ae2.core.AppEng;
import ae2.core.gui.GuiOpener;
import ae2.items.parts.PartModels;
import ae2.parts.PartModel;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;

public class EnergyLevelEmitterPart extends AbstractLevelEmitterPart {

    @PartModels
    public static final ResourceLocation MODEL_BASE_OFF = AppEng.makeId(
        "part/level_emitter_base_off");
    @PartModels
    public static final ResourceLocation MODEL_BASE_ON = AppEng.makeId(
        "part/level_emitter_base_on");
    @PartModels
    public static final ResourceLocation MODEL_STATUS_OFF = AppEng.makeId(
        "part/level_emitter_status_off");
    @PartModels
    public static final ResourceLocation MODEL_STATUS_ON = AppEng.makeId(
        "part/level_emitter_status_on");
    @PartModels
    public static final ResourceLocation MODEL_STATUS_HAS_CHANNEL = AppEng.makeId(
        "part/level_emitter_status_has_channel");

    public static final PartModel MODEL_OFF_OFF = new PartModel(MODEL_BASE_OFF, MODEL_STATUS_OFF);
    public static final PartModel MODEL_OFF_ON = new PartModel(MODEL_BASE_OFF, MODEL_STATUS_ON);
    public static final PartModel MODEL_OFF_HAS_CHANNEL = new PartModel(MODEL_BASE_OFF, MODEL_STATUS_HAS_CHANNEL);
    public static final PartModel MODEL_ON_OFF = new PartModel(MODEL_BASE_ON, MODEL_STATUS_OFF);
    public static final PartModel MODEL_ON_ON = new PartModel(MODEL_BASE_ON, MODEL_STATUS_ON);
    public static final PartModel MODEL_ON_HAS_CHANNEL = new PartModel(MODEL_BASE_ON, MODEL_STATUS_HAS_CHANNEL);

    private IEnergyWatcher energyWatcher;

    public EnergyLevelEmitterPart(IPartItem<?> partItem) {
        super(partItem);

        IEnergyWatcherNode energyWatcherNode = new IEnergyWatcherNode() {
            @Override
            public void updateWatcher(IEnergyWatcher newWatcher) {
                energyWatcher = newWatcher;
                configureWatchers();
            }

            @Override
            public void onThresholdPass(IEnergyService energyGrid) {
                lastReportedValue = (long) energyGrid.getStoredPower();
                updateState();
            }
        };
        getMainNode().addService(IEnergyWatcherNode.class, energyWatcherNode);
    }

    @Override
    protected void registerSettings(IConfigManagerBuilder builder) {
        super.registerSettings(builder);
        builder.registerSetting(Settings.REDSTONE_EMITTER, RedstoneMode.HIGH_SIGNAL);
    }

    @Override
    protected int getUpgradeSlots() {
        return 0;
    }

    @Override
    protected void configureWatchers() {
        if (this.energyWatcher != null) {
            this.energyWatcher.reset();
        }

        if (this.energyWatcher != null) {
            this.energyWatcher.add(getReportingValue());
        }

        getMainNode().ifPresent(grid -> {
            // update to power...
            this.lastReportedValue = (long) grid.getEnergyService().getStoredPower();
            this.updateState();
        });
    }

    @Override
    protected boolean hasDirectOutput() {
        return false;
    }

    @Override
    protected boolean getDirectOutput() {
        throw new UnsupportedOperationException("hasDirectOutput is false...");
    }

    @Override
    public boolean onUseWithoutItem(EntityPlayer player, Vec3d pos) {
        if (!isClientSide()) {
            GuiOpener.openPartGui(player, GuiIds.GuiKey.ENERGY_LEVEL_EMITTER, this);
        }
        return true;
    }

    @Override
    public boolean onUseItemOn(ItemStack heldItem, EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (super.onUseItemOn(heldItem, player, hand, pos)) {
            return true;
        }
        return this.onUseWithoutItem(player, pos);
    }

    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return this.isLevelEmitterOn() ? MODEL_ON_HAS_CHANNEL : MODEL_OFF_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return this.isLevelEmitterOn() ? MODEL_ON_ON : MODEL_OFF_ON;
        } else {
            return this.isLevelEmitterOn() ? MODEL_ON_OFF : MODEL_OFF_OFF;
        }
    }
}
