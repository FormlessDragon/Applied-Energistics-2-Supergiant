/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
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

package appeng.block.paint;

import appeng.client.render.cablebus.CubeBuilder;
import appeng.core.AppEng;
import appeng.helpers.Splotch;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.property.IExtendedBlockState;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Renders paint blocks, which render multiple "splotches" that have been applied to the sides of adjacent blocks using
 * a matter cannon with paintballs.
 */
class PaintBakedModel implements IBakedModel {

    private static final ResourceLocation TEXTURE_PAINT1 = AppEng.makeId("block/paint1");
    private static final ResourceLocation TEXTURE_PAINT2 = AppEng.makeId("block/paint2");
    private static final ResourceLocation TEXTURE_PAINT3 = AppEng.makeId("block/paint3");

    private final VertexFormat vertexFormat;
    private final TextureAtlasSprite[] textures;

    PaintBakedModel(VertexFormat vertexFormat, Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        this.vertexFormat = vertexFormat;
        this.textures = new TextureAtlasSprite[]{
            bakedTextureGetter.apply(TEXTURE_PAINT1),
            bakedTextureGetter.apply(TEXTURE_PAINT2),
            bakedTextureGetter.apply(TEXTURE_PAINT3)
        };
    }

    static List<ResourceLocation> getRequiredTextures() {
        return ImmutableList.of(TEXTURE_PAINT1, TEXTURE_PAINT2, TEXTURE_PAINT3);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        if (side != null) {
            return Collections.emptyList();
        }

        if (!(state instanceof IExtendedBlockState)) {
            List<BakedQuad> quads = new ObjectArrayList<>(1);
            CubeBuilder builder = new CubeBuilder(this.vertexFormat, quads);
            builder.setTexture(this.textures[0]);
            builder.addCube(0, 0, 0, 16, 16, 16);
            return quads;
        }

        PaintSplotches splotchesState = ((IExtendedBlockState) state).getValue(PaintSplotchesBlock.SPLOTCHES);
        if (splotchesState == null) {
            return Collections.emptyList();
        }

        List<Splotch> splotches = splotchesState.getSplotches();
        CubeBuilder builder = new CubeBuilder(this.vertexFormat);

        float offsetConstant = 0.001f;
        for (Splotch s : splotches) {
            if (s.isLumen()) {
                builder.setColorRGB(s.getColor().whiteVariant);
                builder.setRenderFullBright(true);
            } else {
                builder.setColorRGB(s.getColor().mediumVariant);
                builder.setRenderFullBright(false);
            }

            float offset = offsetConstant;
            offsetConstant += 0.001f;

            final float buffer = 0.1f;
            float posX = MathHelper.clamp(s.x(), buffer, 1.0f - buffer);
            float posY = MathHelper.clamp(s.y(), buffer, 1.0f - buffer);

            TextureAtlasSprite texture = this.textures[s.getSeed() % this.textures.length];
            builder.setTexture(texture);
            builder.setCustomUv(s.getSide().getOpposite(), 0, 0, 16, 16);

            switch (s.getSide()) {
                case UP -> {
                    offset = 1.0f - offset;
                    builder.addQuad(EnumFacing.DOWN, posX - buffer, offset, posY - buffer, posX + buffer, offset,
                        posY + buffer);
                }
                case DOWN -> builder.addQuad(EnumFacing.UP, posX - buffer, offset, posY - buffer, posX + buffer,
                    offset, posY + buffer);
                case EAST -> {
                    offset = 1.0f - offset;
                    builder.addQuad(EnumFacing.WEST, offset, posX - buffer, posY - buffer, offset, posX + buffer,
                        posY + buffer);
                }
                case WEST -> builder.addQuad(EnumFacing.EAST, offset, posX - buffer, posY - buffer, offset,
                    posX + buffer, posY + buffer);
                case SOUTH -> {
                    offset = 1.0f - offset;
                    builder.addQuad(EnumFacing.NORTH, posX - buffer, posY - buffer, offset, posX + buffer,
                        posY + buffer, offset);
                }
                case NORTH -> builder.addQuad(EnumFacing.SOUTH, posX - buffer, posY - buffer, offset, posX + buffer,
                    posY + buffer, offset);
                default -> {
                }
            }
        }

        return builder.getOutput();
    }

    @Override
    public boolean isAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean isBuiltInRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return this.textures[0];
    }

    @Override
    @SuppressWarnings("deprecation")
    public ItemCameraTransforms getItemCameraTransforms() {
        return ItemCameraTransforms.DEFAULT;
    }

    @Override
    public ItemOverrideList getOverrides() {
        return ItemOverrideList.NONE;
    }
}
