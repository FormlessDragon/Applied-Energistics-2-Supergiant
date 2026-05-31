package ae2.client.render.tesr;

import ae2.tile.storage.TileSkyStoneTank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.opengl.GL11;

public final class SkyStoneTankBlockEntityRenderer extends TileEntitySpecialRenderer<TileSkyStoneTank> {
    private static final float TANK_W = 1 / 16.0F + 0.001F;

    public static void drawFluidInTank(FluidStack fluid, float fill, double x, double y, double z) {
        Fluid fluidType = fluid.getFluid();
        TextureAtlasSprite sprite = Minecraft.getMinecraft()
                                             .getTextureMapBlocks()
                                             .getAtlasSprite(fluidType.getStill(fluid).toString());
        int color = fluidType.getColor(fluid);
        float r = ((color >> 16) & 255) / 256.0F;
        float g = ((color >> 8) & 255) / 256.0F;
        float b = (color & 255) / 256.0F;

        float fillY = TANK_W + Math.clamp(fill, 0.0F, 1.0F) * (1.0F - 2.0F * TANK_W);
        float bottomHeight = TANK_W;
        float topHeight = fillY;
        if (fluidType.isGaseous(fluid)) {
            topHeight = 1.0F - TANK_W;
            bottomHeight = 1.0F - fillY;
        }

        Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        float lastLightX = OpenGlHelper.lastBrightnessX;
        float lastLightY = OpenGlHelper.lastBrightnessY;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);

        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        renderCube(buffer, sprite, bottomHeight, topHeight, r, g, b);
        Tessellator.getInstance().draw();

        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastLightX, lastLightY);
    }

    private static void renderCube(BufferBuilder buffer, TextureAtlasSprite sprite, float y1,
                                   float y2, float r, float g, float b) {
        float u1 = sprite.getMinU();
        float u2 = sprite.getMaxU();
        float v1 = sprite.getMinV();
        float v2 = sprite.getMaxV();

        vertex(buffer, (float) 0.9365, y1, SkyStoneTankBlockEntityRenderer.TANK_W, u2, v2, r, g, b);
        vertex(buffer, (float) 0.9365, y1, (float) 0.9365, u1, v2, r, g, b);
        vertex(buffer, SkyStoneTankBlockEntityRenderer.TANK_W, y1, (float) 0.9365, u1, v1, r, g, b);
        vertex(buffer, SkyStoneTankBlockEntityRenderer.TANK_W, y1, SkyStoneTankBlockEntityRenderer.TANK_W, u2, v1, r, g, b);

        vertex(buffer, SkyStoneTankBlockEntityRenderer.TANK_W, y2, SkyStoneTankBlockEntityRenderer.TANK_W, u1, v1, r, g, b);
        vertex(buffer, SkyStoneTankBlockEntityRenderer.TANK_W, y2, (float) 0.9365, u1, v2, r, g, b);
        vertex(buffer, (float) 0.9365, y2, (float) 0.9365, u2, v2, r, g, b);
        vertex(buffer, (float) 0.9365, y2, SkyStoneTankBlockEntityRenderer.TANK_W, u2, v1, r, g, b);

        vertex(buffer, (float) 0.9365, y2, SkyStoneTankBlockEntityRenderer.TANK_W, u2, v1, r, g, b);
        vertex(buffer, (float) 0.9365, y1, SkyStoneTankBlockEntityRenderer.TANK_W, u2, v2, r, g, b);
        vertex(buffer, SkyStoneTankBlockEntityRenderer.TANK_W, y1, SkyStoneTankBlockEntityRenderer.TANK_W, u1, v2, r, g, b);
        vertex(buffer, SkyStoneTankBlockEntityRenderer.TANK_W, y2, SkyStoneTankBlockEntityRenderer.TANK_W, u1, v1, r, g, b);

        vertex(buffer, SkyStoneTankBlockEntityRenderer.TANK_W, y2, (float) 0.9365, u1, v1, r, g, b);
        vertex(buffer, SkyStoneTankBlockEntityRenderer.TANK_W, y1, (float) 0.9365, u1, v2, r, g, b);
        vertex(buffer, (float) 0.9365, y1, (float) 0.9365, u2, v2, r, g, b);
        vertex(buffer, (float) 0.9365, y2, (float) 0.9365, u2, v1, r, g, b);

        vertex(buffer, SkyStoneTankBlockEntityRenderer.TANK_W, y2, SkyStoneTankBlockEntityRenderer.TANK_W, u1, v1, r, g, b);
        vertex(buffer, SkyStoneTankBlockEntityRenderer.TANK_W, y1, SkyStoneTankBlockEntityRenderer.TANK_W, u1, v2, r, g, b);
        vertex(buffer, SkyStoneTankBlockEntityRenderer.TANK_W, y1, (float) 0.9365, u2, v2, r, g, b);
        vertex(buffer, SkyStoneTankBlockEntityRenderer.TANK_W, y2, (float) 0.9365, u2, v1, r, g, b);

        vertex(buffer, (float) 0.9365, y2, (float) 0.9365, u2, v1, r, g, b);
        vertex(buffer, (float) 0.9365, y1, (float) 0.9365, u2, v2, r, g, b);
        vertex(buffer, (float) 0.9365, y1, SkyStoneTankBlockEntityRenderer.TANK_W, u1, v2, r, g, b);
        vertex(buffer, (float) 0.9365, y2, SkyStoneTankBlockEntityRenderer.TANK_W, u1, v1, r, g, b);
    }

    private static void vertex(BufferBuilder buffer, float x, float y, float z, float u, float v, float r, float g,
                               float b) {
        buffer.pos(x, y, z).tex(u, v).color(r, g, b, 1.0F).endVertex();
    }

    @Override
    public void render(TileSkyStoneTank tank, double x, double y, double z, float partialTicks, int destroyStage,
                       float alpha) {
        FluidStack fluid = tank.getTank().getFluid();
        if (fluid == null || fluid.amount <= 0) {
            return;
        }

        float fill = (float) fluid.amount / tank.getTank().getCapacity();
        drawFluidInTank(fluid, fill, x, y, z);
    }
}
