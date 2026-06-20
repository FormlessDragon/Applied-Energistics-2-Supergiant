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

import ae2.api.crafting.cpu.CraftingUnitVisualDefinition;
import com.google.common.collect.ImmutableList;
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
import java.util.Objects;
import java.util.function.Function;

public class CraftingCubeModel implements IModel {
    private final CraftingUnitVisualDefinition visualDefinition;

    public CraftingCubeModel(CraftingUnitVisualDefinition visualDefinition) {
        this.visualDefinition = Objects.requireNonNull(visualDefinition, "visualDefinition");
    }

    @Override
    public Collection<ResourceLocation> getDependencies() {
        return Collections.emptyList();
    }

    @Override
    public Collection<ResourceLocation> getTextures() {
        ImmutableList.Builder<ResourceLocation> builder = ImmutableList.builder();
        builder.add(
            this.visualDefinition.ringCornerTexture(),
            this.visualDefinition.ringSideHorTexture(),
            this.visualDefinition.ringSideVerTexture());
        if (this.visualDefinition.baseTexture() != null) {
            builder.add(this.visualDefinition.baseTexture());
        }
        if (this.visualDefinition.lightTexture() != null) {
            builder.add(this.visualDefinition.lightTexture());
        }
        if (this.visualDefinition.monitorBaseTexture() != null) {
            builder.add(this.visualDefinition.monitorBaseTexture());
        }
        if (this.visualDefinition.monitorLightDarkTexture() != null) {
            builder.add(this.visualDefinition.monitorLightDarkTexture());
        }
        if (this.visualDefinition.monitorLightMediumTexture() != null) {
            builder.add(this.visualDefinition.monitorLightMediumTexture());
        }
        if (this.visualDefinition.monitorLightBrightTexture() != null) {
            builder.add(this.visualDefinition.monitorLightBrightTexture());
        }
        return builder.build();
    }

    @Override
    public IBakedModel bake(@NotNull IModelState state, @NotNull VertexFormat format,
                            Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        TextureAtlasSprite ringCorner = bakedTextureGetter.apply(this.visualDefinition.ringCornerTexture());
        TextureAtlasSprite ringSideHor = bakedTextureGetter.apply(this.visualDefinition.ringSideHorTexture());
        TextureAtlasSprite ringSideVer = bakedTextureGetter.apply(this.visualDefinition.ringSideVerTexture());

        return switch (this.visualDefinition.visualKind()) {
            case UNIT -> new UnitBakedModel(format, ringCorner, ringSideHor, ringSideVer,
                bakedTextureGetter.apply(Objects.requireNonNull(this.visualDefinition.baseTexture(),
                    "Crafting unit visual definition missing base texture")));
            case LIGHT -> new LightBakedModel(format, ringCorner, ringSideHor, ringSideVer,
                bakedTextureGetter.apply(Objects.requireNonNull(this.visualDefinition.baseTexture(),
                    "Crafting light visual definition missing base texture")),
                bakedTextureGetter.apply(Objects.requireNonNull(this.visualDefinition.lightTexture(),
                    "Crafting light visual definition missing light texture")));
            case MONITOR -> new MonitorBakedModel(format, ringCorner, ringSideHor, ringSideVer,
                bakedTextureGetter.apply(Objects.requireNonNull(this.visualDefinition.baseTexture(),
                    "Crafting monitor visual definition missing chassis texture")),
                bakedTextureGetter.apply(Objects.requireNonNull(this.visualDefinition.monitorBaseTexture(),
                    "Crafting monitor visual definition missing base texture")),
                bakedTextureGetter.apply(Objects.requireNonNull(this.visualDefinition.monitorLightDarkTexture(),
                    "Crafting monitor visual definition missing dark light texture")),
                bakedTextureGetter.apply(Objects.requireNonNull(this.visualDefinition.monitorLightMediumTexture(),
                    "Crafting monitor visual definition missing medium light texture")),
                bakedTextureGetter.apply(Objects.requireNonNull(this.visualDefinition.monitorLightBrightTexture(),
                    "Crafting monitor visual definition missing bright light texture")));
            case CUSTOM -> throw new IllegalStateException(
                "Custom crafting unit visuals must be baked through a registered model provider");
        };
    }

    @Override
    public IModelState getDefaultState() {
        return TRSRTransformation.identity();
    }
}
