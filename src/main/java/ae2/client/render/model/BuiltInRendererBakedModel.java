package ae2.client.render.model;

import ae2.client.render.DelegateBakedModel;
import net.minecraft.client.renderer.block.model.IBakedModel;

public class BuiltInRendererBakedModel extends DelegateBakedModel {
    public BuiltInRendererBakedModel(IBakedModel base) {
        super(base);
    }

    @Override
    public boolean isBuiltInRenderer() {
        return true;
    }
}
