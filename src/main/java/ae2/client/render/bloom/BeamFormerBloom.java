package ae2.client.render.bloom;

import ae2.client.render.BeamFormerRenderer;
import ae2.client.render.GlowRenderer.GlowColor;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@SideOnly(Side.CLIENT)
public final class BeamFormerBloom {

    public static final BeamFormerBloom INSTANCE = new BeamFormerBloom();

    private static final String BLUR_FRAGMENT = """
        #version 120
        
        varying vec2 textureCoords;
        uniform sampler2D sourceTexture;
        uniform vec2 texelStep;
        
        void main() {
            vec4 color = texture2D(sourceTexture, textureCoords) * 0.227027;
            color += texture2D(sourceTexture, textureCoords + texelStep * 1.384615) * 0.316216;
            color += texture2D(sourceTexture, textureCoords - texelStep * 1.384615) * 0.316216;
            color += texture2D(sourceTexture, textureCoords + texelStep * 3.230769) * 0.070270;
            color += texture2D(sourceTexture, textureCoords - texelStep * 3.230769) * 0.070270;
            gl_FragColor = color;
        }
        """;
    private static final String ADD_FRAGMENT = """
        #version 120
        
        varying vec2 textureCoords;
        uniform sampler2D sourceTexture;
        uniform float intensity;
        
        void main() {
            vec4 color = texture2D(sourceTexture, textureCoords);
            gl_FragColor = vec4(color.rgb * intensity, color.a);
        }
        """;
    private static final int MAX_POOLED_COMMANDS = 256;
    private static final int INITIAL_COMMAND_CAPACITY = 32;
    private static final int DOWNSAMPLE = 4;
    private static final int BLUR_PASSES = 3;
    private static final float BLOOM_INTENSITY = 2.35F;
    private static final double BLOOM_SOURCE_RADIUS_SCALE = 1.15D;
    private static final List<BeamCommand> COMMANDS = new ObjectArrayList<>(INITIAL_COMMAND_CAPACITY);
    private static final Deque<BeamCommand> COMMAND_POOL = new ArrayDeque<>(MAX_POOLED_COMMANDS);

    private BloomFramebuffer highlight;
    private BloomFramebuffer blurA;
    private BloomFramebuffer blurB;
    private BloomShader blurShader;
    private BloomShader addShader;

    private BeamFormerBloom() {
    }

    public static void queue(double startX, double startY, double startZ, double endX, double endY, double endZ,
                             EnumFacing direction, double radius, GlowColor color) {
        if (compareEndpoints(startX, startY, startZ, endX, endY, endZ) > 0) {
            return;
        }
        BeamCommand command = COMMAND_POOL.pollFirst();
        if (command == null) {
            command = new BeamCommand();
        }
        command.set(startX, startY, startZ, endX, endY, endZ, direction, radius, color);
        COMMANDS.add(command);
    }

    private static void restoreMainFramebuffer(Minecraft minecraft) {
        minecraft.getFramebuffer().bindFramebuffer(true);
        GlStateManager.viewport(0, 0, minecraft.displayWidth, minecraft.displayHeight);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.bindTexture(0);
    }

    private static void recycleCommands() {
        for (BeamCommand command : COMMANDS) {
            command.clear();
            if (COMMAND_POOL.size() < MAX_POOLED_COMMANDS) {
                COMMAND_POOL.addFirst(command);
            }
        }
        COMMANDS.clear();
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

    private static void setLighting(boolean enabled) {
        if (enabled) {
            GlStateManager.enableLighting();
        } else {
            GlStateManager.disableLighting();
        }
    }

    private static void setFog(boolean enabled) {
        if (enabled) {
            GlStateManager.enableFog();
        } else {
            GlStateManager.disableFog();
        }
    }

    private static void setTexture2d(boolean enabled) {
        if (enabled) {
            GlStateManager.enableTexture2D();
        } else {
            GlStateManager.disableTexture2D();
        }
    }

    private static void setDepthTest(boolean enabled) {
        if (enabled) {
            GlStateManager.enableDepth();
        } else {
            GlStateManager.disableDepth();
        }
    }

    private static void setBlend(boolean enabled) {
        if (enabled) {
            GlStateManager.enableBlend();
        } else {
            GlStateManager.disableBlend();
        }
    }

    private static void setCull(boolean enabled) {
        if (enabled) {
            GlStateManager.enableCull();
        } else {
            GlStateManager.disableCull();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void renderWorldLast(RenderWorldLastEvent event) {
        if (COMMANDS.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null || minecraft.world == null) {
            recycleCommands();
            return;
        }

        RenderState state = RenderState.capture();
        try {
            renderMainBeams(minecraft);
            if (BloomShader.isSupported()) {
                try {
                    ensureResources(minecraft);
                    renderHighlight(minecraft);
                    blur(minecraft);
                    composite(minecraft);
                } catch (RuntimeException ignored) {
                    releaseResources();
                }
            }
        } catch (RuntimeException ignored) {
            releaseResources();
        } finally {
            recycleCommands();
            restoreMainFramebuffer(minecraft);
            state.restore();
        }
    }

    private void renderMainBeams(Minecraft minecraft) {
        minecraft.getFramebuffer().bindFramebuffer(true);
        GlStateManager.viewport(0, 0, minecraft.displayWidth, minecraft.displayHeight);

        double camX = minecraft.getRenderManager().viewerPosX;
        double camY = minecraft.getRenderManager().viewerPosY;
        double camZ = minecraft.getRenderManager().viewerPosZ;

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(-camX, -camY, -camZ);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableLighting();
            GlStateManager.disableFog();
            GlStateManager.disableTexture2D();
            GlStateManager.enableDepth();
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.depthMask(false);
            GlStateManager.disableBlend();
            GlStateManager.disableCull();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (BeamCommand command : COMMANDS) {
                BeamFormerRenderer.renderBeam(buffer, command.startX, command.startY, command.startZ, command.endX,
                    command.endY, command.endZ, command.direction, command.radius, command.color);
            }
            tessellator.draw();
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private void ensureResources(Minecraft minecraft) {
        Framebuffer main = minecraft.getFramebuffer();
        int width = main.framebufferWidth;
        int height = main.framebufferHeight;
        int lowWidth = Math.max(1, width / DOWNSAMPLE);
        int lowHeight = Math.max(1, height / DOWNSAMPLE);

        if (this.highlight == null) {
            this.highlight = new BloomFramebuffer(width, height, false);
            this.blurA = new BloomFramebuffer(lowWidth, lowHeight, false);
            this.blurB = new BloomFramebuffer(lowWidth, lowHeight, false);
            this.blurShader = new BloomShader(BLUR_FRAGMENT);
            this.addShader = new BloomShader(ADD_FRAGMENT);
            hookMainDepth(main);
            return;
        }

        this.highlight.resize(width, height);
        this.blurA.resize(lowWidth, lowHeight);
        this.blurB.resize(lowWidth, lowHeight);
        hookMainDepth(main);
    }

    private void hookMainDepth(Framebuffer main) {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, this.highlight.framebuffer().framebufferObject);
        OpenGlHelper.glFramebufferRenderbuffer(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_DEPTH_ATTACHMENT,
            OpenGlHelper.GL_RENDERBUFFER, main.depthBuffer);
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, main.framebufferObject);
    }

    private void renderHighlight(Minecraft minecraft) {
        this.highlight.framebuffer().bindFramebuffer(true);
        GlStateManager.viewport(0, 0, this.highlight.framebuffer().framebufferWidth,
            this.highlight.framebuffer().framebufferHeight);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        double camX = minecraft.getRenderManager().viewerPosX;
        double camY = minecraft.getRenderManager().viewerPosY;
        double camZ = minecraft.getRenderManager().viewerPosZ;

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(-camX, -camY, -camZ);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableLighting();
            GlStateManager.disableFog();
            GlStateManager.disableTexture2D();
            GlStateManager.enableDepth();
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.depthMask(false);
            GlStateManager.disableBlend();
            GlStateManager.disableCull();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (BeamCommand command : COMMANDS) {
                BeamFormerRenderer.renderBeam(buffer, command.startX, command.startY, command.startZ, command.endX,
                    command.endY, command.endZ, command.direction, command.radius * BLOOM_SOURCE_RADIUS_SCALE,
                    command.color);
            }
            tessellator.draw();
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private void blur(Minecraft minecraft) {
        this.highlight.framebuffer().bindFramebufferTexture();
        renderFullscreen(this.blurA.framebuffer(), this.blurShader, () -> {
            GL20.glUniform1i(this.blurShader.uniformLocation("sourceTexture"), 0);
            GL20.glUniform2f(this.blurShader.uniformLocation("texelStep"),
                1.0F / this.highlight.framebuffer().framebufferWidth, 0.0F);
        });

        for (int i = 0; i < BLUR_PASSES; i++) {
            this.blurA.framebuffer().bindFramebufferTexture();
            renderFullscreen(this.blurB.framebuffer(), this.blurShader, () -> {
                GL20.glUniform1i(this.blurShader.uniformLocation("sourceTexture"), 0);
                GL20.glUniform2f(this.blurShader.uniformLocation("texelStep"),
                    0.0F, 1.0F / this.blurA.framebuffer().framebufferHeight);
            });

            this.blurB.framebuffer().bindFramebufferTexture();
            renderFullscreen(this.blurA.framebuffer(), this.blurShader, () -> {
                GL20.glUniform1i(this.blurShader.uniformLocation("sourceTexture"), 0);
                GL20.glUniform2f(this.blurShader.uniformLocation("texelStep"),
                    1.0F / this.blurB.framebuffer().framebufferWidth, 0.0F);
            });
        }

        restoreMainFramebuffer(minecraft);
    }

    private void composite(Minecraft minecraft) {
        minecraft.getFramebuffer().bindFramebuffer(true);
        GlStateManager.viewport(0, 0, minecraft.displayWidth, minecraft.displayHeight);

        try {
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.disableLighting();
            GlStateManager.disableFog();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

            this.blurA.framebuffer().bindFramebufferTexture();
            this.addShader.use(() -> {
                GL20.glUniform1i(this.addShader.uniformLocation("sourceTexture"), 0);
                GL20.glUniform1f(this.addShader.uniformLocation("intensity"), BLOOM_INTENSITY);
            });
            BloomFullscreen.draw();
        } finally {
            this.addShader.release();
        }
    }

    private void renderFullscreen(Framebuffer target, BloomShader shader, Runnable uniforms) {
        target.framebufferClear();
        target.bindFramebuffer(true);
        GlStateManager.viewport(0, 0, target.framebufferWidth, target.framebufferHeight);

        try {
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.disableLighting();
            GlStateManager.disableFog();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.disableCull();
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            shader.use(uniforms);
            BloomFullscreen.draw();
        } finally {
            shader.release();
        }
    }

    private void releaseResources() {
        if (this.highlight != null) {
            this.highlight.delete();
        }
        if (this.blurA != null) {
            this.blurA.delete();
        }
        if (this.blurB != null) {
            this.blurB.delete();
        }
        if (this.blurShader != null) {
            this.blurShader.delete();
        }
        if (this.addShader != null) {
            this.addShader.delete();
        }
        this.highlight = null;
        this.blurA = null;
        this.blurB = null;
        this.blurShader = null;
        this.addShader = null;
    }

    private static final class BeamCommand {
        private double startX;
        private double startY;
        private double startZ;
        private double endX;
        private double endY;
        private double endZ;
        private EnumFacing direction;
        private double radius;
        private GlowColor color;

        private void set(double startX, double startY, double startZ, double endX, double endY, double endZ,
                         EnumFacing direction, double radius, GlowColor color) {
            this.startX = startX;
            this.startY = startY;
            this.startZ = startZ;
            this.endX = endX;
            this.endY = endY;
            this.endZ = endZ;
            this.direction = direction;
            this.radius = radius;
            this.color = color;
        }

        private void clear() {
            this.direction = null;
            this.color = null;
        }
    }

    private record RenderState(boolean lighting, boolean fog, boolean texture2d, boolean depthTest, boolean blend,
                               boolean cull, boolean depthMask, int depthFunc, int blendSrc, int blendDst,
                               int activeTexture, int texture0Binding, float lightmapX, float lightmapY) {
        static RenderState capture() {
            int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            int texture0Binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GlStateManager.setActiveTexture(activeTexture);
            return new RenderState(
                GL11.glIsEnabled(GL11.GL_LIGHTING),
                GL11.glIsEnabled(GL11.GL_FOG),
                GL11.glIsEnabled(GL11.GL_TEXTURE_2D),
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glIsEnabled(GL11.GL_CULL_FACE),
                GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                GL11.glGetInteger(GL11.GL_BLEND_SRC),
                GL11.glGetInteger(GL11.GL_BLEND_DST),
                activeTexture,
                texture0Binding,
                OpenGlHelper.lastBrightnessX,
                OpenGlHelper.lastBrightnessY
            );
        }

        void restore() {
            setLighting(this.lighting);
            setFog(this.fog);
            setTexture2d(this.texture2d);
            setDepthTest(this.depthTest);
            setBlend(this.blend);
            setCull(this.cull);
            GlStateManager.depthMask(this.depthMask);
            GlStateManager.depthFunc(this.depthFunc);
            GlStateManager.blendFunc(this.blendSrc, this.blendDst);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.bindTexture(this.texture0Binding);
            GlStateManager.setActiveTexture(this.activeTexture);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, this.lightmapX, this.lightmapY);
        }
    }
}
