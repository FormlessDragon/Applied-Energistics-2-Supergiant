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

package ae2.client.gui.me.common;

import ae2.core.AEConfig;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;

/**
 * @author AlgorithmX2
 * @author thatsIch
 * @version rv2
 * @since rv0
 */
public class StackSizeRenderer {
    private static void renderSizeLabelInternal(FontRenderer fontRenderer, float xPos, float yPos, String text,
                                                boolean largeFonts) {
        final float scaleFactor = largeFonts ? 0.85f : 0.666f;
        final float inverseScaleFactor = 1.0f / scaleFactor;
        final int offset = largeFonts ? 0 : -1;

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();
        final int x = (int) ((xPos + offset + 16.0f + 2.0f - fontRenderer.getStringWidth(text) * scaleFactor)
            * inverseScaleFactor);
        final int y = (int) ((yPos + offset + 16.0f - 5.0f * scaleFactor) * inverseScaleFactor);
        fontRenderer.drawString(text, x + 1, y + 1, 0x413f54);
        fontRenderer.drawString(text, x, y, 0xffffff);
        GlStateManager.enableBlend();
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
    }

    public static void renderSizeLabel(FontRenderer fontRenderer, float xPos, float yPos, String text) {
        renderSizeLabel(fontRenderer, xPos, yPos, text, AEConfig.instance().isUseLargeFonts());
    }

    public static void renderSizeLabel(FontRenderer fontRenderer, float xPos, float yPos, String text,
                                       boolean largeFonts) {
        final float scaleFactor = largeFonts ? 0.85f : 0.666f;

        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 200);
        GlStateManager.scale(scaleFactor, scaleFactor, scaleFactor);

        renderSizeLabelInternal(fontRenderer, xPos, yPos, text, largeFonts);

        GlStateManager.popMatrix();
    }
}
