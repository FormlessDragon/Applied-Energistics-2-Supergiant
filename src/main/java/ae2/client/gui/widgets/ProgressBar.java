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

package ae2.client.gui.widgets;

import ae2.client.gui.style.Blitter;
import ae2.container.interfaces.IProgressProvider;
import ae2.core.localization.GuiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.jspecify.annotations.NonNull;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;

public class ProgressBar extends GuiButton implements ITooltip {
    private final IProgressProvider source;
    private final Blitter blitter;
    private final Direction layout;
    private final Rectangle sourceRect;
    private final ITextComponent titleName;
    private ITextComponent fullMsg;

    public ProgressBar(IProgressProvider source, Blitter blitter, Direction dir) {
        this(source, blitter, dir, null);
    }

    public ProgressBar(IProgressProvider source, Blitter blitter, Direction dir, ITextComponent title) {
        super(0, 0, 0, blitter.getSrcWidth(), blitter.getSrcHeight(), "");
        this.source = source;
        this.blitter = blitter.copy();
        this.layout = dir;
        this.titleName = title;
        this.sourceRect = new Rectangle(
            blitter.getSrcX(),
            blitter.getSrcY(),
            blitter.getSrcWidth(),
            blitter.getSrcHeight());
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }

        int max = this.source.getMaxProgress();
        int current = Math.min(this.source.getCurrentProgress(), max);

        int srcX = this.sourceRect.x;
        int srcY = this.sourceRect.y;
        int srcW = this.sourceRect.width;
        int srcH = this.sourceRect.height;
        int destX = this.x;
        int destY = this.y;

        if (this.layout == Direction.VERTICAL) {
            int diff = this.height - (max > 0 ? this.height * current / max : 0);
            destY += diff;
            srcY += diff;
            srcH -= diff;
        } else {
            int diff = this.width - (max > 0 ? this.width * current / max : 0);
            srcX += diff;
            srcW -= diff;
        }

        if (srcW > 0 && srcH > 0) {
            this.blitter.copy()
                        .src(srcX, srcY, srcW, srcH)
                        .dest(destX, destY, srcW, srcH)
                        .blit();
        }
    }

    public void setFullMsg(ITextComponent msg) {
        this.fullMsg = msg;
    }

    @Override
    public @NonNull List<ITextComponent> getTooltipMessage() {
        if (this.fullMsg != null) {
            return Collections.singletonList(this.fullMsg);
        }

        ITextComponent result = this.titleName != null ? this.titleName : new TextComponentString("");
        return List.of(
            result,
            new TextComponentString(this.source.getCurrentProgress() + " ")
                .appendSibling(GuiText.Of.text())
                .appendText(" " + this.source.getMaxProgress()));
    }

    @Override
    public Rectangle getTooltipArea() {
        return new Rectangle(this.x - 2, this.y - 2, this.width + 4, this.height + 4);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return this.visible;
    }

    public enum Direction {
        HORIZONTAL,
        VERTICAL
    }
}
