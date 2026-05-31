package ae2.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.client.IRenderHandler;
import org.lwjgl.opengl.GL11;

import java.util.Random;

public class SpatialSkyRender extends IRenderHandler {

    private static final SpatialSkyRender INSTANCE = new SpatialSkyRender();

    private final Random random = new Random();
    private final int dspList;
    private long cycle = 0;

    public SpatialSkyRender() {
        this.dspList = GLAllocation.generateDisplayLists(1);
    }

    public static IRenderHandler getInstance() {
        return INSTANCE;
    }

    @Override
    public void render(float partialTicks, WorldClient world, Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - this.cycle > 2000) {
            this.cycle = now;
            GlStateManager.glNewList(this.dspList, GL11.GL_COMPILE);
            this.renderTwinkles();
            GlStateManager.glEndList();
        }

        float fade = now - this.cycle;
        fade /= 1000;
        fade = 0.15f * (1.0f - Math.abs((fade - 1.0f) * (fade - 1.0f)));

        GlStateManager.disableFog();
        GlStateManager.disableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.depthMask(false);
        GlStateManager.color(0.0f, 0.0f, 0.0f, 1.0f);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        for (int i = 0; i < 6; ++i) {
            GlStateManager.pushMatrix();
            if (i == 1) {
                GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
            }
            if (i == 2) {
                GlStateManager.rotate(-90.0F, 1.0F, 0.0F, 0.0F);
            }
            if (i == 3) {
                GlStateManager.rotate(180.0F, 1.0F, 0.0F, 0.0F);
            }
            if (i == 4) {
                GlStateManager.rotate(90.0F, 0.0F, 0.0F, 1.0F);
            }
            if (i == 5) {
                GlStateManager.rotate(-90.0F, 0.0F, 0.0F, 1.0F);
            }

            GlStateManager.disableTexture2D();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
            buffer.pos(-100.0D, -100.0D, -100.0D).endVertex();
            buffer.pos(-100.0D, -100.0D, 100.0D).endVertex();
            buffer.pos(100.0D, -100.0D, 100.0D).endVertex();
            buffer.pos(100.0D, -100.0D, -100.0D).endVertex();
            tessellator.draw();
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
        }

        GlStateManager.depthMask(true);

        if (fade > 0.0f) {
            GlStateManager.disableFog();
            GlStateManager.disableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.disableTexture2D();
            GlStateManager.depthMask(false);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.color(fade, fade, fade, 1.0f);
            GlStateManager.callList(this.dspList);
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.enableFog();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void renderTwinkles() {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);

        for (int i = 0; i < 50; ++i) {
            double iX = this.random.nextFloat() * 2.0F - 1.0F;
            double iY = this.random.nextFloat() * 2.0F - 1.0F;
            double iZ = this.random.nextFloat() * 2.0F - 1.0F;
            double d3 = 0.05F + this.random.nextFloat() * 0.1F;
            double dist = iX * iX + iY * iY + iZ * iZ;

            if (dist < 1.0D && dist > 0.01D) {
                dist = 1.0D / Math.sqrt(dist);
                iX *= dist;
                iY *= dist;
                iZ *= dist;
                double x = iX * 100.0D;
                double y = iY * 100.0D;
                double z = iZ * 100.0D;
                double d8 = Math.atan2(iX, iZ);
                double d9 = Math.sin(d8);
                double d10 = Math.cos(d8);
                double d11 = Math.atan2(Math.sqrt(iX * iX + iZ * iZ), iY);
                double d12 = Math.sin(d11);
                double d13 = Math.cos(d11);
                double d14 = this.random.nextDouble() * Math.PI * 2.0D;
                double d15 = Math.sin(d14);
                double d16 = Math.cos(d14);

                for (int j = 0; j < 4; ++j) {
                    double d17 = 0.0D;
                    double d18 = ((j & 2) - 1) * d3;
                    double d19 = ((j + 1 & 2) - 1) * d3;
                    double d20 = d18 * d16 - d19 * d15;
                    double d21 = d19 * d16 + d18 * d15;
                    double d22 = d20 * d12 + d17 * d13;
                    double d23 = d17 * d12 - d20 * d13;
                    double d24 = d23 * d9 - d21 * d10;
                    double d25 = d21 * d9 + d23 * d10;
                    buffer.pos(x + d24, y + d22, z + d25).endVertex();
                }
            }
        }

        tessellator.draw();
    }
}
