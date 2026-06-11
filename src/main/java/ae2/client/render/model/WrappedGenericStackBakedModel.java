package ae2.client.render.model;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraftforge.client.model.BakedItemModel;
import net.minecraftforge.client.model.PerspectiveMapWrapper;
import net.minecraftforge.common.model.IModelState;

@SuppressWarnings("deprecation")
class WrappedGenericStackBakedModel extends BakedItemModel {
    @SuppressWarnings("unused")
    WrappedGenericStackBakedModel(VertexFormat format, IModelState state,
                                  TextureAtlasSprite particle) {
        super(ImmutableList.of(), particle, PerspectiveMapWrapper.getTransforms(state), ItemOverrideList.NONE, false);
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public ItemCameraTransforms getItemCameraTransforms() {
        return ItemCameraTransforms.DEFAULT;
    }
}
