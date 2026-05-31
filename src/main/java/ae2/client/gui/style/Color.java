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

import org.jspecify.annotations.NonNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Color(int r, int g, int b, int a) {

    private static final Pattern PATTERN = Pattern.compile("^#([0-9a-fA-F]{2}){3,4}$");

    public static Color parse(String string) {
        Matcher m = PATTERN.matcher(string);
        if (!m.matches()) {
            throw new IllegalArgumentException("Color must have format #AARRGGBB (" + string + ")");
        }

        int r, g, b, a = 255;
        if (string.length() == 7) {
            r = Integer.valueOf(string.substring(1, 3), 16);
            g = Integer.valueOf(string.substring(3, 5), 16);
            b = Integer.valueOf(string.substring(5, 7), 16);
        } else {
            a = Integer.valueOf(string.substring(1, 3), 16);
            r = Integer.valueOf(string.substring(3, 5), 16);
            g = Integer.valueOf(string.substring(5, 7), 16);
            b = Integer.valueOf(string.substring(7, 9), 16);
        }

        return new Color(r, g, b, a);
    }

    @Override
    public @NonNull String toString() {
        StringBuilder result = new StringBuilder(9);
        result.append('#');
        if (a <= 0xF) {
            result.append('0');
        }
        result.append(Integer.toHexString(a));
        if (r <= 0xF) {
            result.append('0');
        }
        result.append(Integer.toHexString(r));
        if (g <= 0xF) {
            result.append('0');
        }
        result.append(Integer.toHexString(g));
        if (b <= 0xF) {
            result.append('0');
        }
        result.append(Integer.toHexString(b));

        return result.toString();
    }

    public int toARGB() {
        return a << 24 | r << 16 | g << 8 | b;
    }
}

