/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026, TeamAppliedEnergistics, All rights reserved.
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

package ae2.client.gui.me.crafting;

import ae2.client.gui.style.Blitter;

final class CraftingScreenBackground {
    private static final int MAIN_WIDTH = 218;
    private static final int SCROLLBAR_WIDTH = CraftingScreenLayout.WIDTH - MAIN_WIDTH;

    private CraftingScreenBackground() {
    }

    static void draw(String texture, int offsetX, int offsetY, int rows, int topHeight, int rowSourceY,
                     int bottomSourceY, int bottomHeight, int rightFooterHeight) {
        int firstRowSourceY = topHeight;
        int lastRowSourceY = bottomSourceY - CraftingScreenLayout.TABLE_ROW_HEIGHT;

        Blitter.texture(texture)
               .src(0, 0, MAIN_WIDTH, topHeight)
               .dest(offsetX, offsetY)
               .blit();
        drawScrollbarTrack(texture, offsetX + MAIN_WIDTH, offsetY, rows, topHeight, rowSourceY, bottomSourceY);

        int y = offsetY + topHeight;
        for (int row = 0; row < rows; row++) {
            int currentRowSourceY;
            if (row == 0) {
                currentRowSourceY = firstRowSourceY;
            } else if (row == rows - 1) {
                currentRowSourceY = lastRowSourceY;
            } else {
                currentRowSourceY = rowSourceY;
            }
            Blitter.texture(texture)
                   .src(0, currentRowSourceY, MAIN_WIDTH, CraftingScreenLayout.TABLE_ROW_HEIGHT)
                   .dest(offsetX, y)
                   .blit();
            y += CraftingScreenLayout.TABLE_ROW_HEIGHT;
        }

        Blitter.texture(texture)
               .src(0, bottomSourceY, MAIN_WIDTH, bottomHeight)
               .dest(offsetX, y)
               .blit();

        if (rightFooterHeight > 0) {
            Blitter.texture(texture)
                   .src(MAIN_WIDTH, bottomSourceY, SCROLLBAR_WIDTH, rightFooterHeight)
                   .dest(offsetX + MAIN_WIDTH, y)
                   .blit();
        }
    }

    private static void drawScrollbarTrack(String texture, int x, int offsetY, int rows, int topHeight, int rowSourceY,
                                           int bottomSourceY) {
        Blitter.texture(texture)
               .src(MAIN_WIDTH, 0, SCROLLBAR_WIDTH, topHeight)
               .dest(x, offsetY)
               .blit();

        int y = offsetY + topHeight;
        for (int row = 0; row < rows; row++) {
            Blitter.texture(texture)
                   .src(MAIN_WIDTH, rowSourceY, SCROLLBAR_WIDTH, CraftingScreenLayout.TABLE_ROW_HEIGHT)
                   .dest(x, y)
                   .blit();
            y += CraftingScreenLayout.TABLE_ROW_HEIGHT;
        }

        Blitter.texture(texture)
               .src(MAIN_WIDTH, bottomSourceY - 1, SCROLLBAR_WIDTH, 1)
               .dest(x, y - 1)
               .blit();
    }
}
