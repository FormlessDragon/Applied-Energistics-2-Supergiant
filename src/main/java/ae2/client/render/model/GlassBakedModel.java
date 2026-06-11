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

import ae2.decorative.solid.GlassState;
import ae2.decorative.solid.QuartzGlassBlock;
import com.google.common.base.Strings;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import net.minecraftforge.common.property.IExtendedBlockState;

import org.jetbrains.annotations.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.IntStream;

@SuppressWarnings("deprecation")
public class GlassBakedModel implements IBakedModel {
    static final ResourceLocation TEXTURE_A = new ResourceLocation("ae2:block/glass/quartz_glass_a");
    static final ResourceLocation TEXTURE_B = new ResourceLocation("ae2:block/glass/quartz_glass_b");
    static final ResourceLocation TEXTURE_C = new ResourceLocation("ae2:block/glass/quartz_glass_c");
    static final ResourceLocation TEXTURE_D = new ResourceLocation("ae2:block/glass/quartz_glass_d");
    static final ResourceLocation[] TEXTURES_FRAME = generateTexturesFrame();
    private static final byte[][][] OFFSETS = generateOffsets();
    private final TextureAtlasSprite[] glassTextures;
    private final TextureAtlasSprite[] frameTextures;
    private final VertexFormat vertexFormat;

    public GlassBakedModel(VertexFormat format, Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        this.glassTextures = new TextureAtlasSprite[]{
            bakedTextureGetter.apply(TEXTURE_A),
            bakedTextureGetter.apply(TEXTURE_B),
            bakedTextureGetter.apply(TEXTURE_C),
            bakedTextureGetter.apply(TEXTURE_D)
        };

        this.vertexFormat = format;
        this.frameTextures = new TextureAtlasSprite[16];
        for (int i = 0; i < TEXTURES_FRAME.length; i++) {
            this.frameTextures[1 + i] = bakedTextureGetter.apply(TEXTURES_FRAME[i]);
        }
    }

    private static ResourceLocation[] generateTexturesFrame() {
        return IntStream.range(1, 16)
                        .mapToObj(Integer::toBinaryString)
                        .map(s -> Strings.padStart(s, 4, '0'))
                        .map(s -> new ResourceLocation("ae2:block/glass/quartz_glass_frame" + s))
                        .toArray(ResourceLocation[]::new);
    }

    private static byte[][][] generateOffsets() {
        Random random = new Random(924);
        byte[][][] offset = new byte[10][10][10];

        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                random.nextBytes(offset[x][y]);
            }
        }

        return offset;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        if (side == null) {
            return Collections.emptyList();
        }

        GlassState glassState = GlassState.DEFAULT;
        if (state instanceof IExtendedBlockState) {
            GlassState extendedGlassState = ((IExtendedBlockState) state).getValue(QuartzGlassBlock.GLASS_STATE);
            if (extendedGlassState != null) {
                glassState = extendedGlassState;
            }
        }

        if (glassState.hasAdjacentGlassBlock(side)) {
            return Collections.emptyList();
        }

        int cx = Math.abs((int) (rand & 0xF) % 10);
        int cy = Math.abs((int) ((rand >> 4) & 0xF) % 10);
        int cz = Math.abs((int) ((rand >> 8) & 0xF) % 10);

        int u = OFFSETS[cx][cy][cz] % 4;
        int v = OFFSETS[9 - cx][9 - cy][9 - cz] % 4;
        int texIdx = Math.abs(OFFSETS[cx][cy][cz] % 4);

        if (texIdx < 2) {
            u /= 2;
            v /= 2;
        }

        TextureAtlasSprite glassTexture = this.glassTextures[texIdx];

        List<BakedQuad> quads = new ObjectArrayList<>(5);
        List<Vec3d> corners = RenderHelper.getFaceCorners(side);
        quads.add(this.createQuad(side, corners, glassTexture, u, v));

        int edgeBitmask = glassState.getMask(side);
        TextureAtlasSprite sideSprite = this.frameTextures[edgeBitmask];

        if (sideSprite != null) {
            quads.add(this.createQuad(side, corners, sideSprite, 0, 0));
        }

        return quads;
    }

    private BakedQuad createQuad(EnumFacing side, List<Vec3d> corners, TextureAtlasSprite sprite, float uOffset,
                                 float vOffset) {
        return this.createQuad(side, corners.get(0), corners.get(1), corners.get(2), corners.get(3), sprite, uOffset,
            vOffset);
    }

    private BakedQuad createQuad(EnumFacing side, Vec3d c1, Vec3d c2, Vec3d c3, Vec3d c4,
                                 TextureAtlasSprite sprite, float uOffset, float vOffset) {
        Vec3d normal = new Vec3d(side.getDirectionVec());

        float u1 = MathHelper.clamp(0 - uOffset, 0, 16);
        float u2 = MathHelper.clamp(16 - uOffset, 0, 16);
        float v1 = MathHelper.clamp(0 - vOffset, 0, 16);
        float v2 = MathHelper.clamp(16 - vOffset, 0, 16);

        UnpackedBakedQuad.Builder builder = new UnpackedBakedQuad.Builder(this.vertexFormat);
        builder.setTexture(sprite);
        builder.setQuadOrientation(side);
        builder.setQuadTint(-1);
        builder.setApplyDiffuseLighting(false);
        this.putVertex(builder, normal, c1.x, c1.y, c1.z, sprite, u1, v1);
        this.putVertex(builder, normal, c2.x, c2.y, c2.z, sprite, u1, v2);
        this.putVertex(builder, normal, c3.x, c3.y, c3.z, sprite, u2, v2);
        this.putVertex(builder, normal, c4.x, c4.y, c4.z, sprite, u2, v1);
        return builder.build();
    }

    /*
     * This method is as complicated as it is, because the order in which we push data into the vertexbuffer actually
     * has to be precisely the order in which the vertex elements had been declared in the vertex format.
     */
    private void putVertex(UnpackedBakedQuad.Builder builder, Vec3d normal, double x, double y, double z,
                           TextureAtlasSprite sprite, float u, float v) {
        for (int e = 0; e < this.vertexFormat.getElementCount(); e++) {
            switch (this.vertexFormat.getElement(e).getUsage()) {
                case POSITION -> builder.put(e, (float) x, (float) y, (float) z, 1.0f);
                case COLOR -> builder.put(e, 1.0f, 1.0f, 1.0f, 1.0f);
                case UV -> {
                    if (this.vertexFormat.getElement(e).getIndex() == 0) {
                        builder.put(e, sprite.getInterpolatedU(u), sprite.getInterpolatedV(v), 0f, 1f);
                    } else {
                        final float lightMapU = (float) (15 * 0x20) / 0xFFFF;
                        final float lightMapV = (float) (15 * 0x20) / 0xFFFF;
                        builder.put(e, lightMapU, lightMapV);
                    }
                }
                case NORMAL -> builder.put(e, (float) normal.x, (float) normal.y, (float) normal.z, 0f);
                default -> builder.put(e);
            }
        }
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
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return this.frameTextures[this.frameTextures.length - 1];
    }

    @Override
    public ItemCameraTransforms getItemCameraTransforms() {
        return ItemCameraTransforms.DEFAULT;
    }

    @Override
    public ItemOverrideList getOverrides() {
        return ItemOverrideList.NONE;
    }
}
