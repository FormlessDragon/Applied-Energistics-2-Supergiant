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

import ae2.api.implementations.items.IMemoryCard;
import ae2.api.implementations.items.MemoryCardColors;
import ae2.api.util.AEColor;
import ae2.client.render.cablebus.CubeBuilder;
import ae2.core.AELog;
import ae2.items.tools.MemoryCardItem;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.common.model.TRSRTransformation;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

@SuppressWarnings("deprecation")
class MemoryCardBakedModel implements IBakedModel {
    private final VertexFormat format;
    private final IBakedModel baseModel;
    private final TextureAtlasSprite texture;
    private final MemoryCardColors colors;
    private final Cache<MemoryCardColors, MemoryCardBakedModel> modelCache;
    private final ImmutableList<BakedQuad> generalQuads;

    MemoryCardBakedModel(VertexFormat format, IBakedModel baseModel, TextureAtlasSprite texture) {
        this(format, baseModel, texture, MemoryCardColors.DEFAULT, createCache());
    }

    private MemoryCardBakedModel(VertexFormat format, IBakedModel baseModel, TextureAtlasSprite texture,
                                 MemoryCardColors colors, Cache<MemoryCardColors, MemoryCardBakedModel> modelCache) {
        this.format = format;
        this.baseModel = baseModel;
        this.texture = texture;
        this.colors = colors;
        this.generalQuads = ImmutableList.copyOf(this.buildGeneralQuads());
        this.modelCache = modelCache;
    }

    private static Cache<MemoryCardColors, MemoryCardBakedModel> createCache() {
        return CacheBuilder.newBuilder().maximumSize(100).build();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        List<BakedQuad> quads = this.baseModel.getQuads(state, side, rand);
        if (side != null) {
            return quads;
        }

        List<BakedQuad> result = new ObjectArrayList<>(quads.size() + this.generalQuads.size());
        result.addAll(quads);
        result.addAll(this.generalQuads);
        return result;
    }

    private List<BakedQuad> buildGeneralQuads() {
        CubeBuilder builder = new CubeBuilder(this.format);
        builder.setTexture(this.texture);

        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 2; y++) {
                AEColor color = this.colors.get(x, y);
                builder.setColorRGB(color.mediumVariant);
                builder.addCube(8 + x, 8 + 1 - y, 7.5f, 8 + x + 1, 8 + 1 - y + 1, 8.5f);
            }
        }

        return builder.getOutput();
    }

    @Override
    public boolean isAmbientOcclusion() {
        return this.baseModel.isAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return this.baseModel.isGui3d();
    }

    @Override
    public boolean isBuiltInRenderer() {
        return this.baseModel.isBuiltInRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return this.baseModel.getParticleTexture();
    }

    @Override
    public ItemCameraTransforms getItemCameraTransforms() {
        return this.baseModel.getItemCameraTransforms();
    }

    @Override
    public ItemOverrideList getOverrides() {
        return new ItemOverrideList(Collections.emptyList()) {
            @Override
            public IBakedModel handleItemState(IBakedModel originalModel, ItemStack stack, World world,
                                               EntityLivingBase entity) {
                try {
                    if (stack.getItem() instanceof IMemoryCard) {
                        MemoryCardColors colors = MemoryCardItem.getMemoryCardColors(stack);
                        return MemoryCardBakedModel.this.modelCache.get(colors,
                            () -> new MemoryCardBakedModel(MemoryCardBakedModel.this.format,
                                MemoryCardBakedModel.this.baseModel, MemoryCardBakedModel.this.texture, colors,
                                MemoryCardBakedModel.this.modelCache));
                    }
                } catch (ExecutionException e) {
                    AELog.error(e);
                }

                return MemoryCardBakedModel.this;
            }
        };
    }

    @Override
    public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
        Pair<? extends IBakedModel, Matrix4f> pair = this.baseModel.handlePerspective(type);
        if (pair != null) {
            return Pair.of(this, pair.getValue());
        }
        return Pair.of(this, TRSRTransformation.identity().getMatrix());
    }
}
