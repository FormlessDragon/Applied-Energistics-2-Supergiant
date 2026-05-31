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

package ae2.client.render.model;

import ae2.api.orientation.BlockOrientation;
import ae2.block.AEBaseTileBlock;
import ae2.client.render.DelegateBakedModel;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.pipeline.IVertexConsumer;
import net.minecraftforge.client.model.pipeline.QuadGatheringTransformer;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import net.minecraftforge.common.property.IExtendedBlockState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;
import java.util.List;

@SuppressWarnings("deprecation")
public class AutoRotatingModel extends DelegateBakedModel implements IResourceManagerReloadListener {
    private final LoadingCache<AutoRotatingCacheKey, List<BakedQuad>> quadCache;

    public AutoRotatingModel(IBakedModel base) {
        super(base);
        this.quadCache = CacheBuilder.newBuilder().maximumSize(256).build(
            new CacheLoader<>() {
                @Override
                @Nonnull
                public List<BakedQuad> load(@Nonnull AutoRotatingCacheKey key) {
                    return AutoRotatingModel.this.getRotatedModel(key.blockState(), key.side(), key.forward(),
                        key.up());
                }
            });
    }

    private static BakedQuad rotateQuad(BakedQuad quad, BlockOrientation rotation) {
        VertexFormat format = quad.getFormat();
        UnpackedBakedQuad.Builder builder = new UnpackedBakedQuad.Builder(format);
        VertexRotator rotator = new VertexRotator(rotation, quad.getFace());
        rotator.setParent(builder);
        quad.pipe(rotator);

        EnumFacing face = quad.getFace();
        builder.setQuadOrientation(face != null ? rotation.rotate(face) : EnumFacing.NORTH);

        BakedQuad unpackedQuad = builder.build();
        return new BakedQuad(unpackedQuad.getVertexData(), quad.getTintIndex(), unpackedQuad.getFace(),
            quad.getSprite(), quad.shouldApplyDiffuseLighting(), format);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        if (!(state instanceof IExtendedBlockState extState)) {
            return super.getQuads(state, side, rand);
        }

        EnumFacing forward = extState.getValue(AEBaseTileBlock.FORWARD);
        EnumFacing up = extState.getValue(AEBaseTileBlock.UP);
        if (forward == null || up == null) {
            return super.getQuads(state, side, rand);
        }

        if (extState.getUnlistedProperties().size() != 2) {
            return this.getRotatedModel(extState, side, forward, up);
        }

        return this.quadCache.getUnchecked(new AutoRotatingCacheKey(extState.getClean(), forward, up, side));
    }

    @Override
    public void onResourceManagerReload(@Nonnull IResourceManager resourceManager) {
        this.quadCache.invalidateAll();
    }

    private List<BakedQuad> getRotatedModel(IBlockState state, @Nullable EnumFacing side, EnumFacing forward,
                                            EnumFacing up) {
        BlockOrientation rotation = BlockOrientation.get(forward, up);
        EnumFacing sourceSide = side == null ? null : rotation.resultingRotate(side);
        List<BakedQuad> quads = super.getQuads(state, sourceSide, 0);
        if (rotation == BlockOrientation.NORTH_UP || quads.isEmpty()) {
            return quads;
        }

        List<BakedQuad> rotated = new ObjectArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            rotated.add(rotateQuad(quad, rotation));
        }
        return rotated;
    }

    @ParametersAreNonnullByDefault
    private static class VertexRotator extends QuadGatheringTransformer {
        private final BlockOrientation rotation;
        private final EnumFacing face;

        VertexRotator(BlockOrientation rotation, EnumFacing face) {
            this.rotation = rotation;
            this.face = face;
        }

        @Override
        public void setParent(IVertexConsumer parent) {
            super.setParent(parent);
            if (this.getVertexFormat() != parent.getVertexFormat()) {
                this.setVertexFormat(parent.getVertexFormat());
            }
        }

        @Override
        protected void processQuad() {
            VertexFormat format = this.parent.getVertexFormat();
            int count = format.getElementCount();

            for (int vertex = 0; vertex < 4; vertex++) {
                for (int elementIndex = 0; elementIndex < count; elementIndex++) {
                    VertexFormatElement element = format.getElement(elementIndex);
                    if (element.getUsage() == VertexFormatElement.EnumUsage.POSITION) {
                        this.parent.put(elementIndex, transformPosition(this.quadData[elementIndex][vertex]));
                    } else if (element.getUsage() == VertexFormatElement.EnumUsage.NORMAL) {
                        this.parent.put(elementIndex, transformNormal(this.quadData[elementIndex][vertex]));
                    } else {
                        this.parent.put(elementIndex, this.quadData[elementIndex][vertex]);
                    }
                }
            }
        }

        private float[] transformPosition(float[] values) {
            if (values.length == 3) {
                Vector4f vec = new Vector4f(values[0] - 0.5f, values[1] - 0.5f, values[2] - 0.5f, 1);
                this.rotation.getTransformation().transformPosition(vec);
                return new float[]{vec.x + 0.5f, vec.y + 0.5f, vec.z + 0.5f};
            }
            if (values.length == 4) {
                Vector4f vec = new Vector4f(values[0] - 0.5f, values[1] - 0.5f, values[2] - 0.5f, values[3]);
                this.rotation.getTransformation().transformPosition(vec);
                return new float[]{vec.x + 0.5f, vec.y + 0.5f, vec.z + 0.5f, vec.w};
            }
            return values;
        }

        private float[] transformNormal(float[] values) {
            if (this.face != null) {
                var direction = this.rotation.rotate(this.face).getDirectionVec();
                if (values.length == 3) {
                    return new float[]{direction.getX(), direction.getY(), direction.getZ()};
                }
                if (values.length == 4) {
                    return new float[]{direction.getX(), direction.getY(), direction.getZ(), values[3]};
                }
                return values;
            }

            if (values.length == 3) {
                Vector3f vec = new Vector3f(values);
                this.rotation.getTransformation().transformNormal(vec);
                return new float[]{vec.x, vec.y, vec.z};
            }
            if (values.length == 4) {
                Vector3f vec = new Vector3f(values[0], values[1], values[2]);
                this.rotation.getTransformation().transformNormal(vec);
                return new float[]{vec.x, vec.y, vec.z, values[3]};
            }
            return values;
        }

        @Override
        public void setQuadTint(int tint) {
            this.parent.setQuadTint(tint);
        }

        @Override
        public void setQuadOrientation(EnumFacing orientation) {
            this.parent.setQuadOrientation(orientation);
        }

        @Override
        public void setApplyDiffuseLighting(boolean diffuse) {
            this.parent.setApplyDiffuseLighting(diffuse);
        }

        @Override
        public void setTexture(TextureAtlasSprite texture) {
            this.parent.setTexture(texture);
        }
    }
}
