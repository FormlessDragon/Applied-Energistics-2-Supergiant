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

package ae2.client.render;

import ae2.client.render.cablebus.FacadeBuilder;
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

@SuppressWarnings("deprecation")
public class FacadeItemModel implements IModel {

    private static final ResourceLocation MODEL_BASE = AppEng.makeId("item/facade_base");

    @Override
    public Collection<ResourceLocation> getDependencies() {
        return Collections.singletonList(MODEL_BASE);
    }

    @Override
    public Collection<ResourceLocation> getTextures() {
        return Collections.emptyList();
    }

    @Override
    public IBakedModel bake(@NonNull IModelState state, @NonNull VertexFormat format,
                            @NonNull Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        IBakedModel bakedBaseModel = getBaseModel(state, format, bakedTextureGetter);
        FacadeBuilder facadeBuilder = new FacadeBuilder(null);
        return new FacadeDispatcherBakedModel(bakedBaseModel, facadeBuilder);
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
