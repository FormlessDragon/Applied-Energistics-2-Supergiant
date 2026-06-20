package ae2.api.client.crafting;

import ae2.api.crafting.cpu.ICraftingUnitDefinition;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;

import java.util.function.Function;

/**
 * Creates the baked formed model for a crafting unit definition.
 */
public interface ICraftingUnitModelProvider {
    IBakedModel createFormedModel(ICraftingUnitDefinition definition,
                                  VertexFormat format,
                                  Function<ResourceLocation, TextureAtlasSprite> textureGetter);
}
