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

import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemTransformVec3f;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.util.vector.Vector3f;

import java.util.Arrays;
import java.util.List;

/**
 * Collection of utilities for model implementations.
 */
@SuppressWarnings("deprecation")
public abstract class ModelHelper {
    /**
     * Result from {@link #toFaceIndex(EnumFacing)} for null values.
     */
    public static final int NULL_FACE_ID = 6;
    public static final ItemTransformVec3f TRANSFORM_BLOCK_GUI = makeTransform(30, 225, 0, 0, 0, 0, 0.625f, 0.625f, 0.625f);
    public static final ItemTransformVec3f TRANSFORM_BLOCK_GROUND = makeTransform(0, 0, 0, 0, 3, 0, 0.25f, 0.25f, 0.25f);
    public static final ItemTransformVec3f TRANSFORM_BLOCK_FIXED = makeTransform(0, 0, 0, 0, 0, 0, 0.5f, 0.5f, 0.5f);
    public static final ItemTransformVec3f TRANSFORM_BLOCK_3RD_PERSON_RIGHT = makeTransform(75, 45, 0, 0, 2.5f, 0, 0.375f,
        0.375f, 0.375f);
    public static final ItemTransformVec3f TRANSFORM_BLOCK_1ST_PERSON_RIGHT = makeTransform(0, 45, 0, 0, 0, 0, 0.4f, 0.4f,
        0.4f);
    public static final ItemTransformVec3f TRANSFORM_BLOCK_1ST_PERSON_LEFT = makeTransform(0, 225, 0, 0, 0, 0, 0.4f, 0.4f,
        0.4f);
    /**
     * Mimics the vanilla model transformation used for most vanilla blocks, and should be suitable for most custom
     * block-like models.
     */
    @SuppressWarnings("unused")
    public static final ItemCameraTransforms MODEL_TRANSFORM_BLOCK = new ItemCameraTransforms(TRANSFORM_BLOCK_3RD_PERSON_RIGHT,
        TRANSFORM_BLOCK_3RD_PERSON_RIGHT, TRANSFORM_BLOCK_1ST_PERSON_LEFT, TRANSFORM_BLOCK_1ST_PERSON_RIGHT,
        ItemTransformVec3f.DEFAULT, TRANSFORM_BLOCK_GUI, TRANSFORM_BLOCK_GROUND, TRANSFORM_BLOCK_FIXED);
    /**
     * @see #faceFromIndex(int)
     */
    private static final EnumFacing[] FACES = Arrays.copyOf(EnumFacing.VALUES, 7);

    private ModelHelper() {
    }

    /**
     * Convenient way to encode faces that may be null. Null is returned as {@link #NULL_FACE_ID}. Use
     * {@link #faceFromIndex(int)} to retrieve encoded face.
     */
    public static int toFaceIndex(EnumFacing face) {
        return face == null ? NULL_FACE_ID : face.getIndex();
    }

    /**
     * Use to decode a result from {@link #toFaceIndex(EnumFacing)}. Return Value will be null if encoded Value was null.
     * Can also be used for no-allocation iteration of {@link EnumFacing#values()}, optionally including the null face.
     * (Use &lt; or &lt;= {@link #NULL_FACE_ID} to exclude or include the null Value, respectively.)
     */
    public static EnumFacing faceFromIndex(int faceIndex) {
        return FACES[faceIndex];
    }

    /**
     * Converts a mesh into an array of lists of vanilla baked quads. Useful for creating vanilla baked models when
     * required for compatibility. The array indexes correspond to {@link EnumFacing#getIndex()} with the addition
     * of {@link #NULL_FACE_ID}.
     */
    @SuppressWarnings("unused")
    public static List<BakedQuad>[] toQuadLists(Mesh mesh) {
        @SuppressWarnings("unchecked") final ImmutableList.Builder<BakedQuad>[] builders = new ImmutableList.Builder[7];

        for (int i = 0; i < 7; i++) {
            builders[i] = ImmutableList.builder();
        }

        if (mesh != null) {
            mesh.forEach(q -> {
                EnumFacing cullFace = q.cullFace();
                builders[cullFace == null ? NULL_FACE_ID : cullFace.getIndex()]
                    .add(q.toBlockBakedQuad());
            });
        }

        @SuppressWarnings("unchecked")
        List<BakedQuad>[] result = new List[7];

        for (int i = 0; i < 7; i++) {
            result[i] = builders[i].build();
        }

        return result;
    }

    /**
     * The vanilla model transformation logic is closely coupled with model deserialization. That does little good for
     * modded model loaders and procedurally generated models. This convenient construction method applies the same
     * scaling factors used for vanilla models. This means you can use values from a vanilla JSON file as inputs to this
     * method.
     */
    @SuppressWarnings("SameParameterValue")
    private static ItemTransformVec3f makeTransform(float rotationX, float rotationY, float rotationZ, float translationX,
                                                    float translationY, float translationZ, float scaleX, float scaleY, float scaleZ) {
        Vector3f translation = new Vector3f(translationX, translationY, translationZ);
        translation.scale(0.0625f);
        translation = new Vector3f(
            MathHelper.clamp(translation.x, -5f, 5f),
            MathHelper.clamp(translation.y, -5f, 5f),
            MathHelper.clamp(translation.z, -5f, 5f));
        return new ItemTransformVec3f(new Vector3f(rotationX, rotationY, rotationZ), translation,
            new Vector3f(scaleX, scaleY, scaleZ));
    }
}
