package ae2.client.render;

import ae2.api.util.AEColor;
import ae2.client.render.GlowRenderer.GlowColor;
import ae2.client.render.bloom.BeamFormerBloom;
import ae2.helpers.beamformer.BeamFormerEndpoint;
import ae2.helpers.beamformer.BeamFormerRenderGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class BeamFormerRenderer {

    private static final float BEAM_ALPHA = 1.0F;
    private static final double CORE_RADIUS_SCALE = 0.72;
    private static final GlowColor[] COLOR_CACHE = buildColorCache();

    private BeamFormerRenderer() {
    }

    private static GlowColor[] buildColorCache() {
        AEColor[] colors = AEColor.values();
        GlowColor[] cache = new GlowColor[colors.length];
        for (AEColor color : colors) {
            cache[color.ordinal()] = GlowColor.fromRgb(color.mediumVariant);
        }
        return cache;
    }

    public static void render(BeamFormerEndpoint endpoint, double x, double y, double z) {
        if (!BeamFormerRenderGeometry.shouldRender(endpoint)) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!BeamFormerRenderGeometry.isNearEnoughToRender(endpoint, minecraft.getRenderManager().viewerPosX,
            minecraft.getRenderManager().viewerPosY, minecraft.getRenderManager().viewerPosZ)) {
            return;
        }

        EnumFacing direction = endpoint.getBeamDirection();
        AEColor color = endpoint.getBeamColor();
        double radius = endpoint.getBeamRadius();
        double length = endpoint.getBeamLength();
        GlowColor laserColor = COLOR_CACHE[color.ordinal()];
        double startX = BeamFormerRenderGeometry.computeBeamStartX(endpoint);
        double startY = BeamFormerRenderGeometry.computeBeamStartY(endpoint);
        double startZ = BeamFormerRenderGeometry.computeBeamStartZ(endpoint);
        BeamFormerBloom.queue(startX, startY, startZ,
            startX + direction.getXOffset() * length,
            startY + direction.getYOffset() * length,
            startZ + direction.getZOffset() * length,
            direction, radius, laserColor);
    }

    public static void renderBeam(BufferBuilder buffer, double startX, double startY, double startZ, double endX,
                                  double endY, double endZ, EnumFacing direction, double radius, GlowColor laserColor) {
        GlowRenderer.addCylinder(buffer, startX, startY, startZ, endX, endY, endZ, direction,
            radius * CORE_RADIUS_SCALE, laserColor, BEAM_ALPHA);
    }
}
