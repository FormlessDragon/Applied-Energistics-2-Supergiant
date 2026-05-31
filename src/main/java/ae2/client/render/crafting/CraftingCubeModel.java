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

package ae2.client.render.crafting;

import ae2.block.crafting.CraftingUnitType;
import ae2.core.AppEng;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

public class CraftingCubeModel implements IModel {
    private static final ResourceLocation RING_CORNER = texture("ring_corner");
    private static final ResourceLocation RING_SIDE_HOR = texture("ring_side_hor");
    private static final ResourceLocation RING_SIDE_VER = texture("ring_side_ver");
    private static final ResourceLocation UNIT_BASE = texture("unit_base");
    private static final ResourceLocation LIGHT_BASE = texture("light_base");
    private static final ResourceLocation ACCELERATOR_LIGHT = texture("accelerator_light");
    private static final ResourceLocation STORAGE_1K_LIGHT = texture("1k_storage_light");
    private static final ResourceLocation STORAGE_4K_LIGHT = texture("4k_storage_light");
    private static final ResourceLocation STORAGE_16K_LIGHT = texture("16k_storage_light");
    private static final ResourceLocation STORAGE_64K_LIGHT = texture("64k_storage_light");
    private static final ResourceLocation STORAGE_256K_LIGHT = texture("256k_storage_light");
    private static final ResourceLocation MONITOR_BASE = texture("monitor_base");
    private static final ResourceLocation MONITOR_LIGHT_DARK = texture("monitor_light_dark");
    private static final ResourceLocation MONITOR_LIGHT_MEDIUM = texture("monitor_light_medium");
    private static final ResourceLocation MONITOR_LIGHT_BRIGHT = texture("monitor_light_bright");

    private final CraftingUnitType type;

    public CraftingCubeModel(CraftingUnitType type) {
        this.type = type;
    }

    private static TextureAtlasSprite getLightTexture(Function<ResourceLocation, TextureAtlasSprite> textureGetter,
                                                      CraftingUnitType type) {
        return switch (type) {
            case ACCELERATOR -> textureGetter.apply(ACCELERATOR_LIGHT);
            case STORAGE_1K -> textureGetter.apply(STORAGE_1K_LIGHT);
            case STORAGE_4K -> textureGetter.apply(STORAGE_4K_LIGHT);
            case STORAGE_16K -> textureGetter.apply(STORAGE_16K_LIGHT);
            case STORAGE_64K -> textureGetter.apply(STORAGE_64K_LIGHT);
            default -> textureGetter.apply(STORAGE_256K_LIGHT);
        };
    }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation(AppEng.MOD_ID, "block/crafting/" + name);
    }

    @Override
    public Collection<ResourceLocation> getDependencies() {
        return Collections.emptyList();
    }

    @Override
    public Collection<ResourceLocation> getTextures() {
        return ImmutableList.of(RING_CORNER, RING_SIDE_HOR, RING_SIDE_VER, UNIT_BASE, LIGHT_BASE,
            ACCELERATOR_LIGHT, STORAGE_1K_LIGHT, STORAGE_4K_LIGHT, STORAGE_16K_LIGHT, STORAGE_64K_LIGHT,
            STORAGE_256K_LIGHT, MONITOR_BASE, MONITOR_LIGHT_DARK, MONITOR_LIGHT_MEDIUM, MONITOR_LIGHT_BRIGHT);
    }

    @Override
    public IBakedModel bake(@NonNull IModelState state, @NonNull VertexFormat format,
                            Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        TextureAtlasSprite ringCorner = bakedTextureGetter.apply(RING_CORNER);
        TextureAtlasSprite ringSideHor = bakedTextureGetter.apply(RING_SIDE_HOR);
        TextureAtlasSprite ringSideVer = bakedTextureGetter.apply(RING_SIDE_VER);

        return switch (this.type) {
            case UNIT -> new UnitBakedModel(format, ringCorner, ringSideHor, ringSideVer,
                bakedTextureGetter.apply(UNIT_BASE));
            case ACCELERATOR, STORAGE_1K, STORAGE_4K, STORAGE_16K, STORAGE_64K, STORAGE_256K ->
                new LightBakedModel(format, ringCorner, ringSideHor, ringSideVer,
                    bakedTextureGetter.apply(LIGHT_BASE), getLightTexture(bakedTextureGetter, this.type));
            case MONITOR -> new MonitorBakedModel(format, ringCorner, ringSideHor, ringSideVer,
                bakedTextureGetter.apply(UNIT_BASE), bakedTextureGetter.apply(MONITOR_BASE),
                bakedTextureGetter.apply(MONITOR_LIGHT_DARK), bakedTextureGetter.apply(MONITOR_LIGHT_MEDIUM),
                bakedTextureGetter.apply(MONITOR_LIGHT_BRIGHT));
        };
    }

    @Override
    public IModelState getDefaultState() {
        return TRSRTransformation.identity();
    }
}
