package ae2.client.render.model;

import ae2.api.orientation.BlockOrientation;
import ae2.client.render.DelegateBakedModel;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.pipeline.IVertexConsumer;
import net.minecraftforge.client.model.pipeline.QuadGatheringTransformer;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;
import java.util.EnumMap;
import java.util.List;

public class FixedOrientationModel extends DelegateBakedModel {
    private final BlockOrientation rotation;
    private final EnumMap<EnumFacing, List<BakedQuad>> sideCache = new EnumMap<>(EnumFacing.class);
    private List<BakedQuad> nullSideCache;

    public FixedOrientationModel(IBakedModel base, BlockOrientation rotation) {
        super(base);
        this.rotation = rotation;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        if (this.rotation == BlockOrientation.NORTH_UP) {
            return super.getQuads(state, side, rand);
        }

        if (state != null) {
            return this.getRotatedQuads(state, side, rand);
        }

        if (side == null) {
            if (this.nullSideCache == null) {
                this.nullSideCache = this.getRotatedQuads(null, null, 0L);
            }
            return this.nullSideCache;
        }

        return this.sideCache.computeIfAbsent(side, key -> this.getRotatedQuads(null, key, 0L));
    }

    private List<BakedQuad> getRotatedQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        EnumFacing sourceSide = side == null ? null : this.rotation.resultingRotate(side);
        List<BakedQuad> quads = super.getQuads(state, sourceSide, rand);
        if (quads.isEmpty()) {
            return quads;
        }

        List<BakedQuad> rotated = new ObjectArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            rotated.add(rotateQuad(quad));
        }
        return rotated;
    }

    private BakedQuad rotateQuad(BakedQuad quad) {
        VertexFormat format = quad.getFormat();
        UnpackedBakedQuad.Builder builder = new UnpackedBakedQuad.Builder(format);
        VertexRotator rotator = new VertexRotator(this.rotation, quad.getFace());
        rotator.setParent(builder);
        quad.pipe(rotator);

        EnumFacing face = quad.getFace();
        builder.setQuadOrientation(face != null ? this.rotation.rotate(face) : EnumFacing.NORTH);

        BakedQuad unpackedQuad = builder.build();
        return new BakedQuad(unpackedQuad.getVertexData(), quad.getTintIndex(), unpackedQuad.getFace(),
            quad.getSprite(), quad.shouldApplyDiffuseLighting(), format);
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
