/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
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

import ae2.core.AppEng;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

/**
 * Model wrapper for the memory card item model, which combines a base card layer with a "visual hash" of the part/tile.
 */
@SuppressWarnings("deprecation")
public class MemoryCardModel implements IModel {

    public static final ResourceLocation MODEL_BASE = AppEng.makeId("item/memory_card_base");
    private static final ResourceLocation TEXTURE = AppEng.makeId("item/memory_card_hash");

    @Override
    public Collection<ResourceLocation> getDependencies() {
        return Collections.singletonList(MODEL_BASE);
    }

    @Override
    public Collection<ResourceLocation> getTextures() {
        return Collections.singletonList(TEXTURE);
    }

    @Override
    public IBakedModel bake(@NonNull IModelState state, @NonNull VertexFormat format,
                            Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        TextureAtlasSprite texture = bakedTextureGetter.apply(TEXTURE);
        IBakedModel baseModel = this.getBaseModel(state, format, bakedTextureGetter);
        return new MemoryCardBakedModel(format, baseModel, texture);
    }

    private IBakedModel getBaseModel(IModelState state, VertexFormat format,
                                     Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        try {
            return ModelLoaderRegistry.getModel(MODEL_BASE).bake(state, format, bakedTextureGetter);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public IModelState getDefaultState() {
        return TRSRTransformation.identity().toItemTransform();
    }
}
