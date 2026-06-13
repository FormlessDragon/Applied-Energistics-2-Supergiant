package ae2.client.render.bloom;

import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;

final class BloomFramebuffer {

    private final Framebuffer framebuffer;

    BloomFramebuffer(int width, int height, boolean depth) {
        this.framebuffer = new Framebuffer(Math.max(1, width), Math.max(1, height), depth);
        this.framebuffer.setFramebufferColor(0.0F, 0.0F, 0.0F, 0.0F);
        this.framebuffer.setFramebufferFilter(GL11.GL_LINEAR);
    }

    Framebuffer framebuffer() {
        return this.framebuffer;
    }

    void resize(int width, int height) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        if (this.framebuffer.framebufferWidth == width && this.framebuffer.framebufferHeight == height) {
            return;
        }
        this.framebuffer.createBindFramebuffer(width, height);
        this.framebuffer.setFramebufferColor(0.0F, 0.0F, 0.0F, 0.0F);
        this.framebuffer.setFramebufferFilter(GL11.GL_LINEAR);
    }

    void delete() {
        this.framebuffer.deleteFramebuffer();
    }
}
