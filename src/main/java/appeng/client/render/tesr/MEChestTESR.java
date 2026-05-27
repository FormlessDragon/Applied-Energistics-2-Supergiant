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
package appeng.client.render.tesr;

import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.IOrientationStrategy;
import appeng.client.render.BakedModelUnwrapper;
import appeng.client.render.BlockEntityRenderHelper;
import appeng.client.render.DelegateBakedModel;
import appeng.client.render.model.DriveBakedModel;
import appeng.core.definitions.AEBlocks;
import appeng.tile.storage.TileMEChest;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.Item;
import net.minecraft.util.EnumFacing;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.List;

public class MEChestTESR extends TileEntitySpecialRenderer<TileMEChest> {
    @Nullable
    private static DriveBakedModel getDriveModel() {
        IBlockState state = AEBlocks.DRIVE.block().getDefaultState()
                                          .withProperty(BlockDirectional.FACING, EnumFacing.NORTH)
                                          .withProperty(IOrientationStrategy.SPIN, 0);
        IBakedModel model = Minecraft.getMinecraft()
                                     .getBlockRendererDispatcher()
                                     .getModelForState(state);
        return BakedModelUnwrapper.unwrap(model, DriveBakedModel.class);
    }

    @Override
    public void render(TileMEChest chest, double x, double y, double z, float partialTicks, int destroyStage,
                       float alpha) {
        Item cellItem = chest.getCellItem(0);
        if (cellItem == null || chest.getWorld() == null) {
            return;
        }

        DriveBakedModel driveModel = getDriveModel();
        if (driveModel == null) {
            return;
        }

        BlockOrientation orientation = BlockOrientation.get(chest);
        IBakedModel cellModel = new FaceRotatingModel(driveModel.getCellChassisModel(cellItem), orientation);
        IBlockState blockState = chest.getWorld().getBlockState(chest.getPos());

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        BlockRendererDispatcher dispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();

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

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.translate(0.5F, 0.5F, 0.5F);
        BlockEntityRenderHelper.applyOrientation(orientation);
        GlStateManager.translate(-0.5F, -0.5F, -0.5F);
        GlStateManager.translate(5 / 16.0F, 4 / 16.0F, 0);

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        buffer.setTranslation(-chest.getPos().getX(), -chest.getPos().getY(), -chest.getPos().getZ());
        dispatcher.getBlockModelRenderer().renderModel(chest.getWorld(), cellModel, blockState, chest.getPos(),
            buffer, false);
        buffer.setTranslation(0, 0, 0);
        tessellator.draw();

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        CellLedRenderer.renderLed(chest, 0);
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();

        GlStateManager.popMatrix();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        RenderHelper.enableStandardItemLighting();
    }

    private static final class FaceRotatingModel extends DelegateBakedModel {
        private final BlockOrientation orientation;

        private FaceRotatingModel(IBakedModel base, BlockOrientation orientation) {
            super(base);
            this.orientation = orientation;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            EnumFacing sourceSide = side == null ? null : this.orientation.resultingRotate(side);
            List<BakedQuad> quads = new ObjectArrayList<>(super.getQuads(state, sourceSide, rand));

            for (int i = 0; i < quads.size(); i++) {
                BakedQuad quad = quads.get(i);
                EnumFacing rotatedFace = quad.getFace() == null ? null : this.orientation.rotate(quad.getFace());
                quads.set(i, new BakedQuad(quad.getVertexData(), quad.getTintIndex(), rotatedFace, quad.getSprite(),
                    quad.shouldApplyDiffuseLighting(), quad.getFormat()));
            }

            return quads;
        }
    }
}
