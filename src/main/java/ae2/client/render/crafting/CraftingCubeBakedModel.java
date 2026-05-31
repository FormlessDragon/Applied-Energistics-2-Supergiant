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
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

@SuppressWarnings("deprecation")
abstract class CraftingCubeBakedModel implements IBakedModel {
    private final VertexFormat format;
    private final TextureAtlasSprite ringCorner;
    private final TextureAtlasSprite ringHor;
    private final TextureAtlasSprite ringVer;

    CraftingCubeBakedModel(VertexFormat format, TextureAtlasSprite ringCorner, TextureAtlasSprite ringHor,
                           TextureAtlasSprite ringVer) {
        this.format = format;
        this.ringCorner = ringCorner;
        this.ringHor = ringHor;
        this.ringVer = ringVer;
    }

    private static EnumSet<EnumFacing> getConnections(@Nullable IBlockState state) {
        if (!(state instanceof IExtendedBlockState)) {
            return EnumSet.noneOf(EnumFacing.class);
        }

        CraftingCubeState cubeState = ((IExtendedBlockState) state).getValue(AbstractCraftingUnitBlock.STATE);
        return cubeState == null ? EnumSet.noneOf(EnumFacing.class) : cubeState.connections();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        if (side == null) {
            return Collections.emptyList();
        }

        EnumSet<EnumFacing> connections = getConnections(state);
        List<BakedQuad> quads = new ObjectArrayList<>();
        CubeBuilder builder = new CubeBuilder(this.format, quads);
        builder.setDrawFaces(EnumSet.of(side));

        this.addRing(builder, side, connections);

        float x2 = connections.contains(EnumFacing.EAST) ? 16 : 13.01f;
        float x1 = connections.contains(EnumFacing.WEST) ? 0 : 2.99f;
        float y2 = connections.contains(EnumFacing.UP) ? 16 : 13.01f;
        float y1 = connections.contains(EnumFacing.DOWN) ? 0 : 2.99f;
        float z2 = connections.contains(EnumFacing.SOUTH) ? 16 : 13.01f;
        float z1 = connections.contains(EnumFacing.NORTH) ? 0 : 2.99f;

        switch (side) {
            case DOWN, UP -> {
                y1 = 0;
                y2 = 16;
            }
            case NORTH, SOUTH -> {
                z1 = 0;
                z2 = 16;
            }
            case WEST, EAST -> {
                x1 = 0;
                x2 = 16;
            }
            default -> {
            }
        }

        this.addInnerCube(side, state, builder, x1, y1, z1, x2, y2, z2);
        return quads;
    }

    private void addRing(CubeBuilder builder, EnumFacing side, EnumSet<EnumFacing> connections) {
        builder.setTexture(this.ringCorner);
        this.addCornerCap(builder, connections, side, EnumFacing.UP, EnumFacing.EAST, EnumFacing.NORTH);
        this.addCornerCap(builder, connections, side, EnumFacing.UP, EnumFacing.EAST, EnumFacing.SOUTH);
        this.addCornerCap(builder, connections, side, EnumFacing.UP, EnumFacing.WEST, EnumFacing.NORTH);
        this.addCornerCap(builder, connections, side, EnumFacing.UP, EnumFacing.WEST, EnumFacing.SOUTH);
        this.addCornerCap(builder, connections, side, EnumFacing.DOWN, EnumFacing.EAST, EnumFacing.NORTH);
        this.addCornerCap(builder, connections, side, EnumFacing.DOWN, EnumFacing.EAST, EnumFacing.SOUTH);
        this.addCornerCap(builder, connections, side, EnumFacing.DOWN, EnumFacing.WEST, EnumFacing.NORTH);
        this.addCornerCap(builder, connections, side, EnumFacing.DOWN, EnumFacing.WEST, EnumFacing.SOUTH);

        for (EnumFacing direction : EnumFacing.values()) {
            if (direction == side || direction == side.getOpposite()) {
                continue;
            }

            if (side.getAxis() != EnumFacing.Axis.Y
                && (direction == EnumFacing.NORTH || direction == EnumFacing.EAST || direction == EnumFacing.WEST
                || direction == EnumFacing.SOUTH)) {
                builder.setTexture(this.ringVer);
            } else if (side.getAxis() == EnumFacing.Axis.Y
                && (direction == EnumFacing.EAST || direction == EnumFacing.WEST)) {
                builder.setTexture(this.ringVer);
            } else {
                builder.setTexture(this.ringHor);
            }

            if (!connections.contains(direction)) {
                float x1 = 0;
                float y1 = 0;
                float z1 = 0;
                float x2 = 16;
                float y2 = 16;
                float z2 = 16;

                switch (direction) {
                    case DOWN -> {
                        y1 = 0;
                        y2 = 3;
                    }
                    case UP -> y1 = 13;
                    case WEST -> {
                        x1 = 0;
                        x2 = 3;
                    }
                    case EAST -> x1 = 13;
                    case NORTH -> {
                        z1 = 0;
                        z2 = 3;
                    }
                    case SOUTH -> z1 = 13;
                    default -> {
                    }
                }

                EnumFacing perpendicular = direction.rotateAround(side.getAxis());
                for (EnumFacing cornerCandidate : EnumSet.of(perpendicular, perpendicular.getOpposite())) {
                    if (!connections.contains(cornerCandidate)) {
                        switch (cornerCandidate) {
                            case DOWN -> y1 = 3;
                            case UP -> y2 = 13;
                            case NORTH -> z1 = 3;
                            case SOUTH -> z2 = 13;
                            case WEST -> x1 = 3;
                            case EAST -> x2 = 13;
                            default -> {
                            }
                        }
                    }
                }

                builder.addCube(x1, y1, z1, x2, y2, z2);
            }
        }
    }

    private void addCornerCap(CubeBuilder builder, EnumSet<EnumFacing> connections, EnumFacing side, EnumFacing down,
                              EnumFacing west, EnumFacing north) {
        if (connections.contains(down) || connections.contains(west) || connections.contains(north)) {
            return;
        }
        if (side != down && side != west && side != north) {
            return;
        }

        float x1 = west == EnumFacing.WEST ? 0 : 13;
        float y1 = down == EnumFacing.DOWN ? 0 : 13;
        float z1 = north == EnumFacing.NORTH ? 0 : 13;
        float x2 = west == EnumFacing.WEST ? 3 : 16;
        float y2 = down == EnumFacing.DOWN ? 3 : 16;
        float z2 = north == EnumFacing.NORTH ? 3 : 16;
        builder.addCube(x1, y1, z1, x2, y2, z2);
    }

    protected abstract void addInnerCube(EnumFacing side, IBlockState state, CubeBuilder builder, float x1, float y1,
                                         float z1, float x2, float y2, float z2);

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
        return this.ringCorner;
    }

    @Override
    public ItemCameraTransforms getItemCameraTransforms() {
        return ItemCameraTransforms.DEFAULT;
    }

    @Override
    public ItemOverrideList getOverrides() {
        return ItemOverrideList.NONE;
    }
}
