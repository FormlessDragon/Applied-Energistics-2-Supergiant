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
import ae2.client.render.BlockEntityRenderHelper;
import ae2.core.AELog;
import ae2.core.AppEng;
import ae2.tile.misc.TileCrank;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.model.TRSRTransformation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

public class CrankRenderer extends TileEntitySpecialRenderer<TileCrank> {
    private static final ResourceLocation BASE_MODEL = AppEng.makeId("block/crank_base");
    private static final ResourceLocation HANDLE_MODEL = AppEng.makeId("block/crank_handle");

    private IBakedModel baseModel;
    private IBakedModel handleModel;

    private static @Nullable IBakedModel bake(ResourceLocation modelId) {
        try {
            return ModelLoaderRegistry.getModel(modelId)
                                      .bake(TRSRTransformation.identity(), DefaultVertexFormats.BLOCK,
                                          texture -> Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(
                                              texture.toString()));
        } catch (Exception e) {
            AELog.error(e, String.format("Failed to bake crank model %s", modelId));
            return null;
        }
    }

    @Override
    public void render(TileCrank crank, double x, double y, double z, float partialTicks, int destroyStage,
                       float alpha) {
        IBakedModel base = this.getBaseModel();
        IBakedModel handle = this.getHandleModel();
        if (base == null || handle == null) {
            return;
        }

        Tessellator tessellator = Tessellator.getInstance();
        this.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableBlend();
        GlStateManager.disableCull();

        if (Minecraft.isAmbientOcclusionEnabled()) {
            GlStateManager.shadeModel(GL11.GL_SMOOTH);
        } else {
            GlStateManager.shadeModel(GL11.GL_FLAT);
        }

        IBlockState blockState = crank.getWorld().getBlockState(crank.getPos());
        BlockRendererDispatcher dispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();
        BufferBuilder buffer = tessellator.getBuffer();

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, z);
            GlStateManager.translate(0.5F, 0.5F, 0.5F);
            BlockOrientation orientation = BlockOrientation.get(crank);
            BlockEntityRenderHelper.applyOrientation(orientation);
            GlStateManager.translate(-0.5F, -0.5F, -0.5F);

            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            buffer.setTranslation(-crank.getPos().getX(), -crank.getPos().getY(), -crank.getPos().getZ());
            dispatcher.getBlockModelRenderer().renderModel(crank.getWorld(), base, blockState, crank.getPos(), buffer,
                false);
            buffer.setTranslation(0, 0, 0);
            tessellator.draw();

            GlStateManager.translate(0.5F, 0.5F, 0.5F);
            GlStateManager.rotate(-crank.getVisibleRotation(), 0, 0, 1);
            GlStateManager.translate(-0.5F, -0.5F, -0.5F);

            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            buffer.setTranslation(-crank.getPos().getX(), -crank.getPos().getY(), -crank.getPos().getZ());
            dispatcher.getBlockModelRenderer().renderModel(crank.getWorld(), handle, blockState, crank.getPos(), buffer,
                false);
            buffer.setTranslation(0, 0, 0);
            tessellator.draw();
        } finally {
            buffer.setTranslation(0, 0, 0);
            GlStateManager.popMatrix();
            GlStateManager.shadeModel(GL11.GL_FLAT);
            GlStateManager.enableCull();
            GlStateManager.disableBlend();
            RenderHelper.enableStandardItemLighting();
        }
    }

    private IBakedModel getBaseModel() {
        if (this.baseModel == null) {
            this.baseModel = bake(BASE_MODEL);
        }
        return this.baseModel;
    }

    private IBakedModel getHandleModel() {
        if (this.handleModel == null) {
            this.handleModel = bake(HANDLE_MODEL);
        }
        return this.handleModel;
    }
}
