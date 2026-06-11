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

package ae2.client.render.tesr.spatial;

import ae2.core.AppEng;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;

import org.jetbrains.annotations.NotNull;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public class SpatialPylonModel implements IModel {

    private static ResourceLocation getTexturePath(SpatialPylonTextureType type) {
        return AppEng.makeId("block/spatial_pylon/" + type.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public Collection<ResourceLocation> getDependencies() {
        return Collections.emptyList();
    }

    @Override
    public Collection<ResourceLocation> getTextures() {
        ObjectList<ResourceLocation> textures = new ObjectArrayList<>(SpatialPylonTextureType.values().length);
        for (SpatialPylonTextureType type : SpatialPylonTextureType.values()) {
            textures.add(getTexturePath(type));
        }
        return textures;
    }

    @Override
    public IBakedModel bake(@NotNull IModelState state, @NotNull VertexFormat format,
                            @NotNull Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        Map<SpatialPylonTextureType, TextureAtlasSprite> textures = new EnumMap<>(
            SpatialPylonTextureType.class);

        for (SpatialPylonTextureType type : SpatialPylonTextureType.values()) {
            textures.put(type, bakedTextureGetter.apply(getTexturePath(type)));
        }

        return new SpatialPylonBakedModel(textures);
    }

    @Override
    public IModelState getDefaultState() {
        return TRSRTransformation.identity();
    }
}
