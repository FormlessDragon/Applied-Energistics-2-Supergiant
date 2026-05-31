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

import ae2.api.util.AEColor;
import ae2.block.AEBaseTileBlock;
import ae2.block.crafting.AbstractCraftingUnitBlock;
import ae2.block.crafting.CraftingMonitorBlock;
import ae2.client.render.cablebus.CubeBuilder;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;

class MonitorBakedModel extends CraftingCubeBakedModel {
    private final TextureAtlasSprite chassisTexture;
    private final TextureAtlasSprite baseTexture;
    private final TextureAtlasSprite lightDarkTexture;
    private final TextureAtlasSprite lightMediumTexture;
    private final TextureAtlasSprite lightBrightTexture;

    MonitorBakedModel(VertexFormat format, TextureAtlasSprite ringCorner, TextureAtlasSprite ringHor,
                      TextureAtlasSprite ringVer, TextureAtlasSprite chassisTexture, TextureAtlasSprite baseTexture,
                      TextureAtlasSprite lightDarkTexture, TextureAtlasSprite lightMediumTexture,
                      TextureAtlasSprite lightBrightTexture) {
        super(format, ringCorner, ringHor, ringVer);
        this.chassisTexture = chassisTexture;
        this.baseTexture = baseTexture;
        this.lightDarkTexture = lightDarkTexture;
        this.lightMediumTexture = lightMediumTexture;
        this.lightBrightTexture = lightBrightTexture;
    }

    private static AEColor getColor(IBlockState state) {
        if (state instanceof IExtendedBlockState) {
            AEColor color = ((IExtendedBlockState) state).getValue(CraftingMonitorBlock.COLOR);
            if (color != null) {
                return color;
            }
        }
        return AEColor.TRANSPARENT;
    }

    private static EnumFacing getForward(IBlockState state) {
        if (state instanceof IExtendedBlockState) {
            EnumFacing forward = ((IExtendedBlockState) state).getValue(AEBaseTileBlock.FORWARD);
            if (forward != null) {
                return forward;
            }
        }

        return EnumFacing.NORTH;
    }

    @Override
    protected void addInnerCube(EnumFacing side, IBlockState state, CubeBuilder builder, float x1, float y1, float z1,
                                float x2, float y2, float z2) {
        EnumFacing forward = getForward(state);

        if (side != forward) {
            builder.setTexture(this.chassisTexture);
            builder.addCube(x1, y1, z1, x2, y2, z2);
            return;
        }

        builder.setTexture(this.baseTexture);
        builder.addCube(x1, y1, z1, x2, y2, z2);

        AEColor color = getColor(state);
        boolean powered = state.getValue(AbstractCraftingUnitBlock.POWERED);

        builder.setRenderFullBright(powered);

        builder.setColorRGB(color.whiteVariant);
        builder.setTexture(this.lightBrightTexture);
        builder.addCube(x1, y1, z1, x2, y2, z2);

        builder.setColorRGB(color.mediumVariant);
        builder.setTexture(this.lightMediumTexture);
        builder.addCube(x1, y1, z1, x2, y2, z2);

        builder.setColorRGB(color.blackVariant);
        builder.setTexture(this.lightDarkTexture);
        builder.addCube(x1, y1, z1, x2, y2, z2);

        builder.setRenderFullBright(false);
        builder.setColorRGB(1, 1, 1);
    }
}
