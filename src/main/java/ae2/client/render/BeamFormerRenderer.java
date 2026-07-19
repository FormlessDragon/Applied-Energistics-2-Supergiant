package ae2.client.render;

import ae2.api.util.AEColor;
import ae2.client.render.GlowRenderer.GlowColor;
import ae2.client.render.bloom.BeamFormerBloom;
import ae2.core.AEConfig;
import ae2.helpers.beamformer.BeamFormerEndpoint;
import ae2.helpers.beamformer.BeamFormerRenderGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityBeaconRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

@SideOnly(Side.CLIENT)
public final class BeamFormerRenderer {

    private static final float BEAM_ALPHA = 1.0F;
    private static final double CORE_RADIUS_SCALE = 0.72;
    private static final GlowColor[] COLOR_CACHE = buildColorCache();
    private static final float[][] BEACON_COLOR_CACHE = buildBeaconColorCache();

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

    private static float[][] buildBeaconColorCache() {
        GlowColor[] colors = COLOR_CACHE;
        float[][] cache = new float[colors.length][];
        for (int i = 0; i < colors.length; i++) {
            GlowColor color = colors[i];
            cache[i] = new float[] { color.red(), color.green(), color.blue() };
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
        double endX = startX + direction.getXOffset() * length;
        double endY = startY + direction.getYOffset() * length;
        double endZ = startZ + direction.getZOffset() * length;
        if (AEConfig.instance().isBeamFormerLowCostRendering()) {
            if (compareEndpoints(startX, startY, startZ, endX, endY, endZ) <= 0) {
                renderVanillaBeaconBeam(endpoint, x, y, z, direction, length, radius, color, minecraft);
            }
            return;
        }
        BeamFormerBloom.queue(startX, startY, startZ, endX, endY, endZ, direction, radius, laserColor);
    }

    private static void renderVanillaBeaconBeam(BeamFormerEndpoint endpoint, double x, double y, double z,
                                                EnumFacing direction, double length, double radius, AEColor color,
                                                Minecraft minecraft) {
        int segmentHeight = (int) Math.ceil(length);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.enableTexture2D();
            minecraft.getTextureManager().bindTexture(TileEntityBeaconRenderer.TEXTURE_BEACON_BEAM);
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GlStateManager.disableFog();
            GlStateManager.translate(
                x + 0.5D + endpoint.getBeamOriginOffsetX(),
                y + 0.5D + endpoint.getBeamOriginOffsetY(),
                z + 0.5D + endpoint.getBeamOriginOffsetZ());
            rotateFromUp(direction);
            GlStateManager.scale(1.0D, length / segmentHeight, 1.0D);
            TileEntityBeaconRenderer.renderBeamSegment(
                -0.5D, 0.0D, -0.5D,
                minecraft.getRenderPartialTicks(), 1.0D, endpoint.getBeamWorld().getTotalWorldTime(),
                0, segmentHeight, BEACON_COLOR_CACHE[color.ordinal()], radius * CORE_RADIUS_SCALE, radius * 1.25D);
        } finally {
            GlStateManager.popMatrix();
            restoreVanillaBeaconRenderState(minecraft);
        }
    }

    private static void restoreVanillaBeaconRenderState(Minecraft minecraft) {
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.enableFog();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        minecraft.getTextureManager().bindTexture(TileEntityBeaconRenderer.TEXTURE_BEACON_BEAM);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GlStateManager.bindTexture(0);
    }

    private static void rotateFromUp(EnumFacing direction) {
        switch (direction) {
            case DOWN -> GlStateManager.rotate(180.0F, 1.0F, 0.0F, 0.0F);
            case NORTH -> GlStateManager.rotate(-90.0F, 1.0F, 0.0F, 0.0F);
            case SOUTH -> GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
            case WEST -> GlStateManager.rotate(90.0F, 0.0F, 0.0F, 1.0F);
            case EAST -> GlStateManager.rotate(-90.0F, 0.0F, 0.0F, 1.0F);
            case UP -> {
            }
        }
    }

    private static int compareEndpoints(double startX, double startY, double startZ, double endX, double endY,
                                        double endZ) {
        int compare = Double.compare(startX, endX);
        if (compare != 0) {
            return compare;
        }
        compare = Double.compare(startY, endY);
        if (compare != 0) {
            return compare;
        }
        return Double.compare(startZ, endZ);
    }

    public static void renderBeam(BufferBuilder buffer, double startX, double startY, double startZ, double endX,
                                  double endY, double endZ, EnumFacing direction, double radius, GlowColor laserColor) {
        GlowRenderer.addCylinder(buffer, startX, startY, startZ, endX, endY, endZ, direction,
            radius * CORE_RADIUS_SCALE, laserColor, BEAM_ALPHA);
    }

}
