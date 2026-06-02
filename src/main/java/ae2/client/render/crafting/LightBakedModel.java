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

import ae2.block.crafting.AbstractCraftingUnitBlock;
import ae2.client.render.cablebus.CubeBuilder;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;

class LightBakedModel extends CraftingCubeBakedModel {
    private final TextureAtlasSprite baseTexture;
    private final TextureAtlasSprite lightTexture;

    LightBakedModel(VertexFormat format, TextureAtlasSprite ringCorner, TextureAtlasSprite ringHor,
                    TextureAtlasSprite ringVer, TextureAtlasSprite baseTexture, TextureAtlasSprite lightTexture) {
        super(format, ringCorner, ringHor, ringVer);
        this.baseTexture = baseTexture;
        this.lightTexture = lightTexture;
    }

    @Override
    protected void addInnerCube(EnumFacing side, IBlockState state, CubeBuilder builder, float x1, float y1, float z1,
                                float x2, float y2, float z2) {
        if (side == EnumFacing.UP || side == EnumFacing.DOWN) {
            builder.setFlipU(side, true);
            builder.setFlipV(side, true);
        }

        builder.setTexture(this.baseTexture);
        builder.addCube(x1, y1, z1, x2, y2, z2);

        builder.setRenderFullBright(state.getValue(AbstractCraftingUnitBlock.POWERED));
        builder.setTexture(this.lightTexture);
        builder.addCube(x1, y1, z1, x2, y2, z2);
        builder.setRenderFullBright(false);
    }
}
