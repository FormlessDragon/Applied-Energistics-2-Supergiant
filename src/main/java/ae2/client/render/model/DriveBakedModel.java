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

import org.jetbrains.annotations.Nullable;
import javax.vecmath.Matrix4f;
import javax.vecmath.Vector3f;
import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecation")
public class DriveBakedModel implements IBakedModel {
    private static final EnumFacing[] CELL_SIDES = new EnumFacing[]{
        null,
        EnumFacing.DOWN,
        EnumFacing.UP,
        EnumFacing.NORTH,
        EnumFacing.SOUTH,
        EnumFacing.WEST,
        EnumFacing.EAST
    };

    private final IBakedModel bakedBase;
    private final Map<Item, IBakedModel> bakedCells;
    private final IBakedModel defaultCellModel;
    private final Matrix4f[] slotTransforms;

    public DriveBakedModel(IBakedModel bakedBase, Map<Item, IBakedModel> bakedCells, IBakedModel defaultCellModel,
                           TRSRTransformation transform) {
        this.bakedBase = bakedBase;
        this.bakedCells = bakedCells;
        this.defaultCellModel = defaultCellModel;
        this.slotTransforms = createSlotTransforms(transform);
    }

    public static void getSlotOrigin(int row, int col, Vector3f translation) {
        float xOffset = (9 - col * 8) / 16.0f;
        float yOffset = (13 - row * 3) / 16.0f;
        float zOffset = 1 / 16.0f;
        translation.set(xOffset, yOffset, zOffset);
    }

    private static Matrix4f[] createSlotTransforms(TRSRTransformation transform) {
        Matrix4f rotation = new Matrix4f();
        rotation.set(transform.getLeftRot());

        Matrix4f[] slotTransforms = new Matrix4f[10];
        Vector3f translation = new Vector3f();
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 2; col++) {
                getSlotOrigin(row, col, translation);
                rotation.transform(translation);
                slotTransforms[row * 2 + col] = createTranslationMatrix(translation);
            }
        }
        return slotTransforms;
    }

    private static Matrix4f createTranslationMatrix(Vector3f translation) {
        Matrix4f transform = new Matrix4f();
        transform.setIdentity();
        transform.setTranslation(translation);
        return transform;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        List<BakedQuad> result = new ObjectArrayList<>(this.bakedBase.getQuads(state, side, rand));

        if (side == null && state instanceof IExtendedBlockState extState) {
            DriveModelData renderState = extState.getValue(DriveBlock.RENDER_STATE);
            if (renderState == null) {
                return result;
            }

            for (int slot = 0; slot < this.slotTransforms.length; slot++) {
                MatrixVertexTransformer transformer = new MatrixVertexTransformer(this.slotTransforms[slot]);
                for (BakedQuad bakedQuad : getCellChassisQuads(state, renderState.getItem(slot), rand)) {
                    UnpackedBakedQuad.Builder builder = new UnpackedBakedQuad.Builder(bakedQuad.getFormat());
                    transformer.setParent(builder);
                    transformer.setVertexFormat(builder.getVertexFormat());
                    bakedQuad.pipe(transformer);
                    result.add(builder.build());
                }
            }
        }

        return result;
    }

    public IBakedModel getCellChassisModel(@Nullable Item item) {
        if (item == null) {
            IBakedModel emptyModel = this.bakedCells.get(Items.AIR);
            return emptyModel != null ? emptyModel : this.defaultCellModel;
        }
        IBakedModel model = this.bakedCells.get(item);
        return model != null ? model : this.defaultCellModel;
    }

    public List<BakedQuad> getCellChassisQuads(@Nullable IBlockState state, @Nullable Item item, long rand) {
        IBakedModel bakedCell = getCellChassisModel(item);
        if (bakedCell == null) {
            return List.of();
        }
        List<BakedQuad> result = new ObjectArrayList<>();
        for (EnumFacing cellSide : CELL_SIDES) {
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
