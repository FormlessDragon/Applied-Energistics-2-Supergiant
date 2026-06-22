package ae2.client.render.cablebus;

import ae2.core.AppEng;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ResourceLocation;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

public class SmartCableTextures {

    public static final ResourceLocation[] SMART_CHANNELS_TEXTURES = new ResourceLocation[]{
        AppEng.makeId("part/cable/smart/channels_00"),
        AppEng.makeId("part/cable/smart/channels_01"),
        AppEng.makeId("part/cable/smart/channels_02"),
        AppEng.makeId("part/cable/smart/channels_03"),
        AppEng.makeId("part/cable/smart/channels_04"),
        AppEng.makeId("part/cable/smart/channels_10"),
        AppEng.makeId("part/cable/smart/channels_11"),
        AppEng.makeId("part/cable/smart/channels_12"),
        AppEng.makeId("part/cable/smart/channels_13"),
        AppEng.makeId("part/cable/smart/channels_14")
    };

    public static final ResourceLocation[] DENSE_SMART_CHANNELS_TEXTURES = new ResourceLocation[]{
        AppEng.makeId("part/cable/dense_smart/channels_00"),
        AppEng.makeId("part/cable/dense_smart/channels_01"),
        AppEng.makeId("part/cable/dense_smart/channels_02"),
        AppEng.makeId("part/cable/dense_smart/channels_03"),
        AppEng.makeId("part/cable/dense_smart/channels_04"),
        AppEng.makeId("part/cable/dense_smart/channels_10"),
        AppEng.makeId("part/cable/dense_smart/channels_11"),
        AppEng.makeId("part/cable/dense_smart/channels_12"),
        AppEng.makeId("part/cable/dense_smart/channels_13"),
        AppEng.makeId("part/cable/dense_smart/channels_14")
    };
    private static final Collection<ResourceLocation> TEXTURE_DEPENDENCIES = buildTextureDependencies();

    private final TextureAtlasSprite[] textures;
    private final TextureAtlasSprite[] denseTextures;

    public SmartCableTextures(Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        this.textures = bakeTextures(SMART_CHANNELS_TEXTURES, bakedTextureGetter);
        this.denseTextures = bakeTextures(DENSE_SMART_CHANNELS_TEXTURES, bakedTextureGetter);
    }

    public static Collection<ResourceLocation> getTextureDependencies() {
        return TEXTURE_DEPENDENCIES;
    }

    private static TextureAtlasSprite[] bakeTextures(ResourceLocation[] textureIds,
                                                     Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        var textures = new TextureAtlasSprite[textureIds.length];
        for (int i = 0; i < textureIds.length; i++) {
            textures[i] = bakedTextureGetter.apply(textureIds[i]);
        }
        return textures;
    }

    private static Collection<ResourceLocation> buildTextureDependencies() {
        ObjectLinkedOpenHashSet<ResourceLocation> result = new ObjectLinkedOpenHashSet<>();
        result.addAll(Arrays.asList(SMART_CHANNELS_TEXTURES));
        result.addAll(Arrays.asList(DENSE_SMART_CHANNELS_TEXTURES));
        return Collections.unmodifiableCollection(result);
    }

    public TextureAtlasSprite getOddTextureForChannels(int channels) {
        if (channels < 0) {
            return this.textures[0];
        }
        if (channels <= 4) {
            return this.textures[channels];
        }
        return this.textures[4];
    }

    public TextureAtlasSprite getEvenTextureForChannels(int channels) {
        if (channels < 5) {
            return this.textures[5];
        }
        if (channels <= 8) {
            return this.textures[1 + channels];
        }
        return this.textures[9];
    }

    public TextureAtlasSprite getOddTextureForDenseChannels(int channels) {
        if (channels < 0) {
            return this.denseTextures[0];
        }
        if (channels <= 4) {
            return this.denseTextures[channels];
        }
        return this.denseTextures[4];
    }

    public TextureAtlasSprite getEvenTextureForDenseChannels(int channels) {
        if (channels < 5) {
            return this.denseTextures[5];
        }
        if (channels <= 8) {
            return this.denseTextures[1 + channels];
        }
        return this.denseTextures[9];
    }
}
