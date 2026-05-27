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
package appeng.client.render.model;

import appeng.api.client.StorageCellModels;
import appeng.client.render.BasicUnbakedModel;
import appeng.core.AppEng;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

public class DriveModel implements BasicUnbakedModel {
    private static final ResourceLocation MODEL_BASE = AppEng.makeId("block/drive/drive_base");
    private static final ResourceLocation MODEL_CELL_EMPTY = AppEng.makeId("block/drive/drive_cell_empty");

    @Override
    public Collection<ResourceLocation> getDependencies() {
        return ImmutableSet.<ResourceLocation>builder()
                           .add(MODEL_BASE)
                           .add(MODEL_CELL_EMPTY)
                           .add(StorageCellModels.getDefaultModel())
                           .addAll(StorageCellModels.models().values())
                           .build();
    }

    @Override
    public IBakedModel bake(@Nonnull IModelState state, @Nonnull VertexFormat format,
                            @Nonnull Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        Map<Item, IBakedModel> cellModels = new Reference2ObjectOpenHashMap<>();

        try {
            for (Map.Entry<Item, ResourceLocation> entry : StorageCellModels.models().entrySet()) {
                cellModels.put(entry.getKey(),
                    ModelLoaderRegistry.getModel(entry.getValue()).bake(state, format, bakedTextureGetter));
            }

            IBakedModel baseModel = ModelLoaderRegistry.getModel(MODEL_BASE).bake(state, format, bakedTextureGetter);
            IBakedModel defaultCellModel = ModelLoaderRegistry.getModel(StorageCellModels.getDefaultModel())
                                                              .bake(state, format, bakedTextureGetter);
            cellModels.put(Items.AIR,
                ModelLoaderRegistry.getModel(MODEL_CELL_EMPTY).bake(state, format, bakedTextureGetter));
            TRSRTransformation transform = state.apply(java.util.Optional.empty())
                                                .orElse(TRSRTransformation.identity());
            return new DriveBakedModel(baseModel, cellModels, defaultCellModel, transform);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
