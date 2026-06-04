package ae2.client.render;

import ae2.api.networking.pathing.ChannelMode;
import ae2.core.AEConfig;
import ae2.items.tools.NetworkAnalyserItem;
import ae2.me.AnalyserMode;
import ae2.me.NetworkData;
import ae2.me.netdata.LinkFlag;
import ae2.util.ClientUtil;
import ae2.util.ColorData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.EnumSet;
import java.util.Set;

public final class NetworkRender {
    public static final NetworkRender INSTANCE = new NetworkRender();
    private static final Set<AnalyserMode> RENDER_NODES = EnumSet.of(AnalyserMode.NODES, AnalyserMode.FULL,
        AnalyserMode.NONUM);
    private static final Set<AnalyserMode> RENDER_LINKS = EnumSet.of(AnalyserMode.CHANNELS, AnalyserMode.FULL,
        AnalyserMode.NONUM, AnalyserMode.P2P);
    private static final ColorData WHITE = new ColorData(1.0F, 1.0F, 1.0F);

    private NetworkRender() {
    }

    @SubscribeEvent
    public void renderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) {
            return;
        }
        ItemStack held = mc.player.getHeldItemMainhand();
        if (held.isEmpty() || !(held.getItem() instanceof NetworkAnalyserItem)) {
            return;
        }
        NetworkAnalyserItem.TargetPos target = NetworkAnalyserItem.getTarget(held);
        if (target == null || target.dimensionId() != mc.world.provider.getDimension()) {
            return;
        }
        NetworkDataHandler.updateConfig(NetworkAnalyserItem.getConfig(held));
        NetworkData data = NetworkDataHandler.pullData();
        if (data == null || data.isCorrupt()) {
            return;
        }

        double camX = mc.getRenderManager().viewerPosX;
        double camY = mc.getRenderManager().viewerPosY;
        double camZ = mc.getRenderManager().viewerPosZ;
        AnalyserMode mode = NetworkDataHandler.getMode();

        GlStateManager.pushMatrix();
        GlStateManager.translate(-camX, -camY, -camZ);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO);

        if (RENDER_LINKS.contains(mode)) {
            renderLinks(data, mode == AnalyserMode.P2P);
        }
        if (RENDER_NODES.contains(mode)) {
            renderNodes(data);
        }
        if (mode == AnalyserMode.FULL && AEConfig.instance().getChannelMode() != ChannelMode.INFINITE) {
            GlStateManager.enableTexture2D();
            renderChannelLabels(data);
            GlStateManager.disableTexture2D();
        }

        GlStateManager.glLineWidth(1.0F);
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void renderNodes(NetworkData data) {
        float size = NetworkDataHandler.getNodeSize();
        for (NetworkData.ANode node : data.nodes) {
            ColorData color = NetworkDataHandler.getColorByConfig(node.state().get());
            drawCube(size, color, node.pos());
        }
    }

    private void renderLinks(NetworkData data, boolean p2pOnly) {
        for (NetworkData.ALink link : data.links) {
            if (p2pOnly && link.state().get() != LinkFlag.COMPRESSED) {
                continue;
            }
            ColorData color = NetworkDataHandler.getColorByConfig(link.state().get());
            drawLink(link.state().get() == LinkFlag.DENSE, color, link.a().pos(), link.b().pos());
        }
    }

    private void renderChannelLabels(NetworkData data) {
        for (NetworkData.ALink link : data.links) {
            if (link.channel() <= 0) {
                continue;
            }
            Vec3d center = ClientUtil.getCenter(link.a().pos(), link.b().pos());
            drawInWorldText(String.valueOf(link.channel()), WHITE, center.x, center.y, center.z);
        }
    }

    public void drawCube(float size, ColorData color, BlockPos pos) {
        float half = size / 2.0F;
        Vec3d c = ClientUtil.getCenter(pos);
        double minX = c.x - half;
        double minY = c.y - half;
        double minZ = c.z - half;
        double maxX = c.x + half;
        double maxY = c.y + half;
        double maxZ = c.z + half;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        drawSide(
            new Vec3d(minX, maxY, maxZ),
            new Vec3d(maxX, maxY, maxZ),
            new Vec3d(minX, minY, maxZ),
            new Vec3d(maxX, minY, maxZ),
            color, buffer);
        drawSide(
            new Vec3d(maxX, maxY, minZ),
            new Vec3d(minX, maxY, minZ),
            new Vec3d(maxX, minY, minZ),
            new Vec3d(minX, minY, minZ),
            color, buffer);
        drawSide(
            new Vec3d(maxX, maxY, maxZ),
            new Vec3d(maxX, maxY, minZ),
            new Vec3d(maxX, minY, maxZ),
            new Vec3d(maxX, minY, minZ),
            color, buffer);
        drawSide(
            new Vec3d(minX, maxY, minZ),
            new Vec3d(minX, maxY, maxZ),
            new Vec3d(minX, minY, minZ),
            new Vec3d(minX, minY, maxZ),
            color, buffer);
        drawSide(
            new Vec3d(minX, maxY, minZ),
            new Vec3d(maxX, maxY, minZ),
            new Vec3d(minX, maxY, maxZ),
            new Vec3d(maxX, maxY, maxZ),
            color, buffer);
        drawSide(
            new Vec3d(minX, minY, maxZ),
            new Vec3d(maxX, minY, maxZ),
            new Vec3d(minX, minY, minZ),
            new Vec3d(maxX, minY, minZ),
            color, buffer);
        tessellator.draw();
    }

    public void drawLink(boolean dense, ColorData color, BlockPos from, BlockPos to) {
        Vec3d a = ClientUtil.getCenter(from);
        Vec3d b = ClientUtil.getCenter(to);
        double wide = dense ? 0.1D : 0.025D;
        Vec3d law = ClientUtil.getLawVec(a, b).scale(wide);
        Vec3d law2 = ClientUtil.getLawVec2(a, b).scale(wide);
        Vec3d topRight = a.add(law2);
        Vec3d bottomRight = a.subtract(law);
        Vec3d bottomLeft = a.subtract(law2);
        Vec3d topLeft = a.add(law);
        Vec3d topRight2 = b.add(law2);
        Vec3d bottomRight2 = b.subtract(law);
        Vec3d bottomLeft2 = b.subtract(law2);
        Vec3d topLeft2 = b.add(law);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        drawSide(topRight, topLeft, bottomRight, bottomLeft, color, buffer);
        drawSide(topRight2, topRight, bottomRight2, bottomRight, color, buffer);
        drawSide(topLeft2, topRight2, bottomLeft2, bottomRight2, color, buffer);
        drawSide(topLeft, topLeft2, bottomLeft, bottomLeft2, color, buffer);
        drawSide(topLeft2, topRight2, topLeft, topRight, color, buffer);
        drawSide(bottomLeft2, bottomRight2, bottomLeft, bottomRight, color, buffer);
        tessellator.draw();
    }

    private void drawSide(Vec3d tr, Vec3d tl, Vec3d br, Vec3d bl, ColorData color, BufferBuilder buffer) {
        buffer.pos(tr.x, tr.y, tr.z).color(color.red(), color.green(), color.blue(), color.alpha()).endVertex();
        buffer.pos(br.x, br.y, br.z).color(color.red(), color.green(), color.blue(), color.alpha()).endVertex();
        buffer.pos(bl.x, bl.y, bl.z).color(color.red(), color.green(), color.blue(), color.alpha()).endVertex();
        buffer.pos(tl.x, tl.y, tl.z).color(color.red(), color.green(), color.blue(), color.alpha()).endVertex();
    }

    public void drawInWorldText(String text, ColorData color, double x, double y, double z) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        float scale = 0.027F;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-scale, -scale, scale);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO);

        font.drawString(text, -font.getStringWidth(text) / 2.0F, 0.0F, color.toARGB(), false);

        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
