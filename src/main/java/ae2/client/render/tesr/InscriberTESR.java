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
package ae2.client.render.tesr;

import ae2.api.orientation.BlockOrientation;
import ae2.core.AppEng;
import ae2.recipes.handlers.InscriberProcessType;
import ae2.recipes.handlers.InscriberRecipe;
import ae2.tile.misc.TileInscriber;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

public final class InscriberTESR extends TileEntitySpecialRenderer<TileInscriber> {
    private static final float ITEM_RENDER_SCALE = 1.0f / 1.2f;
    private static final ResourceLocation TEXTURE_INSIDE = AppEng.makeId("block/inscriber_inside");

    @Nullable
    private static TextureAtlasSprite textureInside;

    public static void registerTexture(TextureStitchEvent.Pre event) {
        textureInside = event.getMap().registerSprite(TEXTURE_INSIDE);
    }

    private static void applyInscriberOrientation(BlockOrientation orientation) {
        GlStateManager.translate(0.5F, 0.5F, 0.5F);
        GlStateManager.rotate(-orientation.getAngleX(), 1, 0, 0);
        GlStateManager.rotate(-orientation.getAngleY(), 0, 1, 0);
        GlStateManager.rotate(-orientation.getAngleZ(), 0, 0, 1);
        GlStateManager.translate(-0.5F, -0.5F, -0.5F);
    }

    @Override
    public void render(TileInscriber tile, double x, double y, double z, float partialTicks, int destroyStage,
                       float alpha) {
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, z);
            BlockOrientation orientation = BlockOrientation.get(tile);
            applyInscriberOrientation(orientation);

            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableLighting();
            GlStateManager.disableRescaleNormal();

            Minecraft mc = Minecraft.getMinecraft();
            mc.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

            int br = tile.getWorld().getCombinedLight(tile.getPos(), 0);
            int lightX = br % 65536;
            int lightY = br / 65536;
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);

            long absoluteProgress = 0;
            if (tile.isSmash()) {
                absoluteProgress = System.currentTimeMillis() - tile.getClientStart();
                if (absoluteProgress > 800) {
                    absoluteProgress = 800;
                }
            }

            float relativeProgress = absoluteProgress % 800 / 400.0f;
            float progress = relativeProgress;
            if (progress > 1.0f) {
                progress = 1.0f - (progress - 1.0f);
            }
            float press = 0.2f - progress / 5.0f;

            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

            float middle = 0.52f;
            float twoPixels = 2.0f / 16.0f;
            float base = 0.4f;

            TextureAtlasSprite sprite = textureInside;
            if (sprite != null) {
                buffer.pos(twoPixels, middle + press, twoPixels).tex(sprite.getInterpolatedU(2), sprite.getInterpolatedV(13))
                      .endVertex();
                buffer.pos(1.0 - twoPixels, middle + press, twoPixels)
                      .tex(sprite.getInterpolatedU(14), sprite.getInterpolatedV(13)).endVertex();
                buffer.pos(1.0 - twoPixels, middle + press, 1.0 - twoPixels)
                      .tex(sprite.getInterpolatedU(14), sprite.getInterpolatedV(2)).endVertex();
                buffer.pos(twoPixels, middle + press, 1.0 - twoPixels)
                      .tex(sprite.getInterpolatedU(2), sprite.getInterpolatedV(2)).endVertex();

                buffer.pos(twoPixels, middle + base, twoPixels)
                      .tex(sprite.getInterpolatedU(2), sprite.getInterpolatedV(3 - 16 * (press - base))).endVertex();
                buffer.pos(1.0 - twoPixels, middle + base, twoPixels)
                      .tex(sprite.getInterpolatedU(14), sprite.getInterpolatedV(3 - 16 * (press - base))).endVertex();
                buffer.pos(1.0 - twoPixels, middle + press, twoPixels)
                      .tex(sprite.getInterpolatedU(14), sprite.getInterpolatedV(3)).endVertex();
                buffer.pos(twoPixels, middle + press, twoPixels).tex(sprite.getInterpolatedU(2), sprite.getInterpolatedV(3))
                      .endVertex();

                middle -= 0.04f;
                buffer.pos(1.0 - twoPixels, middle - press, twoPixels)
                      .tex(sprite.getInterpolatedU(2), sprite.getInterpolatedV(13)).endVertex();
                buffer.pos(twoPixels, middle - press, twoPixels)
                      .tex(sprite.getInterpolatedU(14), sprite.getInterpolatedV(13)).endVertex();
                buffer.pos(twoPixels, middle - press, 1.0 - twoPixels)
                      .tex(sprite.getInterpolatedU(14), sprite.getInterpolatedV(2)).endVertex();
                buffer.pos(1.0 - twoPixels, middle - press, 1.0 - twoPixels)
                      .tex(sprite.getInterpolatedU(2), sprite.getInterpolatedV(2)).endVertex();

                buffer.pos(1.0 - twoPixels, middle - base, twoPixels)
                      .tex(sprite.getInterpolatedU(2), sprite.getInterpolatedV(3 - 16 * (press - base))).endVertex();
                buffer.pos(twoPixels, middle - base, twoPixels)
                      .tex(sprite.getInterpolatedU(14), sprite.getInterpolatedV(3 - 16 * (press - base))).endVertex();
                buffer.pos(twoPixels, middle - press, twoPixels)
                      .tex(sprite.getInterpolatedU(14), sprite.getInterpolatedV(3)).endVertex();
                buffer.pos(1.0 - twoPixels, middle - press, twoPixels)
                      .tex(sprite.getInterpolatedU(2), sprite.getInterpolatedV(3)).endVertex();
            }

            Tessellator.getInstance().draw();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            int items = 0;
            if (!tile.getVisualStack(0).isEmpty()) {
                items++;
            }
            if (!tile.getVisualStack(1).isEmpty()) {
                items++;
            }
            if (!tile.getVisualStack(2).isEmpty()) {
                items++;
            }

            if (relativeProgress > 1.0f || items == 0) {
                ItemStack is = tile.getVisualStack(3);
                if (is.isEmpty()) {
                    InscriberRecipe recipe = tile.getTask();
                    if (recipe != null) {
                        is = recipe.getResultItem().copy();
                    }
                }
                renderItem(is, 0.0f);
            } else {
                boolean renderPresses = true;
                renderItem(tile.getVisualStack(2), 0.0f);
                if (relativeProgress > 1.0f) {
                    renderPresses = false;
                    InscriberRecipe recipe = tile.getTask();
                    if (recipe != null) {
                        renderPresses = recipe.getProcessType() == InscriberProcessType.INSCRIBE;
                    }
                }
                if (renderPresses) {
                    renderItem(tile.getVisualStack(0), press);
                    renderItem(tile.getVisualStack(1), -press);
                }
            }
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.enableLighting();
            GlStateManager.enableRescaleNormal();
        }
    }

    private void renderItem(ItemStack stack, float yOffset) {
        if (stack.isEmpty()) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(0.5f, 0.5f + yOffset, 0.5f);
        GlStateManager.rotate(90, 1, 0, 0);
        GlStateManager.scale(ITEM_RENDER_SCALE, ITEM_RENDER_SCALE, ITEM_RENDER_SCALE);

        if (!(stack.getItem() instanceof ItemBlock)) {
            GlStateManager.scale(0.5, 0.5, 0.5);
        }

        Minecraft.getMinecraft().getRenderItem().renderItem(stack, ItemCameraTransforms.TransformType.FIXED);
        GlStateManager.popMatrix();
    }
}
