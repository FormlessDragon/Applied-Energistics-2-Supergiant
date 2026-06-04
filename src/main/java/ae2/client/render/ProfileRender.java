package ae2.client.render;

import ae2.items.tools.TickAnalyserItem;
import ae2.me.ticker.ProfileData;
import ae2.util.ColorData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class ProfileRender {
    public static final ProfileRender INSTANCE = new ProfileRender();
    private static final ColorData WHITE = new ColorData(1.0F, 1.0F, 1.0F);

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
        for (ProfileData.ATick tick : data.ticks) {
            if (tick.dimension() != mc.world.provider.getDimension() || !ProfileDataHandler.shouldRender(tick.rate())) {
                continue;
            }
            NetworkRender.INSTANCE.drawCube(0.8F, tick.color(), tick.pos());
        }
        GlStateManager.enableTexture2D();
        for (ProfileData.ATick tick : data.ticks) {
            if (tick.dimension() != mc.world.provider.getDimension() || !ProfileDataHandler.shouldRender(tick.rate())) {
                continue;
            }
            NetworkRender.INSTANCE.drawInWorldText((int) tick.rate() + "μs/t", WHITE,
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
