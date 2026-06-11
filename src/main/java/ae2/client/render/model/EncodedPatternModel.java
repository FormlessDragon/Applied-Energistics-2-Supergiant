package ae2.client.render.model;

import ae2.client.render.BasicUnbakedModel;
import ae2.core.AppEng;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;

import org.jetbrains.annotations.NotNull;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

@SuppressWarnings("deprecation")
public class EncodedPatternModel implements BasicUnbakedModel {
    private final ResourceLocation baseModel;

    public EncodedPatternModel(String baseModel) {
        this.baseModel = AppEng.makeId(baseModel);
    }

    @Override
    public Collection<ResourceLocation> getDependencies() {
        return Collections.singleton(this.baseModel);
    }

    @Override
    public IBakedModel bake(@NotNull IModelState state, @NotNull VertexFormat format,
                            @NotNull Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        try {
            IBakedModel base = ModelLoaderRegistry.getModel(this.baseModel).bake(state, format, bakedTextureGetter);
            return new EncodedPatternBakedModel(base);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public IModelState getDefaultState() {
        return TRSRTransformation.identity().toItemTransform();
    }
}
