package ae2.client.render;

import ae2.core.localization.GuiText;
import ae2.items.tools.TickAnalyserItem;
import ae2.me.ticker.ProfileData;
import ae2.util.ColorData;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

public final class ProfileRender {
    public static final ProfileRender INSTANCE = new ProfileRender();
    private static final ColorData WHITE = new ColorData(1.0F, 1.0F, 1.0F);
    private final ObjectArrayList<ProfileData.ATick> visibleTicks = new ObjectArrayList<>();

    private ProfileRender() {
    }

    @SubscribeEvent
    public void renderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) {
            return;
        }
        ItemStack held = mc.player.getHeldItemMainhand();
        if (held.isEmpty() || !(held.getItem() instanceof TickAnalyserItem)) {
            return;
        }
        ProfileDataHandler.updateConfig(TickAnalyserItem.getConfig(held));
        ProfileData data = ProfileDataHandler.pullData();
        if (data == null || data.isCorrupt() || data.ticks.length == 0) {
            return;
        }
        double camX = mc.getRenderManager().viewerPosX;
        double camY = mc.getRenderManager().viewerPosY;
        double camZ = mc.getRenderManager().viewerPosZ;
        GlStateManager.pushMatrix();
        GlStateManager.translate(-camX, -camY, -camZ);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();

        int dimension = mc.world.provider.getDimension();
        visibleTicks.clear();
        for (ProfileData.ATick tick : data.ticks) {
            if (tick.dimension() != dimension || !ProfileDataHandler.shouldRender(tick.rate())) {
                continue;
            }
            visibleTicks.add(tick);
        }
        if (visibleTicks.isEmpty()) {
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
            return;
        }

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (ProfileData.ATick tick : visibleTicks) {
            NetworkRender.INSTANCE.appendCube(buffer, 0.8F, tick.color(), tick.pos());
        }
        tessellator.draw();
        GlStateManager.enableTexture2D();
        for (ProfileData.ATick tick : visibleTicks) {
            NetworkRender.INSTANCE.drawInWorldText(GuiText.TickAnalyserRate.getLocal((int) tick.rate()), WHITE,
                tick.pos().getX() + 0.5D, tick.pos().getY() + 0.5D, tick.pos().getZ() + 0.5D);
        }
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
