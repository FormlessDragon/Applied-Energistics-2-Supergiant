package ae2.client.render.mesh;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.Nullable;

import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;

/**
 * Specialized {@link MutableQuadView} obtained via {@link MeshBuilder#getEmitter()} to append quads during mesh
 * building.
 *
 * <p>
 * Instances of {@link QuadEmitter} will practically always be threadlocal and/or reused - do not retain references.
 *
 * <p>
 * Only the renderer should implement or extend this interface.
 */
public interface QuadEmitter extends MutableQuadView {
    /**
     * Tolerance for determining if the depth parameter to {@link #square(EnumFacing, float, float, float, float, float)}
     * is effectively zero - meaning the face is a cull face.
     */
    float CULL_FACE_EPSILON = 0.00001f;

    @Override
    void pos(int vertexIndex, float x, float y, float z);

    @Override
    default void pos(int vertexIndex, Vector3f pos) {
        MutableQuadView.super.pos(vertexIndex, pos);
    }

    @Override
    void color(int vertexIndex, int color);

    @Override
    default QuadEmitter color(int c0, int c1, int c2, int c3) {
        MutableQuadView.super.color(c0, c1, c2, c3);
        return this;
    }

    @Override
    void uv(int vertexIndex, float u, float v);

    @Override
    default void uv(int vertexIndex, Vector2f uv) {
        MutableQuadView.super.uv(vertexIndex, uv);
    }

    @Override
    QuadEmitter shade(boolean shade);

    @Override
    QuadEmitter ambientOcclusion(boolean ao);

    @Override
    QuadEmitter spriteBake(TextureAtlasSprite sprite, int bakeFlags);

    default QuadEmitter uvUnitSquare() {
        uv(0, 0, 0);
        uv(1, 0, 1);
        uv(2, 1, 1);
        uv(3, 1, 0);
        return this;
    }

    @Override
    void lightmap(int vertexIndex, int lightmap);

    @Override
    default QuadEmitter lightmap(int b0, int b1, int b2, int b3) {
        MutableQuadView.super.lightmap(b0, b1, b2, b3);
        return this;
    }

    @Override
    void normal(int vertexIndex, float x, float y, float z);

    @Override
    default void normal(int vertexIndex, Vector3f normal) {
        MutableQuadView.super.normal(vertexIndex, normal);
    }

    @Override
    QuadEmitter cullFace(@Nullable EnumFacing face);

    @Override
    void nominalFace(@Nullable EnumFacing face);

    @Override
    void colorIndex(int colorIndex);

    @Override
    QuadEmitter tag(int tag);

    @Override
    QuadEmitter fromVanilla(int[] quadData, int startIndex);

    /**
     * Helper method to assign vertex coordinates for a square aligned with the given face. Ensures that vertex order is
     * consistent with vanilla convention. (Incorrect order can lead to bad AO lighting unless enhanced lighting logic
     * is available/enabled.)
     *
     * <p>
     * Square will be parallel to the given face and coplanar with the face (and culled if the face is occluded) if the
     * depth parameter is approximately zero. See {@link #CULL_FACE_EPSILON}.
     *
     * <p>
     * All coordinates should be normalized (0-1).
     */
    default QuadEmitter square(EnumFacing nominalFace, float left, float bottom, float right, float top, float depth) {
        if (Math.abs(depth) < CULL_FACE_EPSILON) {
            cullFace(nominalFace);
            depth = 0; // avoid any inconsistency for face quads
        } else {
            cullFace(null);
        }

        nominalFace(nominalFace);
        switch (nominalFace) {
            case UP -> {
                depth = 1 - depth;
                top = 1 - top;
                bottom = 1 - bottom;
                pos(0, left, depth, top);
                pos(1, left, depth, bottom);
                pos(2, right, depth, bottom);
                pos(3, right, depth, top);
            }
            case DOWN -> {
                pos(0, left, depth, top);
                pos(1, left, depth, bottom);
                pos(2, right, depth, bottom);
                pos(3, right, depth, top);
            }
            case EAST -> {
                depth = 1 - depth;
                left = 1 - left;
                right = 1 - right;
                pos(0, depth, top, left);
                pos(1, depth, bottom, left);
                pos(2, depth, bottom, right);
                pos(3, depth, top, right);
            }
            case WEST -> {
                pos(0, depth, top, left);
                pos(1, depth, bottom, left);
                pos(2, depth, bottom, right);
                pos(3, depth, top, right);
            }
            case SOUTH -> {
                depth = 1 - depth;
                left = 1 - left;
                right = 1 - right;
                pos(0, 1 - left, top, depth);
                pos(1, 1 - left, bottom, depth);
                pos(2, 1 - right, bottom, depth);
                pos(3, 1 - right, top, depth);
            }
            case NORTH -> {
                pos(0, 1 - left, top, depth);
                pos(1, 1 - left, bottom, depth);
                pos(2, 1 - right, bottom, depth);
                pos(3, 1 - right, top, depth);
            }
        }

        return this;
    }

    /**
     * In static mesh building, causes quad to be appended to the mesh being built. In a dynamic render context, create
     * a new quad to be output to rendering. In both cases, current instance is reset to default values.
     */
    QuadEmitter emit();
}
