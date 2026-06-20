package ae2.api.client.crafting;

import ae2.api.crafting.cpu.ICraftingUnitDefinition;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Client-side registry for crafting unit formed model providers.
 */
public interface ICraftingUnitClientRegistry {
    void registerModelProvider(ResourceLocation id, ICraftingUnitModelProvider provider);

    @Nullable
    ICraftingUnitModelProvider getModelProvider(ResourceLocation id);

    IBakedModel createFormedModel(ICraftingUnitDefinition definition,
                                  VertexFormat format,
                                  Function<ResourceLocation, TextureAtlasSprite> textureGetter);
}
