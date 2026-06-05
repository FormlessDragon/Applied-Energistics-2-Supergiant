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
package ae2.client.gui.implementations;

import ae2.api.config.InscriberInputCapacity;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.client.gui.Icon;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.IconButton;
import ae2.client.gui.widgets.ProgressBar;
import ae2.client.gui.widgets.ProgressBar.Direction;
import ae2.client.gui.widgets.ServerSettingToggleButton;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.container.GuiIds;
import ae2.container.implementations.ContainerInscriber;
import ae2.core.localization.ButtonToolTips;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.SwitchGuisPacket;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

public class GuiInscriber extends GuiUpgradeable<ContainerInscriber> {
    private final ProgressBar progressBar;
    private final SettingToggleButton<YesNo> separateSidesBtn;
    private final SettingToggleButton<YesNo> autoExportBtn;
    private final IconButton outputSidesBtn;
    private final SettingToggleButton<InscriberInputCapacity> bufferSizeBtn;

    public GuiInscriber(ContainerInscriber container, InventoryPlayer playerInventory, ITextComponent title,
                        GuiStyle style) {
        super(container, playerInventory, title, style);

        this.progressBar = new ProgressBar(this.container, style.getImage("progressBar"), Direction.VERTICAL);
        this.widgets.add("progressBar", this.progressBar);

        this.separateSidesBtn = addToLeftToolbar(
            new ServerSettingToggleButton<>(Settings.INSCRIBER_SEPARATE_SIDES, YesNo.NO));
        this.autoExportBtn = addToLeftToolbar(new ServerSettingToggleButton<>(Settings.AUTO_EXPORT, YesNo.NO));
        this.outputSidesBtn = addToLeftToolbar(new IconButton(this::openOutputSides) {
            {
                setMessage(ButtonToolTips.OutputSideConfig.text());
            }

            @Override
            protected Icon getIcon() {
                return Icon.OUTPUT_SIDE_CONFIG;
            }

            @Override
            public java.util.List<net.minecraft.util.text.ITextComponent> getTooltipMessage() {
                return java.util.List.of(
                    ButtonToolTips.OutputSideConfig.text(),
                    ButtonToolTips.OutputSideConfigHint.text());
            }
        });
        this.bufferSizeBtn = addToLeftToolbar(
            new ServerSettingToggleButton<>(Settings.INSCRIBER_INPUT_CAPACITY, InscriberInputCapacity.SIXTY_FOUR));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        int maxProgress = this.container.getMaxProgress();
        int progress = maxProgress > 0 ? this.container.getCurrentProgress() * 100 / maxProgress : 0;
        this.progressBar.setFullMsg(new TextComponentString(progress + "%"));
        this.separateSidesBtn.set(this.container.getSeparateSides());
        this.autoExportBtn.set(this.container.getAutoExport());
        this.outputSidesBtn.setVisibility(this.container.getAutoExport() == YesNo.YES);
        this.bufferSizeBtn.set(this.container.getBufferSize());
    }

    private void openOutputSides() {
        InitNetwork.sendToServer(SwitchGuisPacket.openSubGui(GuiIds.GuiKey.OUTPUT_SIDES));
    }
}
