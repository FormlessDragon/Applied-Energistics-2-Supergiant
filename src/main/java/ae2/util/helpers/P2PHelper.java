/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package ae2.util.helpers;

import ae2.api.util.AEColor;
import com.google.common.base.Preconditions;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

public class P2PHelper {
    private static final String[] HEX_DIGITS = {
        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F"
    };
    private static final TextFormatting[] COLOR_FORMATTINGS = {
        TextFormatting.WHITE,
        TextFormatting.GRAY,
        TextFormatting.DARK_GRAY,
        TextFormatting.DARK_GRAY,
        TextFormatting.GREEN,
        TextFormatting.YELLOW,
        TextFormatting.GOLD,
        TextFormatting.GOLD,
        TextFormatting.RED,
        TextFormatting.LIGHT_PURPLE,
        TextFormatting.LIGHT_PURPLE,
        TextFormatting.DARK_PURPLE,
        TextFormatting.BLUE,
        TextFormatting.AQUA,
        TextFormatting.DARK_AQUA,
        TextFormatting.DARK_GREEN
    };

    private static int getFrequencyNibble(short frequency, int i) {
        return frequency >> 4 * (3 - i) & 0xF;
    }

    public AEColor[] toColors(short frequency) {
        final AEColor[] colors = new AEColor[4];

        for (int i = 0; i < 4; i++) {
            int nibble = getFrequencyNibble(frequency, i);
            colors[i] = AEColor.VALID_COLORS[nibble];
        }

        return colors;
    }

    public short fromColors(AEColor[] colors) {
        Preconditions.checkArgument(colors.length == 4);

        int packed = 0;
        for (int i = 0; i < 4; i++) {
            AEColor color = colors[3 - i];
            Preconditions.checkArgument(color != AEColor.TRANSPARENT);
            packed |= color.ordinal() << 4 * i;
        }

        return (short) (packed & 0xFFFF);
    }

    public String toHexDigit(AEColor color) {
        return String.format("%01X", color.ordinal());
    }

    public String toHexString(short frequency) {
        return String.format("%04X", frequency & 0xFFFF);
    }

    public ITextComponent toColoredHexString(short frequency) {
        ITextComponent parent = new TextComponentString("");

        for (int i = 0; i < 4; i++) {
            int nibble = getFrequencyNibble(frequency, i);
            TextComponentString child = new TextComponentString(HEX_DIGITS[nibble]);
            child.setStyle(new Style().setColor(COLOR_FORMATTINGS[nibble]));
            parent.appendSibling(child);
        }

        return parent;
    }
}
