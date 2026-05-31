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

package ae2.block.qnb;

import ae2.client.render.DelegateBakedModel;
import ae2.client.render.cablebus.CubeBuilder;
import ae2.core.AppEng;
import ae2.core.definitions.AEBlocks;
import com.google.common.collect.ImmutableList;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.property.IExtendedBlockState;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

class QnbFormedBakedModel extends DelegateBakedModel {

    private static final ResourceLocation TEXTURE_LINK = AppEng.makeId("block/quantum_link");
    private static final ResourceLocation TEXTURE_RING = AppEng.makeId("block/quantum_ring");
    private static final ResourceLocation TEXTURE_RING_LIGHT = AppEng.makeId("block/quantum_ring_light");
    private static final ResourceLocation TEXTURE_RING_LIGHT_CORNER = AppEng.makeId("block/quantum_ring_light_corner");
    private static final ResourceLocation TEXTURE_CABLE_GLASS = AppEng.makeId("part/cable/glass/transparent");
    private static final ResourceLocation TEXTURE_COVERED_CABLE = AppEng.makeId("part/cable/covered/transparent");

    private static final float DEFAULT_RENDER_MIN = 2.0f;
    private static final float DEFAULT_RENDER_MAX = 14.0f;

    private final VertexFormat format;
    private final Block linkBlock;
    private final TextureAtlasSprite linkTexture;
    private final TextureAtlasSprite ringTexture;
    private final TextureAtlasSprite glassCableTexture;
    private final TextureAtlasSprite coveredCableTexture;
    private final TextureAtlasSprite lightTexture;
    private final TextureAtlasSprite lightCornerTexture;

    QnbFormedBakedModel(IBakedModel baseModel, VertexFormat format,
                        Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        super(baseModel);
        this.format = format;
        this.linkBlock = AEBlocks.QUANTUM_LINK.block();
        this.linkTexture = bakedTextureGetter.apply(TEXTURE_LINK);
        this.ringTexture = bakedTextureGetter.apply(TEXTURE_RING);
        this.glassCableTexture = bakedTextureGetter.apply(TEXTURE_CABLE_GLASS);
        this.coveredCableTexture = bakedTextureGetter.apply(TEXTURE_COVERED_CABLE);
        this.lightTexture = bakedTextureGetter.apply(TEXTURE_RING_LIGHT);
        this.lightCornerTexture = bakedTextureGetter.apply(TEXTURE_RING_LIGHT_CORNER);
    }

    @Nullable
    private static QnbFormedState getFormedState(@Nullable IBlockState state) {
        if (!(state instanceof IExtendedBlockState)) {
            return null;
        }

        return ((IExtendedBlockState) state).getValue(QnbFormedState.PROPERTY);
    }

    static List<ResourceLocation> getRequiredTextures() {
        return ImmutableList.of(
            TEXTURE_LINK,
            TEXTURE_RING,
            TEXTURE_CABLE_GLASS,
            TEXTURE_COVERED_CABLE,
            TEXTURE_RING_LIGHT,
            TEXTURE_RING_LIGHT_CORNER);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        if (side != null) {
            return Collections.emptyList();
        }

        BlockRenderLayer layer = MinecraftForgeClient.getRenderLayer();
        if (layer != null && layer != BlockRenderLayer.CUTOUT) {
            return Collections.emptyList();
        }

        QnbFormedState formedState = getFormedState(state);
        if (formedState == null || state == null) {
            return getBaseModel().getQuads(state, side, rand);
        }

        return getQuads(formedState, state);
    }

    private List<BakedQuad> getQuads(QnbFormedState formedState, IBlockState state) {
        CubeBuilder builder = new CubeBuilder(this.format);

        if (state.getBlock() == this.linkBlock) {
            Set<EnumFacing> sides = formedState.adjacentQuantumBridges();
            this.renderCableAt(builder, 0.11f * 16, this.glassCableTexture, 0.141f * 16, sides);
            this.renderCableAt(builder, 0.188f * 16, this.coveredCableTexture, 0.1875f * 16, sides);

            builder.setTexture(this.linkTexture);
            builder.addCube(DEFAULT_RENDER_MIN, DEFAULT_RENDER_MIN, DEFAULT_RENDER_MIN,
                DEFAULT_RENDER_MAX, DEFAULT_RENDER_MAX, DEFAULT_RENDER_MAX);
        } else if (formedState.corner()) {
            this.renderCableAt(builder, 0.188f * 16, this.coveredCableTexture, 0.05f * 16,
                formedState.adjacentQuantumBridges());

            builder.setTexture(this.ringTexture);
            builder.addCube(DEFAULT_RENDER_MIN, DEFAULT_RENDER_MIN, DEFAULT_RENDER_MIN,
                DEFAULT_RENDER_MAX, DEFAULT_RENDER_MAX, DEFAULT_RENDER_MAX);

            if (formedState.powered()) {
                builder.setTexture(this.lightCornerTexture);
                builder.setEmissiveMaterial(true);
                for (EnumFacing facing : EnumFacing.values()) {
                    float xOffset = Math.abs(facing.getXOffset() * 0.01f);
                    float yOffset = Math.abs(facing.getYOffset() * 0.01f);
                    float zOffset = Math.abs(facing.getZOffset() * 0.01f);

                    builder.setDrawFaces(EnumSet.of(facing));
                    builder.addCube(DEFAULT_RENDER_MIN - xOffset, DEFAULT_RENDER_MIN - yOffset,
                        DEFAULT_RENDER_MIN - zOffset, DEFAULT_RENDER_MAX + xOffset,
                        DEFAULT_RENDER_MAX + yOffset, DEFAULT_RENDER_MAX + zOffset);
                }
                builder.setEmissiveMaterial(false);
            }
        } else {
            builder.setTexture(this.ringTexture);
            builder.addCube(0, DEFAULT_RENDER_MIN, DEFAULT_RENDER_MIN, 16, DEFAULT_RENDER_MAX, DEFAULT_RENDER_MAX);
            builder.addCube(DEFAULT_RENDER_MIN, 0, DEFAULT_RENDER_MIN, DEFAULT_RENDER_MAX, 16, DEFAULT_RENDER_MAX);
            builder.addCube(DEFAULT_RENDER_MIN, DEFAULT_RENDER_MIN, 0, DEFAULT_RENDER_MAX, DEFAULT_RENDER_MAX, 16);

            if (formedState.powered()) {
                builder.setTexture(this.lightTexture);
                builder.setEmissiveMaterial(true);
                for (EnumFacing facing : EnumFacing.values()) {
                    float xOffset = Math.abs(facing.getXOffset() * 0.01f);
                    float yOffset = Math.abs(facing.getYOffset() * 0.01f);
                    float zOffset = Math.abs(facing.getZOffset() * 0.01f);

                    builder.setDrawFaces(EnumSet.of(facing));
                    builder.addCube(-xOffset, -yOffset, -zOffset,
                        16 + xOffset, 16 + yOffset, 16 + zOffset);
                }
                builder.setEmissiveMaterial(false);
            }
        }

        return builder.getOutput();
    }

    private void renderCableAt(CubeBuilder builder, float thickness, TextureAtlasSprite texture, float pull,
                               Set<EnumFacing> connections) {
        builder.setTexture(texture);

        if (connections.contains(EnumFacing.WEST)) {
            builder.addCube(0, 8 - thickness, 8 - thickness, 8 - thickness - pull, 8 + thickness, 8 + thickness);
        }

        if (connections.contains(EnumFacing.EAST)) {
            builder.addCube(8 + thickness + pull, 8 - thickness, 8 - thickness, 16, 8 + thickness, 8 + thickness);
        }

        if (connections.contains(EnumFacing.NORTH)) {
            builder.addCube(8 - thickness, 8 - thickness, 0, 8 + thickness, 8 + thickness, 8 - thickness - pull);
        }

        if (connections.contains(EnumFacing.SOUTH)) {
            builder.addCube(8 - thickness, 8 - thickness, 8 + thickness + pull, 8 + thickness, 8 + thickness, 16);
        }

        if (connections.contains(EnumFacing.DOWN)) {
            builder.addCube(8 - thickness, 0, 8 - thickness, 8 + thickness, 8 - thickness - pull, 8 + thickness);
        }

        if (connections.contains(EnumFacing.UP)) {
            builder.addCube(8 - thickness, 8 + thickness + pull, 8 - thickness, 8 + thickness, 16, 8 + thickness);
        }
    }
}
