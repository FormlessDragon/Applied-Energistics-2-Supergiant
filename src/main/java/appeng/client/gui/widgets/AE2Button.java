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

package appeng.client.gui.widgets;

import appeng.client.gui.style.Blitter;
import appeng.core.AppEng;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.IntBuffer;

public class AE2Button extends GuiButton {
    private static final ResourceLocation BUTTON = AppEng.makeId("textures/guis/button.png");
    private static final ResourceLocation BUTTON_DISABLED = AppEng.makeId("textures/guis/button_disabled.png");
    private static final ResourceLocation BUTTON_HIGHLIGHTED = AppEng.makeId("textures/guis/button_highlighted.png");

    private final Runnable onPress;
    private final float alpha = 1.0F;
    private ITextComponent message;
    private boolean forceHighlighted;

    public AE2Button(int x, int y, int width, int height, ITextComponent component, Runnable onPress) {
        super(0, x, y, width, height, "");
        this.message = component;
        this.onPress = onPress;
    }

    public AE2Button(ITextComponent component, Runnable onPress) {
        this(0, 0, 0, 0, component, onPress);
    }

    public static void renderButtonText(FontRenderer fontRenderer, ITextComponent text, int minX, int minY, int maxX,
                                        int maxY, int yOffset, int color) {
        renderButtonText(fontRenderer, text, (minX + maxX) / 2, minX, minY, maxX, maxY, yOffset, color);
    }

    public static void renderButtonText(FontRenderer fontRenderer, ITextComponent text, int centerX, int minX, int minY,
                                        int maxX, int maxY, int yOffset, int color) {
        String renderedText = text == null ? "" : text.getFormattedText();
        int textWidth = fontRenderer.getStringWidth(renderedText);
        int textY = (minY + maxY - 9) / 2 + 1;
        int maxWidth = maxX - minX;
        if (textWidth > maxWidth) {
            int overflow = textWidth - maxWidth;
            double now = Minecraft.getSystemTime() / 1000.0D;
            double duration = Math.max(overflow * 0.5D, 3.0D);
            double phase = Math.sin((Math.PI / 2.0D) * Math.cos((Math.PI * 2.0D) * now / duration)) / 2.0D + 0.5D;
            double offset = MathHelper.clampedLerp(0.0D, overflow, phase);
            withScissor(minX, minY, maxX, maxY,
                () -> fontRenderer.drawString(renderedText, (float) (minX - offset), textY, color, false));
        } else {
            int clampedCenterX = MathHelper.clamp(centerX, minX + textWidth / 2, maxX - textWidth / 2);
            fontRenderer.drawString(renderedText, clampedCenterX - textWidth / 2.0F, textY - yOffset, color, false);
        }
    }

    private static void withScissor(int minX, int minY, int maxX, int maxY, Runnable action) {
        boolean wasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        IntBuffer previousBox = BufferUtils.createIntBuffer(4);
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, previousBox);

        Minecraft minecraft = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(minecraft);
        int scale = resolution.getScaleFactor();
        int scissorX = minX * scale;
        int scissorY = minecraft.displayHeight - maxY * scale;
        int scissorWidth = Math.max(0, (maxX - minX) * scale);
        int scissorHeight = Math.max(0, (maxY - minY) * scale);
        try {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
            action.run();
        } finally {
            GL11.glScissor(previousBox.get(0), previousBox.get(1), previousBox.get(2), previousBox.get(3));
            if (wasEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        super.mouseReleased(mouseX, mouseY);
        boolean releasedInside = this.enabled && this.visible
            && mouseX >= this.x
            && mouseY >= this.y
            && mouseX < this.x + this.width
            && mouseY < this.y + this.height;
        if (releasedInside && this.onPress != null) {
            this.onPress.run();
        }
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }

        this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;

        GlStateManager.color(1.0F, 1.0F, 1.0F, this.alpha);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.enableDepth();

        renderNineSlice(getButtonBlitter());

        int color;
        int yOffset;
        if (!this.enabled) {
            color = applyAlpha(0x413f54);
            yOffset = -1;
        } else if (this.hovered || this.forceHighlighted) {
            color = applyAlpha(0x517497);
            yOffset = 0;
        } else {
            color = applyAlpha(0xf2f2f2);
            yOffset = 1;
        }

        this.renderButtonText(minecraft.fontRenderer, color, yOffset);
    }

    protected void renderButtonText(FontRenderer fontRenderer, int color, int yOffset) {
        int minX = this.x + 2;
        int maxX = this.x + this.width - 2;
        renderButtonText(fontRenderer, this.getMessageComponent(), minX, this.y, maxX, this.y + this.height, yOffset, color);
    }

    public ITextComponent getMessageComponent() {
        return this.message != null ? this.message : new TextComponentString("");
    }

    public void setMessage(ITextComponent message) {
        this.message = message;
        this.displayString = getMessageComponent().getFormattedText();
    }

    public void setForceHighlighted(boolean forceHighlighted) {
        this.forceHighlighted = forceHighlighted;
    }

    private Blitter getButtonBlitter() {
        if (!this.enabled) {
            return this.forceHighlighted
                ? Blitter.texture(BUTTON_HIGHLIGHTED, 200, 20)
                : Blitter.texture(BUTTON_DISABLED, 200, 20);
        } else if (this.hovered || this.forceHighlighted) {
            return Blitter.texture(BUTTON_HIGHLIGHTED, 200, 20);
        }
        return Blitter.texture(BUTTON, 200, 20);
    }

    private int applyAlpha(int color) {
        return color | MathHelper.ceil(this.alpha * 255.0F) << 24;
    }

    private void renderNineSlice(Blitter texture) {
        int left = Math.min(3, this.width / 2);
        int right = Math.min(3, this.width - left);
        int top = Math.min(3, this.height / 2);
        int bottom = Math.min(3, this.height - top);
        int centerWidth = Math.max(0, this.width - left - right);
        int centerHeight = Math.max(0, this.height - top - bottom);

        if (left > 0 && top > 0) {
            texture.copy().src(0, 0, 3, 3).dest(this.x, this.y, left, top).blit();
        }
        if (centerWidth > 0 && top > 0) {
            texture.copy().src(3, 0, 194, 3).dest(this.x + left, this.y, centerWidth, top).blit();
        }
        if (right > 0 && top > 0) {
            texture.copy().src(197, 0, 3, 3).dest(this.x + this.width - right, this.y, right, top).blit();
        }
        if (left > 0 && centerHeight > 0) {
            texture.copy().src(0, 3, 3, 14).dest(this.x, this.y + top, left, centerHeight).blit();
        }
        if (centerWidth > 0 && centerHeight > 0) {
            texture.copy().src(3, 3, 194, 14).dest(this.x + left, this.y + top, centerWidth, centerHeight).blit();
        }
        if (right > 0 && centerHeight > 0) {
            texture.copy().src(197, 3, 3, 14).dest(this.x + this.width - right, this.y + top, right, centerHeight).blit();
        }
        if (left > 0 && bottom > 0) {
            texture.copy().src(0, 17, 3, 3).dest(this.x, this.y + this.height - bottom, left, bottom).blit();
        }
        if (centerWidth > 0 && bottom > 0) {
            texture.copy().src(3, 17, 194, 3).dest(this.x + left, this.y + this.height - bottom, centerWidth, bottom).blit();
        }
        if (right > 0 && bottom > 0) {
            texture.copy().src(197, 17, 3, 3).dest(this.x + this.width - right, this.y + this.height - bottom, right, bottom).blit();
        }
    }
}
