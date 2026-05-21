package appeng.client.render.overlay;

import appeng.hooks.CompassManager;
import appeng.items.misc.MeteoriteCompassItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

public final class MeteoriteCompassBeaconRenderer {
    private static final int SEGMENTS = 16;
    private static final double MAX_RENDER_LENGTH = 96.0D;
    private static final double OUTER_RADIUS = 0.07D;
    private static final double INNER_RADIUS = 0.03D;

    @SubscribeEvent
    public void renderWorldLast(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = minecraft.player;
        if (minecraft.world == null || player == null || !isHoldingBeaconCompass(player)) {
            return;
        }

        EnumHandSide handSide = getCompassHandSide(player);
        Vec3d start = getCompassAnchor(minecraft, player, event.getPartialTicks(), handSide);
        BlockPos meteoritePos = CompassManager.INSTANCE.getClosestMeteorite(player.getPosition(), true);
        if (meteoritePos == null) {
            return;
        }

        Vec3d end = new Vec3d(meteoritePos).add(0.5D, 0.5D, 0.5D);
        Vec3d delta = end.subtract(start);
        double fullLength = delta.length();
        if (fullLength < 1.0E-4D) {
            return;
        }

        Vec3d direction = delta.scale(1.0D / fullLength);
        double renderLength = Math.min(fullLength, MAX_RENDER_LENGTH);
        end = start.add(direction.scale(renderLength));

        double camX = minecraft.getRenderManager().viewerPosX;
        double camY = minecraft.getRenderManager().viewerPosY;
        double camZ = minecraft.getRenderManager().viewerPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-camX, -camY, -camZ);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.disableCull();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        renderCylinder(start, end, INNER_RADIUS, 196, 96, 255, 255);

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private static boolean isHoldingBeaconCompass(EntityPlayer player) {
        return MeteoriteCompassItem.hasBeacon(player.getHeldItemMainhand())
            || MeteoriteCompassItem.hasBeacon(player.getHeldItemOffhand());
    }

    private static EnumHandSide getCompassHandSide(EntityPlayer player) {
        if (MeteoriteCompassItem.hasBeacon(player.getHeldItemMainhand())) {
            return player.getPrimaryHand();
        }

        return player.getPrimaryHand() == EnumHandSide.RIGHT ? EnumHandSide.LEFT : EnumHandSide.RIGHT;
    }

    private static Vec3d getCompassAnchor(Minecraft minecraft, EntityPlayer player, float partialTicks,
                                          EnumHandSide handSide) {
        double sideSign = handSide == EnumHandSide.RIGHT ? -1.0D : 1.0D;
        if (minecraft.gameSettings.thirdPersonView == 0) {
            Vec3d eyePos = player.getPositionEyes(partialTicks);
            Vec3d look = player.getLook(partialTicks).normalize();
            Vec3d right = new Vec3d(0.0D, 1.0D, 0.0D).crossProduct(look).normalize();
            Vec3d up = look.crossProduct(right).normalize();

            return eyePos.add(look.scale(0.45D))
                .add(right.scale(0.28D * sideSign))
                .add(up.scale(-0.22D));
        }

        Vec3d playerPos = interpolatePlayerPos(player, partialTicks);
        float bodyYaw = player.prevRenderYawOffset + (player.renderYawOffset - player.prevRenderYawOffset) * partialTicks;
        double yawRad = Math.toRadians(-bodyYaw);
        Vec3d forward = new Vec3d(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
        Vec3d right = new Vec3d(forward.z, 0.0D, -forward.x);
        double height = player.isSneaking() ? player.height * 0.62D : player.height * 0.72D;

        return playerPos.add(0.0D, height, 0.0D)
            .add(right.scale(0.32D * sideSign))
            .add(forward.scale(0.14D));
    }

    private static Vec3d interpolatePlayerPos(EntityPlayer player, float partialTicks) {
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
        return new Vec3d(x, y, z);
    }

    private static void renderCylinder(Vec3d start, Vec3d end, double radius, int red, int green, int blue, int alpha) {
        Vec3d axis = end.subtract(start).normalize();
        Vec3d reference = Math.abs(axis.y) > 0.99D ? new Vec3d(1.0D, 0.0D, 0.0D) : new Vec3d(0.0D, 1.0D, 0.0D);
        Vec3d side = axis.crossProduct(reference).normalize();
        Vec3d up = axis.crossProduct(side).normalize();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_COLOR);

        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = Math.PI * 2.0D * i / SEGMENTS;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Vec3d offset = side.scale(cos * radius).add(up.scale(sin * radius));

            addVertex(buffer, start.add(offset), red, green, blue, alpha);
            addVertex(buffer, end.add(offset), red, green, blue, alpha);
        }

        tessellator.draw();
    }

    private static void addVertex(BufferBuilder buffer, Vec3d pos, int red, int green, int blue, int alpha) {
        buffer.pos(pos.x, pos.y, pos.z).color(red, green, blue, alpha).endVertex();
    }
}
