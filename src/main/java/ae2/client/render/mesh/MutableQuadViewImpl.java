package ae2.client.render.mesh;
/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.Nullable;

import static ae2.client.render.mesh.EncodingFormat.EMPTY;
import static ae2.client.render.mesh.EncodingFormat.HEADER_BITS;
import static ae2.client.render.mesh.EncodingFormat.HEADER_COLOR_INDEX;
import static ae2.client.render.mesh.EncodingFormat.HEADER_TAG;
import static ae2.client.render.mesh.EncodingFormat.VERTEX_COLOR;
import static ae2.client.render.mesh.EncodingFormat.VERTEX_LIGHTMAP;
import static ae2.client.render.mesh.EncodingFormat.VERTEX_NORMAL;
import static ae2.client.render.mesh.EncodingFormat.VERTEX_STRIDE;
import static ae2.client.render.mesh.EncodingFormat.VERTEX_U;
import static ae2.client.render.mesh.EncodingFormat.VERTEX_V;
import static ae2.client.render.mesh.EncodingFormat.VERTEX_X;
import static ae2.client.render.mesh.EncodingFormat.VERTEX_Y;
import static ae2.client.render.mesh.EncodingFormat.VERTEX_Z;

/**
 * Almost-concrete implementation of a mutable quad. The only missing part is {@link #emit()}, because that depends on
 * where/how it is used. (Mesh encoding vs. render-time transformation).
 */
public abstract class MutableQuadViewImpl extends QuadViewImpl implements QuadEmitter {
    public static final ThreadLocal<MutableQuadView> THREAD_LOCAL = ThreadLocal
        .withInitial(() -> new MutableQuadViewImpl() {
            {
                begin(new int[EncodingFormat.TOTAL_STRIDE], 0);
            }

            @Override
            public QuadEmitter emit() {
                throw new UnsupportedOperationException();
            }
        });

    public final void begin(int[] data, int baseIndex) {
        this.data = data;
        this.baseIndex = baseIndex;
        clear();
    }

    public void clear() {
        System.arraycopy(EMPTY, 0, data, baseIndex, EncodingFormat.TOTAL_STRIDE);
        isGeometryInvalid = true;
        nominalFace = null;
        setVertexFormat(null);
        normalFlags(0);
        tag(0);
        colorIndex(-1);
        cullFace(null);
        shade(true);
        ambientOcclusion(true);
    }

    @Override
    public void pos(int vertexIndex, float x, float y, float z) {
        final int index = baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_X;
        data[index] = Float.floatToRawIntBits(x);
        data[index + 1] = Float.floatToRawIntBits(y);
        data[index + 2] = Float.floatToRawIntBits(z);
        isGeometryInvalid = true;
    }

    @Override
    public void color(int vertexIndex, int color) {
        data[baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_COLOR] = color;
    }

    @Override
    public void uv(int vertexIndex, float u, float v) {
        final int i = baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_U;
        data[i] = Float.floatToRawIntBits(u);
        data[i + 1] = Float.floatToRawIntBits(v);
    }

    @Override
    public MutableQuadViewImpl shade(boolean shade) {
        data[baseIndex + HEADER_BITS] = EncodingFormat.shade(data[baseIndex + HEADER_BITS], shade);
        return this;
    }

    @Override
    public MutableQuadViewImpl ambientOcclusion(boolean ao) {
        data[baseIndex + HEADER_BITS] = EncodingFormat.ambientOcclusion(data[baseIndex + HEADER_BITS], ao);
        return this;
    }

    @Override
    public MutableQuadViewImpl spriteBake(TextureAtlasSprite sprite, int bakeFlags) {
        TextureHelper.bakeSprite(this, sprite, bakeFlags);
        return this;
    }

    @Override
    public void lightmap(int vertexIndex, int lightmap) {
        data[baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_LIGHTMAP] = lightmap;
    }

    protected void normalFlags(int flags) {
        data[baseIndex + HEADER_BITS] = EncodingFormat.normalFlags(data[baseIndex + HEADER_BITS], flags);
    }

    @Override
    public void normal(int vertexIndex, float x, float y, float z) {
        normalFlags(normalFlags() | (1 << vertexIndex));
        data[baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_NORMAL] = NormalHelper.packNormal(x, y, z, 0);
    }

    /**
     * Internal helper method. Copies face normals to vertex normals lacking one.
     */
    public final void populateMissingNormals() {
        final int normalFlags = this.normalFlags();

        if (normalFlags == 0b1111)
            return;

        final int packedFaceNormal = NormalHelper.packNormal(faceNormal(), 0);

        for (int v = 0; v < 4; v++) {
            if ((normalFlags & (1 << v)) == 0) {
                data[baseIndex + v * VERTEX_STRIDE + VERTEX_NORMAL] = packedFaceNormal;
            }
        }

        normalFlags(0b1111);
    }

    @Override
    public final MutableQuadViewImpl cullFace(@Nullable EnumFacing face) {
        data[baseIndex + HEADER_BITS] = EncodingFormat.cullFace(data[baseIndex + HEADER_BITS], face);
        nominalFace(face);
        return this;
    }

    @Override
    public final void nominalFace(@Nullable EnumFacing face) {
        nominalFace = face;
    }

    @Override
    public final void colorIndex(int colorIndex) {
        data[baseIndex + HEADER_COLOR_INDEX] = colorIndex;
    }

    @Override
    public final MutableQuadViewImpl tag(int tag) {
        data[baseIndex + HEADER_TAG] = tag;
        return this;
    }

    @Override
    public final MutableQuadViewImpl fromVanilla(int[] quadData, int startIndex) {
        isGeometryInvalid = true;

        int flags = 0;

        for (int i = 0; i < 4; i++) {
            int fromIndex = startIndex + i * VANILLA_VERTEX_STRIDE;
            int toIndex = baseIndex + i * VERTEX_STRIDE;
            data[toIndex + VERTEX_X] = quadData[fromIndex];
            data[toIndex + VERTEX_Y] = quadData[fromIndex + 1];
            data[toIndex + VERTEX_Z] = quadData[fromIndex + 2];
            data[toIndex + VERTEX_COLOR] = ColorHelper.fromVanillaColor(quadData[fromIndex + 3]);
            data[toIndex + VERTEX_U] = quadData[fromIndex + 4];
            data[toIndex + VERTEX_V] = quadData[fromIndex + 5];
            data[toIndex + VERTEX_LIGHTMAP] = VANILLA_VERTEX_STRIDE > 6 ? quadData[fromIndex + 6] : 0;

            if (VANILLA_VERTEX_STRIDE > 7) {
                int normal = quadData[fromIndex + 7];
                data[toIndex + VERTEX_NORMAL] = normal;
                if (normal != 0) {
                    flags |= 1 << i;
                }
            } else {
                data[toIndex + VERTEX_NORMAL] = 0;
            }
        }

        data[baseIndex + HEADER_BITS] = EncodingFormat.normalFlags(data[baseIndex + HEADER_BITS], flags);

        return this;
    }

    @Override
    public final void fromVanilla(BakedQuad quad, @Nullable EnumFacing cullFace) {
        fromVanilla(quad.getVertexData(), 0);
        setVertexFormat(quad.getFormat());
        int bits = EncodingFormat.normalFlags(0, normalFlags());
        bits = EncodingFormat.cullFace(bits, cullFace);
        bits = EncodingFormat.shade(bits, quad.shouldApplyDiffuseLighting());
        bits = EncodingFormat.ambientOcclusion(bits, true);
        data[baseIndex + HEADER_BITS] = bits;
        nominalFace(quad.getFace());
        colorIndex(quad.getTintIndex());
        tag(0);
    }
}
