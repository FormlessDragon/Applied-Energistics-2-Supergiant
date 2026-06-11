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

package ae2.client.render;

import ae2.api.client.AEKeyRendering;
import ae2.api.orientation.BlockOrientation;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AmountFormat;
import ae2.core.localization.GuiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.BufferUtils;

import javax.vecmath.Matrix4f;
import java.nio.FloatBuffer;

/**
 * Helper methods for rendering block entities.
 */
@SideOnly(Side.CLIENT)
public final class BlockEntityRenderHelper {
    private static final ThreadLocal<FloatBuffer> GL_MATRIX_BUFFER = ThreadLocal.withInitial(
        () -> BufferUtils.createFloatBuffer(16));

    private BlockEntityRenderHelper() {
    }

    /**
     * Rotate the current coordinate system, so it is on the face of the given block side. This can be used to render on
     * the given face as if it was a 2D canvas, where x+ is facing right and y+ is facing up.
     */
    public static void rotateToFace(BlockOrientation orientation) {
        applyOrientation(orientation);
        GlStateManager.rotate(180.0f, 0.0f, 1.0f, 0.0f);
    }

    public static void moveToFace(EnumFacing face) {
        GlStateManager.translate(face.getXOffset() * 0.50, face.getYOffset() * 0.50, face.getZOffset() * 0.50);
    }

    public static void applyOrientation(BlockOrientation orientation) {
        GlStateManager.multMatrix(toGlMatrix(orientation.getTransformation().getMatrix()));
    }

    /**
     * Render a resource key in 2D on the currently selected block face.
     */
    public static void renderItem2d(AEKey what, float scale) {
        World level = Minecraft.getMinecraft().world;
        if (what == null || level == null) {
            return;
        }

        GlStateManager.pushMatrix();
        try {
            GlStateManager.scale(1.0f, -1.0f, 1.0f);
            AEKeyRendering.drawOnBlockFace(what, scale, 0xF000F0, level);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    /**
     * Render a resource key in 2D and the given text below it.
     *
     * @param spacing Specifies how far apart the resource icon and amount are rendered.
     */
    public static void renderItem2dWithAmount(AEKey what, long amount, boolean canCraft, float itemScale,
                                              float spacing, int textColor) {
        renderItem2d(what, itemScale);

        String renderedAmount = amount == 0 && canCraft ? GuiText.CraftMonitorCraft.getLocal()
            : what.formatAmount(amount, AmountFormat.SLOT);
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        int width = fontRenderer.getStringWidth(renderedAmount);

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(0.0f, spacing, 0.02f);
            GlStateManager.scale(1.0f / 62.0f, -1.0f / 62.0f, 1.0f / 62.0f);
            GlStateManager.scale(0.5f, 0.5f, 1.0f);
            GlStateManager.translate(-0.5f * width, 0.0f, 0.5f);
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            fontRenderer.drawString(renderedAmount, 0, 0, textColor);
        } finally {
            GlStateManager.enableDepth();
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }
    }

    private static FloatBuffer toGlMatrix(Matrix4f matrix) {
        FloatBuffer buffer = GL_MATRIX_BUFFER.get();
        buffer.clear();
        buffer.put(matrix.m00).put(matrix.m10).put(matrix.m20).put(matrix.m30);
        buffer.put(matrix.m01).put(matrix.m11).put(matrix.m21).put(matrix.m31);
        buffer.put(matrix.m02).put(matrix.m12).put(matrix.m22).put(matrix.m32);
        buffer.put(matrix.m03).put(matrix.m13).put(matrix.m23).put(matrix.m33);
        buffer.flip();
        return buffer;
    }
}
