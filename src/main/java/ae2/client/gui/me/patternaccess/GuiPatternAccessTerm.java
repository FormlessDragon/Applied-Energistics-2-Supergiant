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

package ae2.client.gui.me.patternaccess;

import ae2.client.gui.style.GuiStyle;
import ae2.container.implementations.ContainerPatternAccessTerm;
import ae2.core.AppEng;
import ae2.core.localization.GuiText;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

public class GuiPatternAccessTerm extends AbstractPatternAccessTerm<ContainerPatternAccessTerm> {

    private static final ResourceLocation TEXTURE = AppEng.makeId("textures/guis/ex_pattern_access_terminal.png");
    private static final int GUI_FOOTER_HEIGHT = 99;
    private static final int GUI_FOOTER_TEXTURE_Y = 138;

    public GuiPatternAccessTerm(ContainerPatternAccessTerm container, InventoryPlayer playerInventory, @Nullable ITextComponent title,
                                GuiStyle style) {
        super(container, playerInventory, title, GuiText.PatternAccessTerminalShort.text(), style,
            "pattern access terminal", GUI_FOOTER_HEIGHT);
    }

    @Override
    protected void blitPatternAccess(int x, int y, int u, int v, int width, int height) {
        bindTexture(TEXTURE);
        drawTexturedModalRect(x, y, u, v, width, height);
    }

    @Override
    protected void drawPatternAccessFooter(int x, int y) {
        blitPatternAccess(x, y, 0, GUI_FOOTER_TEXTURE_Y, GUI_WIDTH, GUI_FOOTER_HEIGHT);
    }
}
