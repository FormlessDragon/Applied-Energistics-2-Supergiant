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
import ae2.client.render.model.DriveBakedModel;
import ae2.tile.storage.TileDrive;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;

import javax.vecmath.Vector3f;

public class DriveLedTESR extends TileEntitySpecialRenderer<TileDrive> {
    @Override
    public void render(TileDrive te, double x, double y, double z, float partialTicks, int destroyStage,
                       float alpha) {
        if (te.getCellCount() != 10) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.translate(0.5F, 0.5F, 0.5F);

        BlockOrientation orientation = BlockOrientation.get(te);
        BlockEntityRenderHelper.applyOrientation(orientation);
        GlStateManager.translate(-0.5F, -0.5F, -0.5F);

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        Vector3f slotTranslation = new Vector3f();
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 2; col++) {
                GlStateManager.pushMatrix();
                DriveBakedModel.getSlotOrigin(row, col, slotTranslation);
                GlStateManager.translate(slotTranslation.x, slotTranslation.y, slotTranslation.z);
                CellLedRenderer.renderLed(te, row * 2 + col);
                GlStateManager.popMatrix();
            }
        }

        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }
}
