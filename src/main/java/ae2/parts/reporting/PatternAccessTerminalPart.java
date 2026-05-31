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

import ae2.api.config.Settings;
import ae2.api.config.ShowPatternProviders;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.storage.ILinkStatus;
import ae2.api.storage.IPatternAccessTermContainerHost;
import ae2.api.util.IConfigManager;
import ae2.container.GuiIds;
import ae2.core.AppEng;
import ae2.core.gui.GuiOpener;
import ae2.items.parts.PartModels;
import ae2.parts.PartModel;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;

public class PatternAccessTerminalPart extends AbstractDisplayPart implements IPatternAccessTermContainerHost {

    @PartModels
    public static final ResourceLocation MODEL_OFF = AppEng.makeId("part/pattern_access_terminal_off");
    @PartModels
    public static final ResourceLocation MODEL_ON = AppEng.makeId("part/pattern_access_terminal_on");

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    private final IConfigManager configManager = IConfigManager.builder(() -> this.getHost().markForSave())
                                                               .registerSetting(Settings.TERMINAL_SHOW_PATTERN_PROVIDERS, ShowPatternProviders.VISIBLE)
                                                               .build();

    public PatternAccessTerminalPart(IPartItem<?> partItem) {
        super(partItem, true);
    }

    @Override
    public boolean onUseWithoutItem(EntityPlayer player, Vec3d pos) {
        if (!super.onUseWithoutItem(player, pos) && !isClientSide()) {
            GuiOpener.openPartGui(player, GuiIds.GuiKey.PATTERN_ACCESS_TERMINAL, this);
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
        return this.selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.configManager;
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        this.configManager.writeToNBT(data);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.configManager.readFromNBT(data);
    }

    @Override
    public ILinkStatus getLinkStatus() {
        return ILinkStatus.ofManagedNode(getMainNode());
    }
}
