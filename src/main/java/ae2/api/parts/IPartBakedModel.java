package ae2.api.parts;

import net.minecraft.client.renderer.block.model.BakedQuad;

import javax.annotation.Nullable;
import java.util.List;

public interface IPartBakedModel {
    List<BakedQuad> getPartQuads(@Nullable Object partModelData, long rand);
}
