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

package ae2.client.gui.cellterminal.widget;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

public abstract class AbstractWidget implements IWidget {
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected boolean visible = true;

    protected AbstractWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public static String trimTextToWidth(FontRenderer fontRenderer, String text, int maxWidth) {
        if (fontRenderer.getStringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipWidth = fontRenderer.getStringWidth(ellipsis);
        for (int i = text.length() - 1; i > 0; i--) {
            String trimmed = text.substring(0, i);
            if (fontRenderer.getStringWidth(trimmed) + ellipWidth <= maxWidth) {
                return trimmed + ellipsis;
            }
        }
        return ellipsis;
    }

    public static void renderItemStack(RenderItem itemRender, ItemStack stack, int renderX, int renderY) {
        if (stack.isEmpty()) {
            return;
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemIntoGUI(stack, renderX, renderY);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public boolean isHovered(int mouseX, int mouseY) {
        if (!visible) {
            return false;
        }
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
