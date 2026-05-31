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

import ae2.block.storage.DriveBlock;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraftforge.common.property.IExtendedBlockState;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import javax.vecmath.Vector3f;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecation")
public class DriveBakedModel implements IBakedModel {
    private final IBakedModel bakedBase;
    private final Map<Item, IBakedModel> bakedCells;
    private final IBakedModel defaultCellModel;
    private final TRSRTransformation transform;

    public DriveBakedModel(IBakedModel bakedBase, Map<Item, IBakedModel> bakedCells, IBakedModel defaultCellModel,
                           TRSRTransformation transform) {
        this.bakedBase = bakedBase;
        this.bakedCells = bakedCells;
        this.defaultCellModel = defaultCellModel;
        this.transform = transform;
    }

    public static void getSlotOrigin(int row, int col, Vector3f translation) {
        float xOffset = (9 - col * 8) / 16.0f;
        float yOffset = (13 - row * 3) / 16.0f;
        float zOffset = 1 / 16.0f;
        translation.set(xOffset, yOffset, zOffset);
    }

    private static Iterable<EnumFacing> withNullFace() {
        List<EnumFacing> sides = new ObjectArrayList<>(7);
        sides.add(null);
        Collections.addAll(sides, EnumFacing.values());
        return sides;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        List<BakedQuad> result = new ObjectArrayList<>(this.bakedBase.getQuads(state, side, rand));

        if (side == null && state instanceof IExtendedBlockState extState) {
            DriveModelData renderState = extState.getValue(DriveBlock.RENDER_STATE);
            if (renderState == null) {
                return result;
            }

            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < 2; col++) {
                    Vector3f translation = new Vector3f();
                    getSlotOrigin(row, col, translation);
                    rotateSlotTranslation(translation);
                    MatrixVertexTransformer transformer = new MatrixVertexTransformer(createTranslationMatrix(translation));
                    for (BakedQuad bakedQuad : getCellChassisQuads(state, renderState.getItem(row * 2 + col), rand)) {
                        UnpackedBakedQuad.Builder builder = new UnpackedBakedQuad.Builder(bakedQuad.getFormat());
                        transformer.setParent(builder);
                        transformer.setVertexFormat(builder.getVertexFormat());
                        bakedQuad.pipe(transformer);
                        result.add(builder.build());
                    }
                }
            }
        }

        return result;
    }

    private void rotateSlotTranslation(Vector3f translation) {
        Matrix4f rotation = new Matrix4f();
        rotation.set(this.transform.getLeftRot());
        rotation.transform(translation);
    }

    private Matrix4f createTranslationMatrix(Vector3f translation) {
        Matrix4f transform = new Matrix4f();
        transform.setIdentity();
        transform.setTranslation(translation);
        return transform;
    }

    public IBakedModel getCellChassisModel(@Nullable Item item) {
        if (item == null) {
            return this.bakedCells.get(Items.AIR);
        }
        IBakedModel model = this.bakedCells.get(item);
        return model != null ? model : this.defaultCellModel;
    }

    public List<BakedQuad> getCellChassisQuads(@Nullable IBlockState state, @Nullable Item item, long rand) {
        IBakedModel bakedCell = getCellChassisModel(item);
        List<BakedQuad> result = new ObjectArrayList<>();
        for (EnumFacing cellSide : withNullFace()) {
            result.addAll(bakedCell.getQuads(state, cellSide, rand));
        }
        return result;
    }

    @Override
    public boolean isAmbientOcclusion() {
        return this.bakedBase.isAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return this.bakedBase.isGui3d();
    }

    @Override
    public boolean isBuiltInRenderer() {
        return this.bakedBase.isBuiltInRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return this.bakedBase.getParticleTexture();
    }

    @Override
    public ItemCameraTransforms getItemCameraTransforms() {
        return this.bakedBase.getItemCameraTransforms();
    }

    @Override
    public ItemOverrideList getOverrides() {
        return this.bakedBase.getOverrides();
    }
}
