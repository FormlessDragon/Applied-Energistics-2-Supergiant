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

import ae2.client.render.crafting.AssemblerAnimationStatus;
import ae2.client.render.effects.ParticleTypes;
import ae2.core.AELog;
import ae2.core.AppEng;
import ae2.core.AppEngBase;
import ae2.tile.crafting.TileMolecularAssembler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.model.TRSRTransformation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

public class MolecularAssemblerTESR extends TileEntitySpecialRenderer<TileMolecularAssembler> {
    private static final ResourceLocation LIGHTS_MODEL = AppEng.makeId("block/molecular_assembler_lights");

    @Nullable
    private IBakedModel lightsModel;

    @Override
    public void render(TileMolecularAssembler tile, double x, double y, double z, float partialTicks, int destroyStage,
                       float alpha) {
        AssemblerAnimationStatus status = tile.getAnimationStatus();
        if (status != null) {
            if (!Minecraft.getMinecraft().isGamePaused()) {
                if (status.isExpired()) {
                    tile.setAnimationStatus(null);
                }

                status.setAccumulatedTicks(status.getAccumulatedTicks() + partialTicks);
                status.setTicksUntilParticles(status.getTicksUntilParticles() - partialTicks);
            }

            renderStatus(tile, status, x, y, z);
        }

        if (tile.isPowered()) {
            renderPowerLight(tile, x, y, z);
        }
    }

    private void renderStatus(TileMolecularAssembler tile, AssemblerAnimationStatus status, double x, double y,
                              double z) {
        if (status.getTicksUntilParticles() <= 0) {
            status.setTicksUntilParticles(4);

            if (AppEngBase.runtime().shouldAddParticles(tile.getWorld().rand)) {
                for (int i = 0; i < (int) Math.ceil(status.getSpeed() / 5.0); i++) {
                    double offsetX = (((tile.getWorld().rand.nextInt() % 100) * 0.01) - 0.5) * 0.7;
                    double offsetY = (((tile.getWorld().rand.nextInt() % 100) * 0.01) - 0.5) * 0.7;
                    double offsetZ = (((tile.getWorld().rand.nextInt() % 100) * 0.01) - 0.5) * 0.7;
                    ParticleTypes.CRAFTING.spawn(tile.getWorld(),
                        tile.getPos().getX() + 0.5 + offsetX,
                        tile.getPos().getY() + 0.5 + offsetY,
                        tile.getPos().getZ() + 0.5 + offsetZ,
                        -offsetX * 0.2,
                        -offsetY * 0.2,
                        -offsetZ * 0.2,
                        null);
                }
            }
        }

        renderItem(status.getIs(), x, y, z);
    }

    private void renderItem(@Nullable ItemStack stack, double x, double y, double z) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);

        if (!(stack.getItem() instanceof ItemBlock)) {
            GlStateManager.translate(0.0F, -0.3F, 0.0F);
            GlStateManager.scale(0.5F, 0.5F, 0.5F);
        } else {
            GlStateManager.translate(0.0F, -0.2F, 0.0F);
        }

        RenderHelper.enableStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItem(stack, ItemCameraTransforms.TransformType.GROUND);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    private void renderPowerLight(TileMolecularAssembler tile, double x, double y, double z) {
        IBakedModel lights = getLightsModel();
        if (lights == null || tile.getWorld() == null) {
            return;
        }

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

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        buffer.setTranslation(-tile.getPos().getX(), -tile.getPos().getY(), -tile.getPos().getZ());
        dispatcher.getBlockModelRenderer().renderModel(tile.getWorld(), lights, tile.getWorld().getBlockState(tile.getPos()),
            tile.getPos(), buffer, false);
        buffer.setTranslation(0, 0, 0);
        tessellator.draw();

        GlStateManager.popMatrix();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        RenderHelper.enableStandardItemLighting();
    }

    @Nullable
    private IBakedModel getLightsModel() {
        if (this.lightsModel == null) {
            try {
                this.lightsModel = ModelLoaderRegistry.getModel(LIGHTS_MODEL)
                                                      .bake(TRSRTransformation.identity(), DefaultVertexFormats.BLOCK,
                                                          texture -> Minecraft.getMinecraft().getTextureMapBlocks()
                                                                              .getAtlasSprite(texture.toString()));
            } catch (Exception e) {
                AELog.error(e, String.format("Failed to bake molecular assembler lights model %s", LIGHTS_MODEL));
            }
        }

        return this.lightsModel;
    }

}
