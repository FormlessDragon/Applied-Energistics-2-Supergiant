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

import ae2.client.Point;
import ae2.client.gui.style.Blitter;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.WidgetStyle;
import ae2.client.gui.widgets.CommonButtons;
import ae2.container.implementations.ContainerVibrationChamber;
import ae2.util.Platform;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

import java.awt.Rectangle;

public class GuiVibrationChamber extends GuiUpgradeable<ContainerVibrationChamber> {
    private static final Blitter BURN_PROGRESS = Blitter.texture("guis/vibchamber.png").src(176, 0, 14, 13);

    public GuiVibrationChamber(ContainerVibrationChamber container, InventoryPlayer playerInventory, ITextComponent title,
                               GuiStyle style) {
        super(container, playerInventory, title, style);
        addToLeftToolbar(CommonButtons.togglePowerUnit());
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY, partialTicks);

        if (style == null) {
            return;
        }

        int progress = this.container.getCurrentProgress();
        int maxProgress = this.container.getMaxProgress();
        if (progress > 0 && maxProgress > 0) {
            Blitter progressBar = style.getImage("generationRateBar");
            WidgetStyle widgetStyle = style.getWidget("generationRateBar");
            Point widget = widgetStyle.resolve(new Rectangle(0, 0, this.xSize, this.ySize));
            int height = Math.max(1, progressBar.getSrcHeight() * progress / maxProgress);
            int srcX = progressBar.getSrcX();
            int srcY = progressBar.getSrcY() + progressBar.getSrcHeight() - height;
            int destX = offsetX + widget.x();
            int destY = offsetY + widget.y() + progressBar.getSrcHeight() - height;

            progressBar.copy()
                       .src(srcX, srcY, progressBar.getSrcWidth(), height)
                       .dest(destX, destY, progressBar.getSrcWidth(), height)
                       .blit();
        }
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(Platform.formatPower(this.container.getPowerPerTick(), true), 8, 20, 0x404040);
        this.fontRenderer.drawString("Eff: " + this.container.getFuelEfficiency() + "%", 8, 30, 0x404040);

        if (this.container.getRemainingBurnTime() > 0) {
            int f = Math.max(1, this.container.getRemainingBurnTime() * BURN_PROGRESS.getSrcHeight() / 100);
            BURN_PROGRESS.copy()
                         .src(
                             BURN_PROGRESS.getSrcX(),
                             BURN_PROGRESS.getSrcY() + BURN_PROGRESS.getSrcHeight() - f,
                             BURN_PROGRESS.getSrcWidth(),
                             f)
                         .dest(80, 20 + BURN_PROGRESS.getSrcHeight() - f)
                         .blit(this);
        }
    }
}
