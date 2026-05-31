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

package ae2.client.render.tesr.spatial;

import ae2.block.spatial.SpatialPylonBlock;
import ae2.client.render.cablebus.CubeBuilder;
import ae2.me.cluster.implementations.SpatialPylonCluster;
import ae2.tile.spatial.TileSpatialPylon;
import com.google.common.collect.ImmutableMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.property.IExtendedBlockState;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The baked model that will be used for rendering the spatial pylon.
 */
class SpatialPylonBakedModel implements IBakedModel {
    private final Map<SpatialPylonTextureType, TextureAtlasSprite> textures;

    SpatialPylonBakedModel(Map<SpatialPylonTextureType, TextureAtlasSprite> textures) {
        this.textures = ImmutableMap.copyOf(textures);
    }

    private static SpatialPylonTextureType getTextureTypeFromSideOutside(TileSpatialPylon.ClientState state,
                                                                         EnumFacing ori, EnumFacing dir) {
        if (ori == dir || ori.getOpposite() == dir) {
            return SpatialPylonTextureType.BASE;
        }

        if (state.axisPosition() == TileSpatialPylon.AxisPosition.MIDDLE) {
            return SpatialPylonTextureType.BASE_SPANNED;
        } else if (state.axisPosition() == TileSpatialPylon.AxisPosition.START
            || state.axisPosition() == TileSpatialPylon.AxisPosition.END) {
            return SpatialPylonTextureType.BASE_END;
        }

        return SpatialPylonTextureType.BASE;
    }

    private static SpatialPylonTextureType getTextureTypeFromSideInside(TileSpatialPylon.ClientState state,
                                                                        EnumFacing ori, EnumFacing dir) {
        boolean good = state.online();

        if (ori == dir || ori.getOpposite() == dir) {
            return good ? SpatialPylonTextureType.DIM : SpatialPylonTextureType.RED;
        }

        if (state.axisPosition() == TileSpatialPylon.AxisPosition.MIDDLE) {
            return good ? SpatialPylonTextureType.DIM_SPANNED : SpatialPylonTextureType.RED_SPANNED;
        } else if (state.axisPosition() == TileSpatialPylon.AxisPosition.START
            || state.axisPosition() == TileSpatialPylon.AxisPosition.END) {
            return good ? SpatialPylonTextureType.DIM_END : SpatialPylonTextureType.RED_END;
        }

        return SpatialPylonTextureType.BASE;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState blockState, @Nullable EnumFacing side, long rand) {
        if (side != null) {
            return Collections.emptyList();
        }

        BlockRenderLayer layer = MinecraftForgeClient.getRenderLayer();
        if (layer != null && layer != BlockRenderLayer.CUTOUT) {
            return Collections.emptyList();
        }

        TileSpatialPylon.ClientState state = getState(blockState);
        return Collections.unmodifiableList(buildQuads(state));
    }

    private List<BakedQuad> buildQuads(TileSpatialPylon.ClientState state) {
        CubeBuilder builder = new CubeBuilder();

        if (state.axisPosition() != TileSpatialPylon.AxisPosition.NONE) {
            EnumFacing ori = null;
            SpatialPylonCluster.Axis displayAxis = state.axis();
            TileSpatialPylon.AxisPosition axisPos = state.axisPosition();

            if (displayAxis == SpatialPylonCluster.Axis.X) {
                ori = EnumFacing.EAST;

                if (axisPos == TileSpatialPylon.AxisPosition.END) {
                    builder.setUvRotation(EnumFacing.SOUTH, 1);
                    builder.setUvRotation(EnumFacing.NORTH, 1);
                    builder.setUvRotation(EnumFacing.UP, 2);
                    builder.setUvRotation(EnumFacing.DOWN, 2);
                } else if (axisPos == TileSpatialPylon.AxisPosition.START) {
                    builder.setUvRotation(EnumFacing.SOUTH, 2);
                    builder.setUvRotation(EnumFacing.NORTH, 2);
                    builder.setUvRotation(EnumFacing.UP, 1);
                    builder.setUvRotation(EnumFacing.DOWN, 1);
                } else {
                    builder.setUvRotation(EnumFacing.SOUTH, 1);
                    builder.setUvRotation(EnumFacing.NORTH, 1);
                    builder.setUvRotation(EnumFacing.UP, 1);
                    builder.setUvRotation(EnumFacing.DOWN, 1);
                }

                if (axisPos == TileSpatialPylon.AxisPosition.END) {
                    builder.setFlipU(EnumFacing.UP, true);
                    builder.setFlipU(EnumFacing.DOWN, true);
                } else if (axisPos == TileSpatialPylon.AxisPosition.START) {
                    builder.setFlipU(EnumFacing.NORTH, true);
                    builder.setFlipU(EnumFacing.SOUTH, true);
                }
            } else if (displayAxis == SpatialPylonCluster.Axis.Y) {
                ori = EnumFacing.UP;
                if (axisPos == TileSpatialPylon.AxisPosition.END) {
                    builder.setUvRotation(EnumFacing.NORTH, 3);
                    builder.setUvRotation(EnumFacing.SOUTH, 3);
                    builder.setUvRotation(EnumFacing.EAST, 3);
                    builder.setUvRotation(EnumFacing.WEST, 3);
                    builder.setFlipU(EnumFacing.NORTH, true);
                    builder.setFlipU(EnumFacing.SOUTH, true);
                    builder.setFlipU(EnumFacing.EAST, true);
                    builder.setFlipU(EnumFacing.WEST, true);
                }
            } else if (displayAxis == SpatialPylonCluster.Axis.Z) {
                ori = EnumFacing.NORTH;
                if (axisPos == TileSpatialPylon.AxisPosition.END) {
                    builder.setUvRotation(EnumFacing.EAST, 2);
                    builder.setUvRotation(EnumFacing.WEST, 1);
                    builder.setFlipU(EnumFacing.EAST, true);
                    builder.setFlipU(EnumFacing.WEST, true);
                } else if (axisPos == TileSpatialPylon.AxisPosition.START) {
                    builder.setUvRotation(EnumFacing.EAST, 1);
                    builder.setUvRotation(EnumFacing.WEST, 2);
                    builder.setUvRotation(EnumFacing.UP, 3);
                    builder.setUvRotation(EnumFacing.DOWN, 3);
                    builder.setFlipU(EnumFacing.UP, true);
                    builder.setFlipU(EnumFacing.DOWN, true);
                } else {
                    builder.setUvRotation(EnumFacing.EAST, 1);
                    builder.setUvRotation(EnumFacing.WEST, 2);
                }
            }

            builder.setTextures(this.textures.get(getTextureTypeFromSideOutside(state, ori, EnumFacing.UP)),
                this.textures.get(getTextureTypeFromSideOutside(state, ori, EnumFacing.DOWN)),
                this.textures.get(getTextureTypeFromSideOutside(state, ori, EnumFacing.NORTH)),
                this.textures.get(getTextureTypeFromSideOutside(state, ori, EnumFacing.SOUTH)),
                this.textures.get(getTextureTypeFromSideOutside(state, ori, EnumFacing.EAST)),
                this.textures.get(getTextureTypeFromSideOutside(state, ori, EnumFacing.WEST)));
            builder.addCube(0, 0, 0, 16, 16, 16);

            if (state.powered()) {
                builder.setEmissiveMaterial(true);
            }

            builder.setTextures(this.textures.get(getTextureTypeFromSideInside(state, ori, EnumFacing.UP)),
                this.textures.get(getTextureTypeFromSideInside(state, ori, EnumFacing.DOWN)),
                this.textures.get(getTextureTypeFromSideInside(state, ori, EnumFacing.NORTH)),
                this.textures.get(getTextureTypeFromSideInside(state, ori, EnumFacing.SOUTH)),
                this.textures.get(getTextureTypeFromSideInside(state, ori, EnumFacing.EAST)),
                this.textures.get(getTextureTypeFromSideInside(state, ori, EnumFacing.WEST)));
            builder.addCube(0, 0, 0, 16, 16, 16);
        } else {
            builder.setTexture(this.textures.get(SpatialPylonTextureType.BASE));
            builder.addCube(0, 0, 0, 16, 16, 16);

            builder.setTexture(this.textures.get(SpatialPylonTextureType.DIM));
            builder.addCube(0, 0, 0, 16, 16, 16);
        }

        builder.setEmissiveMaterial(false);
        return builder.getOutput();
    }

    private TileSpatialPylon.ClientState getState(@Nullable IBlockState blockState) {
        if (blockState instanceof IExtendedBlockState) {
            TileSpatialPylon.ClientState state = ((IExtendedBlockState) blockState).getValue(SpatialPylonBlock.RENDER_STATE);
            if (state != null) {
                return state;
            }
        }
        return TileSpatialPylon.ClientState.DEFAULT;
    }

    @Override
    public boolean isAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public boolean isBuiltInRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return this.textures.get(SpatialPylonTextureType.DIM);
    }

    @Override
    public ItemOverrideList getOverrides() {
        return ItemOverrideList.NONE;
    }
}
