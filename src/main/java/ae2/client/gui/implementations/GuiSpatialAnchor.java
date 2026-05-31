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

package ae2.client.gui.implementations;

import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.CommonButtons;
import ae2.client.gui.widgets.ServerSettingToggleButton;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.container.implementations.ContainerSpatialAnchor;
import ae2.core.localization.GuiText;
import ae2.util.Platform;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

public class GuiSpatialAnchor extends AEBaseGui<ContainerSpatialAnchor> {

    private final SettingToggleButton<YesNo> overlayToggle;

    public GuiSpatialAnchor(ContainerSpatialAnchor container, InventoryPlayer playerInventory, ITextComponent title,
                            GuiStyle style) {
        super(container, playerInventory, style);
        this.addToLeftToolbar(CommonButtons.togglePowerUnit());
        this.overlayToggle = this.addToLeftToolbar(new ServerSettingToggleButton<>(Settings.OVERLAY_MODE, YesNo.NO));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.overlayToggle.set(this.container.overlayMode);
        if (this.container.getGuiTitle() == null) {
            setTextContent("dialog_title", GuiText.SpatialAnchor.text());
        }
        setTextContent("used_power", GuiText.SpatialAnchorUsedPower.text(
            Platform.formatPowerLong(this.container.powerConsumption * 100, true)));
        setTextContent("loaded_chunks", GuiText.SpatialAnchorLoadedChunks.text(this.container.loadedChunks));
        setTextContent("statistics_title", GuiText.SpatialAnchorStatistics.text());
        setTextContent("statistics_loaded", GuiText.SpatialAnchorAllLoaded.text(
            this.container.allLoadedChunks, this.container.allLoadedWorlds));
        setTextContent("statistics_total", GuiText.SpatialAnchorAll.text(
            this.container.allChunks, this.container.allWorlds));
    }
}
