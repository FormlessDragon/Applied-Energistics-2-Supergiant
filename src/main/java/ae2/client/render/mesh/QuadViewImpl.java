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

import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.NotNull;

import javax.vecmath.Vector3f;

import static ae2.client.render.mesh.EncodingFormat.HEADER_BITS;
import static ae2.client.render.mesh.EncodingFormat.HEADER_COLOR_INDEX;
import static ae2.client.render.mesh.EncodingFormat.HEADER_STRIDE;
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
 * Base class for all quads / quad makers. Handles the ugly bits of maintaining and encoding the quad state.
 */
public class QuadViewImpl implements QuadView {
    protected final Vector3f faceNormal = new Vector3f();
    protected EnumFacing nominalFace;
    /**
     * True when geometry flags or light face may not match geometry.
     */
    protected boolean isGeometryInvalid = true;
    protected VertexFormat vertexFormat = DefaultVertexFormats.BLOCK;

    /**
     * Size and where it comes from will vary in subtypes. But in all cases quad is fully encoded to array.
     */
    protected int[] data;

    /**
     * Beginning of the quad. Also the header index.
     */
    protected int baseIndex = 0;

    /**
     * Use when subtype is "attached" to a pre-existing array. Sets data reference and index and decodes state from
     * array.
     */
    final void load(int[] data, int baseIndex) {
        this.data = data;
        this.baseIndex = baseIndex;
        load();
    }

    /**
     * Like {@link #load(int[], int)} but assumes array and index already set. Only does the decoding part.
     */
    public final void load() {
        isGeometryInvalid = false;
        setVertexFormat(null);
        nominalFace = lightFace();

        NormalHelper.computeFaceNormal(faceNormal, this);
    }

    protected final void setVertexFormat(VertexFormat vertexFormat) {
        this.vertexFormat = vertexFormat == null ? DefaultVertexFormats.BLOCK : vertexFormat;
    }

    public final VertexFormat vertexFormat() {
        return vertexFormat;
    }

    public final VertexFormat getFormat() {
        return vertexFormat;
    }

    /**
     * Reference to underlying array. Use with caution. Meant for fast renderer access
     */
    public int[] data() {
        return data;
    }

    public int normalFlags() {
        return EncodingFormat.normalFlags(data[baseIndex + HEADER_BITS]);
    }

    /**
     * True if any vertex normal has been set.
     */
    public boolean hasVertexNormals() {
        return normalFlags() != 0;
    }

    /**
     * gets flags used for lighting - lazily computed via {@link GeometryHelper#computeShapeFlags(QuadView)}.
     */
    public int geometryFlags() {
        computeGeometry();
        return EncodingFormat.geometryFlags(data[baseIndex + HEADER_BITS]);
    }

    protected void computeGeometry() {
        if (isGeometryInvalid) {
            isGeometryInvalid = false;

            NormalHelper.computeFaceNormal(faceNormal, this);

            // depends on face normal
            data[baseIndex + HEADER_BITS] = EncodingFormat.lightFace(data[baseIndex + HEADER_BITS],
                GeometryHelper.lightFace(this));

            // depends on light face
            data[baseIndex + HEADER_BITS] = EncodingFormat.geometryFlags(data[baseIndex + HEADER_BITS],
                GeometryHelper.computeShapeFlags(this));
        }
    }

    @Override
    public final int colorIndex() {
        return data[baseIndex + HEADER_COLOR_INDEX];
    }

    @Override
    public final int tag() {
        return data[baseIndex + HEADER_TAG];
    }

    @Override
    public final @NotNull EnumFacing lightFace() {
        computeGeometry();
        return EncodingFormat.lightFace(data[baseIndex + HEADER_BITS]);
    }

    @Override
    public final EnumFacing cullFace() {
        return EncodingFormat.cullFace(data[baseIndex + HEADER_BITS]);
    }

    @Override
    public final EnumFacing nominalFace() {
        return nominalFace;
    }

    @Override
    public final Vector3f faceNormal() {
        computeGeometry();
        return faceNormal;
    }

    @Override
    public void copyTo(MutableQuadView target) {
        computeGeometry();

        final MutableQuadViewImpl quad = (MutableQuadViewImpl) target;
        System.arraycopy(data, baseIndex, quad.data, quad.baseIndex, EncodingFormat.TOTAL_STRIDE);
        quad.faceNormal.set(faceNormal.x, faceNormal.y, faceNormal.z);
        quad.nominalFace = this.nominalFace;
        quad.setVertexFormat(this.vertexFormat);
        quad.isGeometryInvalid = false;
    }

    @Override
    public void copyPos(int vertexIndex, Vector3f target) {
        if (target == null) {
            target = new Vector3f();
        }

        final int index = baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_X;
        target.set(Float.intBitsToFloat(data[index]), Float.intBitsToFloat(data[index + 1]),
            Float.intBitsToFloat(data[index + 2]));
    }

    @Override
    public float posByIndex(int vertexIndex, int coordinateIndex) {
        return Float.intBitsToFloat(data[baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_X + coordinateIndex]);
    }

    @Override
    public float x(int vertexIndex) {
        return Float.intBitsToFloat(data[baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_X]);
    }

    @Override
    public float y(int vertexIndex) {
        return Float.intBitsToFloat(data[baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_Y]);
    }

    @Override
    public float z(int vertexIndex) {
        return Float.intBitsToFloat(data[baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_Z]);
    }

    @Override
    public boolean hasNormal(int vertexIndex) {
        return (normalFlags() & (1 << vertexIndex)) != 0;
    }

    protected final int normalIndex(int vertexIndex) {
        return baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_NORMAL;
    }

    @Override
    public Vector3f copyNormal(int vertexIndex, Vector3f target) {
        if (hasNormal(vertexIndex)) {
            if (target == null) {
                target = new Vector3f();
            }

            final int normal = data[normalIndex(vertexIndex)];
            target.set(NormalHelper.getPackedNormalComponent(normal, 0),
                NormalHelper.getPackedNormalComponent(normal, 1), NormalHelper.getPackedNormalComponent(normal, 2));
            return target;
        } else {
            return null;
        }
    }

    @Override
    public float normalX(int vertexIndex) {
        return hasNormal(vertexIndex) ? NormalHelper.getPackedNormalComponent(data[normalIndex(vertexIndex)], 0)
            : Float.NaN;
    }

    @Override
    public float normalY(int vertexIndex) {
        return hasNormal(vertexIndex) ? NormalHelper.getPackedNormalComponent(data[normalIndex(vertexIndex)], 1)
            : Float.NaN;
    }

    @Override
    public float normalZ(int vertexIndex) {
        return hasNormal(vertexIndex) ? NormalHelper.getPackedNormalComponent(data[normalIndex(vertexIndex)], 2)
            : Float.NaN;
    }

    @Override
    public int lightmap(int vertexIndex) {
        return data[baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_LIGHTMAP];
    }

    @Override
    public int color(int vertexIndex) {
        return data[baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_COLOR];
    }

    @Override
    public float u(int vertexIndex) {
        return Float.intBitsToFloat(data[baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_U]);
    }

    @Override
    public float v(int vertexIndex) {
        return Float.intBitsToFloat(data[baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_V]);
    }

    public int vertexStart() {
        return baseIndex + HEADER_STRIDE;
    }

    @Override
    public boolean hasShade() {
        return EncodingFormat.shade(data[baseIndex + HEADER_BITS]);
    }

    @Override
    public boolean hasAmbientOcclusion() {
        return EncodingFormat.ambientOcclusion(data[baseIndex + HEADER_BITS]);
    }

    @Override
    public final void toVanilla(int[] target, int targetIndex) {
        for (int i = 0; i < 4; i++) {
            int fromIndex = baseIndex + i * VERTEX_STRIDE;
            int toIndex = targetIndex + i * VANILLA_VERTEX_STRIDE;
            target[toIndex] = data[fromIndex + VERTEX_X];
            target[toIndex + 1] = data[fromIndex + VERTEX_Y];
            target[toIndex + 2] = data[fromIndex + VERTEX_Z];
            target[toIndex + 3] = ColorHelper.toVanillaColor(data[fromIndex + VERTEX_COLOR]);
            target[toIndex + 4] = data[fromIndex + VERTEX_U];
            target[toIndex + 5] = data[fromIndex + VERTEX_V];

            if (VANILLA_VERTEX_STRIDE > 6) {
                target[toIndex + 6] = data[fromIndex + VERTEX_LIGHTMAP];
            }

            if (VANILLA_VERTEX_STRIDE > 7) {
                target[toIndex + 7] = hasNormal(i) ? data[fromIndex + VERTEX_NORMAL] : 0;
            }
        }
    }
}
