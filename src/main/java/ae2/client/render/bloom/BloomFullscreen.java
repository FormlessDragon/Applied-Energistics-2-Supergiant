package ae2.client.render.bloom;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

final class BloomFullscreen {

    private BloomFullscreen() {
    }

    static void draw() {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(-1.0D, 1.0D, 0.0D).tex(0.0D, 0.0D).endVertex();
        buffer.pos(-1.0D, -1.0D, 0.0D).tex(0.0D, 1.0D).endVertex();
        buffer.pos(1.0D, -1.0D, 0.0D).tex(1.0D, 1.0D).endVertex();
        buffer.pos(1.0D, 1.0D, 0.0D).tex(1.0D, 0.0D).endVertex();
        tessellator.draw();
    }
}
