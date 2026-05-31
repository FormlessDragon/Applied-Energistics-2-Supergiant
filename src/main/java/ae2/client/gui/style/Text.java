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

package ae2.client.gui.style;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

public class Text {

    private ITextComponent text = new TextComponentString("");

    private PaletteColor color = PaletteColor.DEFAULT_TEXT_COLOR;

    private Position position;

    private TextAlignment align = TextAlignment.LEFT;

    private float scale = 1.0f;

    private int maxWidth = 0;

    public ITextComponent getText() {
        return text;
    }

    public void setText(ITextComponent text) {
        this.text = text;
    }

    public PaletteColor getColor() {
        return color;
    }

    public void setColor(PaletteColor color) {
        this.color = color;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public TextAlignment getAlign() {
        return align;
    }

    public void setAlign(TextAlignment align) {
        this.align = align;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public int getMaxWidth() {
        return maxWidth;
    }

    public void setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
    }
}

