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

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

import java.util.EnumMap;

/**
 * Handles most texture-baking use cases for model loaders and model libraries via
 * {@link #bakeSprite(MutableQuadView, TextureAtlasSprite, int)}. Also used by the API itself to implement automatic
 * block-breaking models for enhanced models.
 */
public class TextureHelper {
    private static final float NORMALIZER = 1f / 16f;
    private static final VertexModifier[] ROTATIONS = new VertexModifier[]{null,
        (q, i) -> q.uv(i, q.v(i), 1 - q.u(i)),
        (q, i) -> q.uv(i, 1 - q.u(i), 1 - q.v(i)),
        (q, i) -> q.uv(i, 1 - q.v(i), q.u(i))};
    private static final EnumMap<EnumFacing, VertexModifier> UVLOCKERS = new EnumMap<>(EnumFacing.class);

    static {
        UVLOCKERS.put(EnumFacing.EAST, (q, i) -> q.uv(i, 1 - q.z(i), 1 - q.y(i)));
        UVLOCKERS.put(EnumFacing.WEST, (q, i) -> q.uv(i, q.z(i), 1 - q.y(i)));
        UVLOCKERS.put(EnumFacing.NORTH, (q, i) -> q.uv(i, 1 - q.x(i), 1 - q.y(i)));
        UVLOCKERS.put(EnumFacing.SOUTH, (q, i) -> q.uv(i, q.x(i), 1 - q.y(i)));
        UVLOCKERS.put(EnumFacing.DOWN, (q, i) -> q.uv(i, q.x(i), 1 - q.z(i)));
        UVLOCKERS.put(EnumFacing.UP, (q, i) -> q.uv(i, q.x(i), q.z(i)));
    }

    private TextureHelper() {
    }

    /**
     * Bakes textures in the provided vertex data, handling UV locking, rotation, interpolation, etc. Textures must not
     * be already baked.
     */
    public static void bakeSprite(MutableQuadView quad, TextureAtlasSprite sprite, int bakeFlags) {
        if (quad.nominalFace() != null && (MutableQuadView.BAKE_LOCK_UV & bakeFlags) != 0) {
            applyModifier(quad, UVLOCKERS.get(quad.nominalFace()));
        } else if ((MutableQuadView.BAKE_NORMALIZED & bakeFlags) == 0) {
            applyModifier(quad, (q, i) -> q.uv(i, q.u(i) * NORMALIZER, q.v(i) * NORMALIZER));
        }

        final int rotation = bakeFlags & 3;

        if (rotation != 0) {
            applyModifier(quad, ROTATIONS[rotation]);
        }

        if ((MutableQuadView.BAKE_FLIP_U & bakeFlags) != 0) {
            applyModifier(quad, (q, i) -> q.uv(i, 1 - q.u(i), q.v(i)));
        }

        if ((MutableQuadView.BAKE_FLIP_V & bakeFlags) != 0) {
            applyModifier(quad, (q, i) -> q.uv(i, q.u(i), 1 - q.v(i)));
        }

        interpolate(quad, sprite);
    }

    /**
     * Faster than sprite method. Sprite computes span and normalizes inputs each call, so we'd have to denormalize
     * before we called, only to have the sprite renormalize immediately.
     */
    private static void interpolate(MutableQuadView q, TextureAtlasSprite sprite) {
        final float uMin = sprite.getMinU();
        final float uSpan = sprite.getMaxU() - uMin;
        final float vMin = sprite.getMinV();
        final float vSpan = sprite.getMaxV() - vMin;

        for (int i = 0; i < 4; i++) {
            q.uv(i, uMin + q.u(i) * uSpan, vMin + q.v(i) * vSpan);
        }
    }

    private static void applyModifier(MutableQuadView quad, VertexModifier modifier) {
        for (int i = 0; i < 4; i++) {
            modifier.apply(quad, i);
        }
    }

    @FunctionalInterface
    private interface VertexModifier {
        void apply(MutableQuadView quad, int vertexIndex);
    }
}
