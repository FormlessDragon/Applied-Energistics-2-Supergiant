package ae2.client.render.tesr;

import ae2.client.render.BeamFormerRenderer;
import ae2.helpers.beamformer.BeamFormerRenderGeometry;
import ae2.tile.networking.TileDenseBeamFormer;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;

public class DenseBeamFormerTESR extends TileEntitySpecialRenderer<TileDenseBeamFormer> {

    @Override
    public void render(TileDenseBeamFormer te, double x, double y, double z, float partialTicks, int destroyStage,
                       float partial) {
        if (te != null && BeamFormerRenderGeometry.shouldRender(te)) {
            BeamFormerRenderer.render(te, x, y, z);
        }
    }
}
