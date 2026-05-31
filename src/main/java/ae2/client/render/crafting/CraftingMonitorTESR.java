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

package ae2.client.render.crafting;

import ae2.client.render.BlockEntityRenderHelper;
import ae2.tile.crafting.TileCraftingMonitor;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;

public class CraftingMonitorTESR extends TileEntitySpecialRenderer<TileCraftingMonitor> {
    @Override
    public void render(TileCraftingMonitor tile, double x, double y, double z, float partialTicks, int destroyStage,
                       float alpha) {
        if (tile == null) {
            return;
        }

        var jobProgress = tile.getJobProgress();
        if (jobProgress == null) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);
        BlockEntityRenderHelper.rotateToFace(tile.getOrientation());
        GlStateManager.translate(0, 0.02, 0.5);
        BlockEntityRenderHelper.renderItem2dWithAmount(
            jobProgress.what(),
            jobProgress.amount(),
            false,
            0.3f,
            -0.18f,
            tile.getColor().contrastTextColor);
        GlStateManager.popMatrix();
    }
}
