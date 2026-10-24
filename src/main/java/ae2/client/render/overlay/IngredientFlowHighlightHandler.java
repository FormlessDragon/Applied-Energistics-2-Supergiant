package ae2.client.render.overlay;

import ae2.api.stacks.AEKey;
import ae2.api.util.FlowSearchDTO;
import ae2.client.gui.me.common.FlowRateFormatter;
import ae2.core.localization.PlayerMessages;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class IngredientFlowHighlightHandler {

    public static final IngredientFlowHighlightHandler INSTANCE = new IngredientFlowHighlightHandler();

    private static final long DURATION_MS = 20_000L;
    private static final long PULSE_PERIOD_MS = 1_200L;
    private static final double CUBE_GROW = 0.02D;
    private static final float EDGE_LINE_WIDTH = 2.0F;
    private static final float EDGE_ALPHA = 0.8F;
    private static final float FILL_ALPHA_MIN = 0.22F;
    private static final float FILL_ALPHA_MAX = 0.42F;
    private static final float ENTER_RED = 0.333F;
    private static final float ENTER_GREEN = 1.0F;
    private static final float ENTER_BLUE = 0.333F;
    private static final float LEAVE_RED = 1.0F;
    private static final float LEAVE_GREEN = 0.333F;
    private static final float LEAVE_BLUE = 0.333F;
    private static final int ENTER_TEXT_COLOR = 0x55FF55;
    private static final int LEAVE_TEXT_COLOR = 0xFF5555;
    private static final float LABEL_SCALE = 0.025F;
    /**
     * Beyond this distance the label grows with distance, so it keeps a readable size on screen.
     */
    private static final double LABEL_CONSTANT_SIZE_DISTANCE = 8.0D;
    private static final double LABEL_MAX_SCALE_FACTOR = 6.0D;

    private final ObjectArrayList<FlowHighlight> highlights = new ObjectArrayList<>();
    private long expiresAt;
    private int highlightedDimension = Integer.MIN_VALUE;

    private IngredientFlowHighlightHandler() {
    }

    /**
     * Draws the net total as a billboard at the cube's center, always facing the camera.
     */
    private static void renderLabel(Minecraft minecraft, RenderManager renderManager, FlowHighlight highlight) {
        BlockPos pos = highlight.pos();
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        double distance = Math.sqrt(renderManager.getDistanceToCamera(x, y, z));
        float scale = LABEL_SCALE * (float) Math.clamp(distance / LABEL_CONSTANT_SIZE_DISTANCE, 1.0D, LABEL_MAX_SCALE_FACTOR);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate((renderManager.options.thirdPersonView == 2 ? -1.0F : 1.0F)
                * renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-scale, -scale, scale);

        String text = highlight.label();
        int halfWidth = minecraft.fontRenderer.getStringWidth(text) / 2;
        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        GlStateManager.disableTexture2D();
        builder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        builder.pos(-halfWidth - 2, -6, 0).color(0, 0, 0, 140).endVertex();
        builder.pos(-halfWidth - 2, 5, 0).color(0, 0, 0, 140).endVertex();
        builder.pos(halfWidth + 2, 5, 0).color(0, 0, 0, 140).endVertex();
        builder.pos(halfWidth + 2, -6, 0).color(0, 0, 0, 140).endVertex();
        Tessellator.getInstance().draw();
        GlStateManager.enableTexture2D();

        minecraft.fontRenderer.drawString(text, -halfWidth, -4,
                highlight.isEntering() ? ENTER_TEXT_COLOR : LEAVE_TEXT_COLOR);
        GlStateManager.popMatrix();
    }

    private static void sendFlowMessage(Minecraft minecraft, ITextComponent message) {
        minecraft.player.sendStatusMessage(message.createCopy(), true);
        minecraft.player.sendMessage(message);
    }

    public void showFlowLocations(Minecraft minecraft, List<FlowSearchDTO> flows, @Nullable AEKey what,
                                  ITextComponent ingredientName) {
        clear();
        if (minecraft.player == null || minecraft.world == null) {
            return;
        }

        if (flows.isEmpty()) {
            minecraft.player.sendStatusMessage(
                PlayerMessages.IngredientFlowNotFound.text(ingredientName), true);
            return;
        }

        int currentDimension = minecraft.world.provider.getDimension();

        for (FlowSearchDTO flow : flows) {
            ITextComponent rate = FlowRateFormatter.formatTotalForChat(flow.netTotal());
            if (flow.dimension != currentDimension) {
                sendFlowMessage(minecraft, PlayerMessages.IngredientFlowInOtherDim.text(
                        ingredientName, rate, flow.dimension));
                continue;
            }
            BlockPos pos = flow.position;

            String label = what != null
                    ? FlowRateFormatter.formatSignedAmount(what, flow.netTotal())
                    : TextFormatting.getTextWithoutFormattingCodes(rate.getUnformattedText());
            this.highlights.add(new FlowHighlight(pos, flow.netTotal(), label));
            sendFlowMessage(minecraft, PlayerMessages.IngredientFlowHighlighted.text(
                ingredientName, rate, pos.getX(), pos.getY(), pos.getZ()));
        }

        this.highlightedDimension = currentDimension;
        this.expiresAt = System.currentTimeMillis() + DURATION_MS;
    }

    public void clear() {
        this.highlights.clear();
    }

    @SubscribeEvent
    public void renderWorldLast(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || minecraft.player == null) {
            clear();
            return;
        }
        if (this.highlights.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (minecraft.world.provider.getDimension() != this.highlightedDimension || now > this.expiresAt) {
            clear();
            return;
        }

        RenderManager renderManager = minecraft.getRenderManager();
        double camX = renderManager.viewerPosX;
        double camY = renderManager.viewerPosY;
        double camZ = renderManager.viewerPosZ;

        double pulse = 0.5D + 0.5D * Math.sin(now % PULSE_PERIOD_MS * (2.0D * Math.PI / PULSE_PERIOD_MS));
        float fillAlpha = (float) (FILL_ALPHA_MIN + (FILL_ALPHA_MAX - FILL_ALPHA_MIN) * pulse);

        GlStateManager.pushMatrix();
        GlStateManager.translate(-camX, -camY, -camZ);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(EDGE_LINE_WIDTH);

        for (FlowHighlight highlight : this.highlights) {
            AxisAlignedBB cube = new AxisAlignedBB(highlight.pos()).grow(CUBE_GROW);
            boolean entering = highlight.isEntering();
            float red = entering ? ENTER_RED : LEAVE_RED;
            float green = entering ? ENTER_GREEN : LEAVE_GREEN;
            float blue = entering ? ENTER_BLUE : LEAVE_BLUE;
            RenderGlobal.renderFilledBox(cube, red, green, blue, fillAlpha);
            RenderGlobal.drawSelectionBoundingBox(cube, red, green, blue, EDGE_ALPHA);
        }

        GlStateManager.glLineWidth(1.0F);
        GlStateManager.enableTexture2D();

        for (FlowHighlight highlight : this.highlights) {
            renderLabel(minecraft, renderManager, highlight);
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private record FlowHighlight(BlockPos pos, long netTotal, String label) {
        /**
         * Matches the chat message, which shows a zero net total as a green +0.
         */
        private boolean isEntering() {
            return this.netTotal >= 0;
        }
    }
}
