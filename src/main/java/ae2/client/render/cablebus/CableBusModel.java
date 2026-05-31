package ae2.client.render.cablebus;

import ae2.api.parts.PartModels;
import ae2.api.util.AEColor;
import ae2.core.AELog;
import ae2.core.AppEng;
import ae2.core.definitions.AEParts;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

public class CableBusModel implements IModel {

    public static final ResourceLocation CABLE_ANCHOR_STILT_MODEL = AppEng.makeId("part/cable_anchor_short");

    @SuppressWarnings("unchecked")
    private static Collection<ResourceLocation> getRegisteredPartModels() {
        try {
            AEParts.init();

            Method freeze = PartModels.class.getDeclaredMethod("freeze");
            freeze.setAccessible(true);
            freeze.invoke(null);

            Method getModels = PartModels.class.getDeclaredMethod("getModels");
            getModels.setAccessible(true);

            return new ObjectLinkedOpenHashSet<>((Collection<ResourceLocation>) getModels.invoke(null));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to resolve registered part models.", e);
        }
    }

    @Override
    public Collection<ResourceLocation> getDependencies() {
        ObjectList<ResourceLocation> dependencies = new ObjectArrayList<>(getRegisteredPartModels());
        dependencies.add(CABLE_ANCHOR_STILT_MODEL);
        return dependencies;
    }

    @Override
    public Collection<ResourceLocation> getTextures() {
        ObjectList<ResourceLocation> textures = new ObjectArrayList<>(CableBuilder.getTextureDependencies());
        textures.add(AppEng.makeId("part/cable_anchor"));
        return textures;
    }

    @Override
    public IBakedModel bake(@Nonnull IModelState state, @Nonnull VertexFormat format,
                            @Nonnull Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        Map<ResourceLocation, IBakedModel> partModels = this.loadPartModels(state, format, bakedTextureGetter);
        CableBuilder cableBuilder = new CableBuilder(bakedTextureGetter);
        IBakedModel cableAnchorStiltModel = this.loadOptionalModel(state, format, bakedTextureGetter);
        FacadeBuilder facadeBuilder = new FacadeBuilder(cableAnchorStiltModel);
        TextureAtlasSprite particleTexture = cableBuilder.getCoreTexture(CableCoreType.GLASS, AEColor.TRANSPARENT);
        return new CableBusBakedModel(cableBuilder, facadeBuilder, partModels, particleTexture);
    }

    @Override
    public IModelState getDefaultState() {
        return TRSRTransformation.identity();
    }

    private Map<ResourceLocation, IBakedModel> loadPartModels(IModelState state, VertexFormat format,
                                                              Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        ImmutableMap.Builder<ResourceLocation, IBakedModel> result = ImmutableMap.builder();
        for (ResourceLocation location : getRegisteredPartModels()) {
            try {
                IBakedModel bakedModel = ModelLoaderRegistry.getModel(location).bake(state, format, bakedTextureGetter);
                if (bakedModel == null) {
                    AELog.warn("Failed to bake part model {}", location);
                } else {
                    result.put(location, bakedModel);
                }
            } catch (Exception e) {
                AELog.warn("Failed to bake part model {}", location);
            }
        }
        return result.build();
    }

    @Nullable
    private IBakedModel loadOptionalModel(IModelState state, VertexFormat format,
                                          Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        try {
            return ModelLoaderRegistry.getModel(CABLE_ANCHOR_STILT_MODEL).bake(state, format, bakedTextureGetter);
        } catch (Exception e) {
            AELog.warn("Failed to bake optional model {}", CABLE_ANCHOR_STILT_MODEL);
            return null;
        }
    }
}
