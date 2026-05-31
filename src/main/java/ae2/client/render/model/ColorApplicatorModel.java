package ae2.client.render.model;

import ae2.client.render.BasicUnbakedModel;
import ae2.core.AppEng;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.model.IModelState;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

public class ColorApplicatorModel implements BasicUnbakedModel {
    private static final ResourceLocation MODEL_BASE = AppEng.makeId("item/color_applicator_colored");
    private static final ResourceLocation TEXTURE_DARK = AppEng.makeId("item/color_applicator_tip_dark");
    private static final ResourceLocation TEXTURE_MEDIUM = AppEng.makeId("item/color_applicator_tip_medium");
    private static final ResourceLocation TEXTURE_BRIGHT = AppEng.makeId("item/color_applicator_tip_bright");

    @Override
    public Collection<ResourceLocation> getDependencies() {
        return Collections.singleton(MODEL_BASE);
    }

    @Override
    public Collection<ResourceLocation> getTextures() {
        return java.util.Arrays.asList(TEXTURE_DARK, TEXTURE_MEDIUM, TEXTURE_BRIGHT);
    }

    @Nullable
    @Override
    public IBakedModel bake(@NonNull IModelState state, @NonNull VertexFormat format,
                            @NonNull Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        try {
            IBakedModel baseModel = net.minecraftforge.client.model.ModelLoaderRegistry.getModel(MODEL_BASE)
                                                                                       .bake(state, format, bakedTextureGetter);
            TextureAtlasSprite texDark = bakedTextureGetter.apply(TEXTURE_DARK);
            TextureAtlasSprite texMedium = bakedTextureGetter.apply(TEXTURE_MEDIUM);
            TextureAtlasSprite texBright = bakedTextureGetter.apply(TEXTURE_BRIGHT);
            return new ColorApplicatorBakedModel(baseModel, texDark, texMedium, texBright);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
