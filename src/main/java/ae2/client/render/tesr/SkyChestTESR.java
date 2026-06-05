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
import ae2.api.orientation.RelativeSide;
import ae2.block.storage.SkyChestBlock;
import ae2.block.storage.SkyChestBlock.SkyChestType;
import ae2.client.render.BlockEntityRenderHelper;
import ae2.core.AppEng;
import ae2.tile.storage.TileSkyChest;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class SkyChestTESR extends TileEntitySpecialRenderer<TileSkyChest> {
    private static final ResourceLocation TEXTURE_STONE = AppEng.makeId("block/skychest");
    private static final ResourceLocation TEXTURE_BLOCK = AppEng.makeId("block/skyblockchest");

    private static SkyChestType getChestType(TileSkyChest te) {
        if (te == null) {
            return SkyChestType.BLOCK;
        }

        Block blockType = te.getBlockType();
        if (blockType instanceof SkyChestBlock s) {
            return s.type;
        }
        return SkyChestType.BLOCK;
    }

    @Override
    public void render(TileSkyChest te, double x, double y, double z, float partialTicks, int destroyStage,
                       float alpha) {
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(515);
        GlStateManager.depthMask(true);

        if (destroyStage >= 0) {
            this.bindTexture(DESTROY_STAGES[destroyStage]);
            GlStateManager.matrixMode(5890);
            GlStateManager.pushMatrix();
            GlStateManager.scale(4.0F, 4.0F, 1.0F);
            GlStateManager.translate(0.0625F, 0.0625F, 0.0625F);
            GlStateManager.matrixMode(5888);
        } else {
            this.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        }

        GlStateManager.pushMatrix();
        GlStateManager.enableRescaleNormal();
        GlStateManager.disableCull();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.translate((float) x, (float) y, (float) z);
        if (te != null) {
            GlStateManager.translate(0.5F, 0.5F, 0.5F);
            EnumFacing forward = te.getOrientation().getSide(RelativeSide.FRONT);
            EnumFacing up = te.getOrientation().getSide(RelativeSide.TOP);
            BlockOrientation orientation = BlockOrientation.get(forward, up);
            BlockEntityRenderHelper.applyOrientation(orientation);
            GlStateManager.translate(-0.5F, -0.5F, -0.5F);
        }
        float f = te != null ? te.getPrevLidAngle() + (te.getLidAngle() - te.getPrevLidAngle()) * partialTicks : 0;
        f = 1.0F - f;
        f = 1.0F - f * f * f;

        TextureAtlasSprite sprite = destroyStage >= 0 ? null : getTexture(te);
        renderBase(sprite);
        renderLidAndKnob(sprite, f);

        GlStateManager.enableCull();
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        if (destroyStage >= 0) {
            GlStateManager.matrixMode(5890);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(5888);
        }
    }

    private static TextureAtlasSprite getTexture(TileSkyChest te) {
        ResourceLocation texture = getChestType(te) == SkyChestType.STONE ? TEXTURE_STONE : TEXTURE_BLOCK;
        return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(texture.toString());
    }

    private static void renderBase(TextureAtlasSprite sprite) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_NORMAL);

        renderFace(buffer, sprite, Face.NORTH, 1, 0, 1, 15, 10, 15, 3.5F, 8.25F, 7.0F, 10.75F, 180);
        renderFace(buffer, sprite, Face.EAST, 1, 0, 1, 15, 10, 15, 0.0F, 8.25F, 3.5F, 10.75F, 180);
        renderFace(buffer, sprite, Face.SOUTH, 1, 0, 1, 15, 10, 15, 10.5F, 8.25F, 14.0F, 10.75F, 180);
        renderFace(buffer, sprite, Face.WEST, 1, 0, 1, 15, 10, 15, 7.0F, 8.25F, 10.5F, 10.75F, 180);
        renderFace(buffer, sprite, Face.UP, 1, 0, 1, 15, 10, 15, 3.5F, 0.0F, 7.0F, 3.5F, 0);
        renderFace(buffer, sprite, Face.DOWN, 1, 0, 1, 15, 10, 15, 7.0F, 0.0F, 10.5F, 3.5F, 0);

        tessellator.draw();
    }

    private static void renderLidAndKnob(TextureAtlasSprite sprite, float lidAngle) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, 10.0F / 16.0F, 15.0F / 16.0F);
        GlStateManager.rotate(lidAngle * 90.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.translate(0.0F, -10.0F / 16.0F, -15.0F / 16.0F);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_NORMAL);

        renderFace(buffer, sprite, Face.NORTH, 1, 10, 1, 15, 15, 15, 3.5F, 3.5F, 7.0F, 4.75F, 180);
        renderFace(buffer, sprite, Face.EAST, 1, 10, 1, 15, 15, 15, 0.0F, 3.5F, 3.5F, 4.75F, 180);
        renderFace(buffer, sprite, Face.SOUTH, 1, 10, 1, 15, 15, 15, 10.5F, 3.5F, 14.0F, 4.75F, 180);
        renderFace(buffer, sprite, Face.WEST, 1, 10, 1, 15, 15, 15, 7.0F, 3.5F, 10.5F, 4.75F, 180);
        renderFace(buffer, sprite, Face.UP, 1, 10, 1, 15, 15, 15, 7.0F, 0.0F, 10.5F, 3.5F, 270);
        renderFace(buffer, sprite, Face.DOWN, 1, 10, 1, 15, 15, 15, 7.0F, 0.0F, 10.5F, 3.5F, 0);

        renderFace(buffer, sprite, Face.NORTH, 7, 7, 0, 9, 11, 1, 0.25F, 0.25F, 0.75F, 1.25F, 0);
        renderFace(buffer, sprite, Face.EAST, 7, 7, 0, 9, 11, 1, 0.0F, 0.25F, 0.25F, 1.25F, 0);
        renderFace(buffer, sprite, Face.WEST, 7, 7, 0, 9, 11, 1, 0.75F, 0.25F, 1.5F, 1.25F, 0);
        renderFace(buffer, sprite, Face.UP, 7, 7, 0, 9, 11, 1, 0.25F, 0.0F, 0.75F, 0.25F, 0);
        renderFace(buffer, sprite, Face.DOWN, 7, 7, 0, 9, 11, 1, 0.75F, 0.0F, 1.5F, 0.25F, 0);

        tessellator.draw();
        GlStateManager.popMatrix();
    }

    private static void renderFace(BufferBuilder buffer, TextureAtlasSprite sprite, Face face,
                                   float x1, float y1, float z1, float x2, float y2, float z2,
                                   float u1, float v1, float u2, float v2, int rotation) {
        float minX = x1 / 16.0F;
        float minY = y1 / 16.0F;
        float minZ = z1 / 16.0F;
        float maxX = x2 / 16.0F;
        float maxY = y2 / 16.0F;
        float maxZ = z2 / 16.0F;

        int uvRotation = Math.floorMod(rotation / 90, 4);
        switch (face) {
            case NORTH -> {
                vertex(buffer, sprite, minX, maxY, minZ, u1, v1, u2, v2, uvRotation, 0, face);
                vertex(buffer, sprite, maxX, maxY, minZ, u1, v1, u2, v2, uvRotation, 1, face);
                vertex(buffer, sprite, maxX, minY, minZ, u1, v1, u2, v2, uvRotation, 2, face);
                vertex(buffer, sprite, minX, minY, minZ, u1, v1, u2, v2, uvRotation, 3, face);
            }
            case EAST -> {
                vertex(buffer, sprite, maxX, maxY, minZ, u1, v1, u2, v2, uvRotation, 0, face);
                vertex(buffer, sprite, maxX, maxY, maxZ, u1, v1, u2, v2, uvRotation, 1, face);
                vertex(buffer, sprite, maxX, minY, maxZ, u1, v1, u2, v2, uvRotation, 2, face);
                vertex(buffer, sprite, maxX, minY, minZ, u1, v1, u2, v2, uvRotation, 3, face);
            }
            case SOUTH -> {
                vertex(buffer, sprite, maxX, maxY, maxZ, u1, v1, u2, v2, uvRotation, 0, face);
                vertex(buffer, sprite, minX, maxY, maxZ, u1, v1, u2, v2, uvRotation, 1, face);
                vertex(buffer, sprite, minX, minY, maxZ, u1, v1, u2, v2, uvRotation, 2, face);
                vertex(buffer, sprite, maxX, minY, maxZ, u1, v1, u2, v2, uvRotation, 3, face);
            }
            case WEST -> {
                vertex(buffer, sprite, minX, maxY, maxZ, u1, v1, u2, v2, uvRotation, 0, face);
                vertex(buffer, sprite, minX, maxY, minZ, u1, v1, u2, v2, uvRotation, 1, face);
                vertex(buffer, sprite, minX, minY, minZ, u1, v1, u2, v2, uvRotation, 2, face);
                vertex(buffer, sprite, minX, minY, maxZ, u1, v1, u2, v2, uvRotation, 3, face);
            }
            case UP -> {
                vertex(buffer, sprite, minX, maxY, minZ, u1, v1, u2, v2, uvRotation, 0, face);
                vertex(buffer, sprite, minX, maxY, maxZ, u1, v1, u2, v2, uvRotation, 1, face);
                vertex(buffer, sprite, maxX, maxY, maxZ, u1, v1, u2, v2, uvRotation, 2, face);
                vertex(buffer, sprite, maxX, maxY, minZ, u1, v1, u2, v2, uvRotation, 3, face);
            }
            case DOWN -> {
                vertex(buffer, sprite, minX, minY, maxZ, u1, v1, u2, v2, uvRotation, 0, face);
                vertex(buffer, sprite, minX, minY, minZ, u1, v1, u2, v2, uvRotation, 1, face);
                vertex(buffer, sprite, maxX, minY, minZ, u1, v1, u2, v2, uvRotation, 2, face);
                vertex(buffer, sprite, maxX, minY, maxZ, u1, v1, u2, v2, uvRotation, 3, face);
            }
        }
    }

    private static void vertex(BufferBuilder buffer, TextureAtlasSprite sprite, float x, float y, float z,
                               float u1, float v1, float u2, float v2, int uvRotation, int uvIndex, Face face) {
        int rotatedUvIndex = (uvIndex + uvRotation) & 3;
        float u = rotatedUvIndex == 0 || rotatedUvIndex == 3 ? u1 : u2;
        float v = rotatedUvIndex == 0 || rotatedUvIndex == 1 ? v2 : v1;
        buffer.pos(x, y, z)
              .tex(mapU(sprite, u), mapV(sprite, v))
              .normal(face.normalX, face.normalY, face.normalZ)
              .endVertex();
    }

    private static double mapU(TextureAtlasSprite sprite, float u) {
        return sprite == null ? u / 16.0F : sprite.getInterpolatedU(u);
    }

    private static double mapV(TextureAtlasSprite sprite, float v) {
        return sprite == null ? v / 16.0F : sprite.getInterpolatedV(v);
    }

    private enum Face {
        NORTH(0.0F, 0.0F, -1.0F),
        EAST(1.0F, 0.0F, 0.0F),
        SOUTH(0.0F, 0.0F, 1.0F),
        WEST(-1.0F, 0.0F, 0.0F),
        UP(0.0F, 1.0F, 0.0F),
        DOWN(0.0F, -1.0F, 0.0F);

        private final float normalX;
        private final float normalY;
        private final float normalZ;

        Face(float normalX, float normalY, float normalZ) {
            this.normalX = normalX;
            this.normalY = normalY;
            this.normalZ = normalZ;
        }
    }
}
