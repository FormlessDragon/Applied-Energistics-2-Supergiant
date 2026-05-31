package ae2.client.render.model;

import ae2.client.render.BasicUnbakedModel;
import ae2.core.AppEng;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.model.IModelState;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

public class WrappedGenericStackModel implements BasicUnbakedModel {
    private static final ResourceLocation PARTICLE = AppEng.makeId("block/white");

    @Override
    public Collection<ResourceLocation> getTextures() {
        return Collections.singleton(PARTICLE);
    }

    @Nullable
    @Override
    public IBakedModel bake(@Nonnull IModelState state, @Nonnull VertexFormat format,
                            @Nonnull Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        return new WrappedGenericStackBakedModel(format, state, bakedTextureGetter.apply(PARTICLE));
    }
}
