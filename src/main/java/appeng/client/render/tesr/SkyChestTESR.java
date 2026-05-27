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
import appeng.api.orientation.RelativeSide;
import appeng.block.storage.SkyChestBlock;
import appeng.block.storage.SkyChestBlock.SkyChestType;
import appeng.client.render.BlockEntityRenderHelper;
import appeng.core.AppEng;
import appeng.tile.storage.TileSkyChest;
import net.minecraft.block.Block;
import net.minecraft.client.model.ModelChest;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

public class SkyChestTESR extends TileEntitySpecialRenderer<TileSkyChest> {
    private static final ResourceLocation TEXTURE_STONE = AppEng.makeId("textures/models/skychest.png");
    private static final ResourceLocation TEXTURE_BLOCK = AppEng.makeId("textures/models/skyblockchest.png");

    private final ModelChest simpleChest = new ModelChest();

    private static SkyChestType getChestType(TileSkyChest te) {
        if (te == null) {
            return SkyChestType.BLOCK;
        }

        Block blockType = te.getBlockType();
        if (blockType instanceof SkyChestBlock) {
            return ((SkyChestBlock) blockType).type;
        }
        return SkyChestType.BLOCK;
    }

    private static EnumFacing swapNorthSouth(EnumFacing side) {
        return switch (side) {
            case NORTH -> EnumFacing.SOUTH;
            case SOUTH -> EnumFacing.NORTH;
            default -> side;
        };
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
            this.bindTexture(getChestType(te) == SkyChestType.STONE ? TEXTURE_STONE : TEXTURE_BLOCK);
        }

        GlStateManager.pushMatrix();
        GlStateManager.enableRescaleNormal();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.translate((float) x, (float) y + 1.0F, (float) z + 1.0F);
        GlStateManager.scale(1.0F, -1.0F, -1.0F);
        if (te != null) {
            GlStateManager.translate(0.5F, 0.5F, 0.5F);
            EnumFacing forward = swapNorthSouth(te.getOrientation().getSide(RelativeSide.FRONT));
            EnumFacing up = swapNorthSouth(te.getOrientation().getSide(RelativeSide.TOP));
            BlockOrientation orientation = BlockOrientation.get(forward, up);
            BlockEntityRenderHelper.applyOrientation(orientation);
            GlStateManager.translate(-0.5F, -0.5F, -0.5F);
        }
        float f = te != null ? te.getPrevLidAngle() + (te.getLidAngle() - te.getPrevLidAngle()) * partialTicks : 0;
        f = 1.0F - f;
        f = 1.0F - f * f * f;
        this.simpleChest.chestLid.rotateAngleX = -(f * ((float) Math.PI / 2F));
        this.simpleChest.renderAll();
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        if (destroyStage >= 0) {
            GlStateManager.matrixMode(5890);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(5888);
        }
    }
}
