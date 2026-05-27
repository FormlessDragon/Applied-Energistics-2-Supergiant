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

package appeng.client.gui.style;

import appeng.core.AppEng;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

import java.awt.Rectangle;
import java.util.Objects;

public final class Blitter {

    public static final int DEFAULT_TEXTURE_WIDTH = 256;
    public static final int DEFAULT_TEXTURE_HEIGHT = 256;

    private final ResourceLocation texture;
    private final int referenceWidth;
    private final int referenceHeight;
    private int r = 255;
    private int g = 255;
    private int b = 255;
    private int a = 255;
    private Rectangle srcRect;
    private Rectangle destRect = new Rectangle(0, 0, 0, 0);
    private boolean blending = true;
    private TextureTransform transform = TextureTransform.NONE;
    private int zOffset;
    private Float minU;
    private Float minV;
    private Float maxU;
    private Float maxV;

    Blitter(ResourceLocation texture, int referenceWidth, int referenceHeight) {
        this.texture = texture;
        this.referenceWidth = referenceWidth;
        this.referenceHeight = referenceHeight;
    }

    public static Blitter texture(ResourceLocation file) {
        return texture(file, DEFAULT_TEXTURE_WIDTH, DEFAULT_TEXTURE_HEIGHT);
    }

    public static Blitter texture(String file) {
        return texture(file, DEFAULT_TEXTURE_WIDTH, DEFAULT_TEXTURE_HEIGHT);
    }

    public static Blitter texture(ResourceLocation file, int referenceWidth, int referenceHeight) {
        return new Blitter(file, referenceWidth, referenceHeight);
    }

    public static Blitter texture(String file, int referenceWidth, int referenceHeight) {
        return new Blitter(AppEng.makeId("textures/" + file), referenceWidth, referenceHeight);
    }

    public static Blitter sprite(TextureAtlasSprite sprite) {
        return new Blitter(TextureMap.LOCATION_BLOCKS_TEXTURE, 1, 1)
            .src(0, 0, sprite.getIconWidth(), sprite.getIconHeight())
            .uv(sprite.getMinU(), sprite.getMinV(), sprite.getMaxU(), sprite.getMaxV());
    }

    public static Blitter sprite(TextureAtlasSprite sprite, int x, int y, int w, int h) {
        float spriteWidth = sprite.getIconWidth();
        float spriteHeight = sprite.getIconHeight();
        float minU = interpolate(sprite.getMinU(), sprite.getMaxU(), x / spriteWidth);
        float minV = interpolate(sprite.getMinV(), sprite.getMaxV(), y / spriteHeight);
        float maxU = interpolate(sprite.getMinU(), sprite.getMaxU(), (x + w) / spriteWidth);
        float maxV = interpolate(sprite.getMinV(), sprite.getMaxV(), (y + h) / spriteHeight);
        return new Blitter(TextureMap.LOCATION_BLOCKS_TEXTURE, 1, 1)
            .src(x, y, w, h)
            .uv(minU, minV, maxU, maxV);
    }

    public static Blitter guiSprite(ResourceLocation resourceLocation) {
        return texture(resourceLocation);
    }

    private static float interpolate(float min, float max, float progress) {
        return min + (max - min) * progress;
    }

    public Blitter copy() {
        Blitter result = new Blitter(texture, referenceWidth, referenceHeight);
        result.srcRect = srcRect;
        result.destRect = destRect;
        result.r = r;
        result.g = g;
        result.b = b;
        result.a = a;
        result.blending = blending;
        result.transform = transform;
        result.zOffset = zOffset;
        result.minU = minU;
        result.minV = minV;
        result.maxU = maxU;
        result.maxV = maxV;
        return result;
    }

    public int getSrcX() {
        if (srcRect == null) {
            return 0;
        } else {
            return srcRect.x;
        }
    }

    public int getSrcY() {
        if (srcRect == null) {
            return 0;
        } else {
            return srcRect.y;
        }
    }

    public int getSrcWidth() {
        if (srcRect == null) {
            return destRect.width;
        } else {
            return srcRect.width;
        }
    }

    public int getSrcHeight() {
        if (srcRect == null) {
            return destRect.height;
        } else {
            return srcRect.height;
        }
    }

    public Blitter src(int x, int y, int w, int h) {
        this.srcRect = new Rectangle(x, y, w, h);
        return this;
    }

    public Blitter srcWidth(int w) {
        this.srcRect = new Rectangle(srcRect.x, srcRect.y, w, srcRect.height);
        return this;
    }

    public Blitter srcHeight(int h) {
        this.srcRect = new Rectangle(srcRect.x, srcRect.y, srcRect.width, h);
        return this;
    }

    public Blitter src(Rectangle rect) {
        return src(rect.x, rect.y, rect.width, rect.height);
    }

    public Blitter dest(int x, int y, int w, int h) {
        this.destRect = new Rectangle(x, y, w, h);
        return this;
    }

    public Blitter dest(int x, int y) {
        return dest(x, y, 0, 0);
    }

    public Blitter dest(Rectangle rect) {

        return dest(rect.x, rect.y, rect.width, rect.height);
    }

    public Rectangle getDestRect() {
        int x = destRect.x;

        int y = destRect.y;
        int w = 0;
        int h = 0;
        if (destRect.width != 0 && destRect.height != 0) {
            w = destRect.width;
            h = destRect.height;
        } else if (srcRect != null) {
            w = srcRect.width;
            h = srcRect.height;
        }
        return new Rectangle(x, y, w, h);
    }

    public Blitter color(float r, float g, float b) {
        this.r = (int) (MathHelper.clamp(r, 0, 1) * 255);
        this.g = (int) (MathHelper.clamp(g, 0, 1) * 255);
        this.b = (int) (MathHelper.clamp(b, 0, 1) * 255);
        return this;
    }

    public Blitter colorArgb(int packedArgb) {
        this.a = packedArgb >> 24 & 255;
        this.r = packedArgb >> 16 & 255;
        this.g = packedArgb >> 8 & 255;
        this.b = packedArgb & 255;
        return this;
    }

    public Blitter opacity(float a) {
        this.a = (int) (MathHelper.clamp(a, 0, 1) * 255);
        return this;
    }

    public Blitter color(float r, float g, float b, float a) {
        return color(r, g, b).opacity(a);
    }

    public Blitter transform(TextureTransform transform) {
        this.transform = Objects.requireNonNull(transform);
        return this;
    }

    public Blitter blending(boolean enable) {
        this.blending = enable;
        return this;
    }

    public Blitter colorRgb(int packedRgb) {
        float r = (packedRgb >> 16 & 255) / 255.0F;
        float g = (packedRgb >> 8 & 255) / 255.0F;
        float b = (packedRgb & 255) / 255.0F;

        return color(r, g, b);
    }

    public Blitter zOffset(int offset) {
        this.zOffset = offset;
        return this;
    }

    private Blitter uv(float minU, float minV, float maxU, float maxV) {
        this.minU = minU;
        this.minV = minV;
        this.maxU = maxU;
        this.maxV = maxV;
        return this;
    }

    public void blit() {
        Minecraft.getMinecraft().getTextureManager().bindTexture(this.texture);

        float minU;
        float minV;
        float maxU;
        float maxV;
        if (this.minU != null && this.minV != null && this.maxU != null && this.maxV != null) {
            minU = this.minU;
            minV = this.minV;
            maxU = this.maxU;
            maxV = this.maxV;
        } else if (srcRect == null) {
            minU = minV = 0;
            maxU = maxV = 1;
        } else {
            minU = srcRect.x / (float) referenceWidth;

            minV = srcRect.y / (float) referenceHeight;
            maxU = (srcRect.x + srcRect.width) / (float) referenceWidth;

            maxV = (srcRect.y + srcRect.height) / (float) referenceHeight;
        }

        switch (transform) {
            case MIRROR_H -> {
                float tmpU = minU;
                minU = maxU;
                maxU = tmpU;
            }
            case MIRROR_V -> {
                float tmpV = minV;
                minV = maxV;
                maxV = tmpV;
            }
            default -> {
            }
        }

        float x1 = destRect.x;

        float y1 = destRect.y;
        float x2 = x1;
        float y2 = y1;
        if (destRect.width != 0 && destRect.height != 0) {
            x2 += destRect.width;
            y2 += destRect.height;
        } else if (srcRect != null) {
            x2 += srcRect.width;
            y2 += srcRect.height;
        }

        if (blending) {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        } else {
            GlStateManager.disableBlend();
        }

        GlStateManager.color(r / 255.0F, g / 255.0F, b / 255.0F, a / 255.0F);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();
        bufferbuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        bufferbuilder.pos(x1, y2, zOffset).tex(minU, maxV).color(r, g, b, a).endVertex();
        bufferbuilder.pos(x2, y2, zOffset).tex(maxU, maxV).color(r, g, b, a).endVertex();
        bufferbuilder.pos(x2, y1, zOffset).tex(maxU, minV).color(r, g, b, a).endVertex();
        bufferbuilder.pos(x1, y1, zOffset).tex(minU, minV).color(r, g, b, a).endVertex();
        tessellator.draw();

        GlStateManager.color(1, 1, 1, 1);
    }

    public void blit(Gui ignored) {
        blit();
    }
}
