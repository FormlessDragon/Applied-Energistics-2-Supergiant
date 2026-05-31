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

package ae2.client.render.cablebus;

import ae2.api.parts.IPartBakedModel;
import ae2.api.util.AEColor;
import ae2.util.Platform;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class P2PTunnelFrequencyBakedModel implements IBakedModel, IPartBakedModel {

    private static final Cache<Long, List<BakedQuad>> MODEL_CACHE = CacheBuilder.newBuilder().maximumSize(100).build();
    private static final int[][] QUAD_OFFSETS = new int[][]{{3, 11, 2}, {11, 11, 2}, {3, 3, 2}, {11, 3, 2}};

    private final VertexFormat format;
    private final TextureAtlasSprite texture;

    public P2PTunnelFrequencyBakedModel(VertexFormat format, TextureAtlasSprite texture) {
        this.format = format;
        this.texture = texture;
    }

    @Override
    public List<BakedQuad> getPartQuads(Object partModelData, long rand) {
        long resolvedFlags = partModelData instanceof Long ? (Long) partModelData : 0L;
        try {
            return MODEL_CACHE.get(resolvedFlags, () -> this.getQuadsForFrequency(
                P2PTunnelFrequencyModelData.getFrequency(resolvedFlags),
                P2PTunnelFrequencyModelData.isActive(resolvedFlags)));
        } catch (ExecutionException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
        if (side != null) {
            return Collections.emptyList();
        }
        return this.getPartQuads(null, rand);
    }

    private List<BakedQuad> getQuadsForFrequency(short frequency, boolean active) {
        AEColor[] colors = Platform.p2p().toColors(frequency);
        CubeBuilder builder = new CubeBuilder(this.format);
        builder.setTexture(this.texture);
        builder.useStandardUV();
        builder.setRenderFullBright(active);

        for (int i = 0; i < 4; ++i) {
            int[] offsets = QUAD_OFFSETS[i];
            for (int j = 0; j < 4; ++j) {
                AEColor color = colors[j];
                if (active) {
                    builder.setColorRGB(color.mediumVariant);
                } else {
                    final float scale = 0.3f / 255.0f;
                    builder.setColorRGB((color.blackVariant >> 16 & 0xff) * scale,
                        (color.blackVariant >> 8 & 0xff) * scale, (color.blackVariant & 0xff) * scale);
                }

                int startX = j % 2;
                int startY = 1 - j / 2;
                builder.addCube(offsets[0] + startX, offsets[1] + startY, offsets[2],
                    offsets[0] + startX + 1, offsets[1] + startY + 1, offsets[2] + 1);
            }
        }

        return builder.getOutput();
    }

    @Override
    public boolean isAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public boolean isBuiltInRenderer() {
        return true;
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return this.texture;
    }

    @Override
    public ItemOverrideList getOverrides() {
        return ItemOverrideList.NONE;
    }
}
