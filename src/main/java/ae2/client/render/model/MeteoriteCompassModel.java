package ae2.client.render.model;

import ae2.client.render.BasicUnbakedModel;
import com.google.common.collect.ImmutableSet;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.model.IModelState;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.function.Function;

public class MeteoriteCompassModel implements BasicUnbakedModel {

    private static final ResourceLocation MODEL_BASE = new ResourceLocation("ae2", "item/meteorite_compass_base");
    private static final ResourceLocation MODEL_POINTER = new ResourceLocation("ae2", "item/meteorite_compass_pointer");

    @Override
    public Collection<ResourceLocation> getDependencies() {
        return ImmutableSet.of(MODEL_BASE, MODEL_POINTER);
    }

    @Override
    public IBakedModel bake(@NonNull IModelState state, @NonNull VertexFormat format,
                            @NonNull Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        try {
            IBakedModel baseModel = net.minecraftforge.client.model.ModelLoaderRegistry.getModel(MODEL_BASE)
                                                                                       .bake(state, format, bakedTextureGetter);
            IBakedModel pointerModel = net.minecraftforge.client.model.ModelLoaderRegistry.getModel(MODEL_POINTER)
                                                                                          .bake(state, format, bakedTextureGetter);
            return new MeteoriteCompassBakedModel(baseModel, pointerModel);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public IModelState getDefaultState() {
        return BasicUnbakedModel.super.getDefaultState();
    }
}
