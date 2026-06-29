package ae2.client.render.overlay;

import ae2.core.definitions.AEItems;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

public class PriorityTunerHighlightHandler {
    public static final PriorityTunerHighlightHandler INSTANCE = new PriorityTunerHighlightHandler();
    private static final float HIGHLIGHT_LINE_WIDTH = 3.0F;
    private static final double HIGHLIGHT_GROW = 0.03D;
    private static final float HIGHLIGHT_RED = 0.35F;
    private static final float HIGHLIGHT_GREEN = 0.8F;
    private static final float HIGHLIGHT_BLUE = 1.0F;
    private static final float HIGHLIGHT_ALPHA = 0.95F;

    private final ObjectArrayList<PendingPriority> currentDimensionEntries = new ObjectArrayList<>();
    private int highlightedDimension = Integer.MIN_VALUE;

    private PriorityTunerHighlightHandler() {
    }

    private static boolean isHoldingPriorityTuner(Minecraft minecraft) {
        return AEItems.PRIORITY_TUNER.is(minecraft.player.getHeldItemMainhand())
            || AEItems.PRIORITY_TUNER.is(minecraft.player.getHeldItemOffhand());
    }

    public void add(int dimensionId, BlockPos pos, @Nullable EnumFacing side, int priority) {
        if (dimensionId != this.highlightedDimension) {
            this.currentDimensionEntries.clear();
            this.highlightedDimension = dimensionId;
        }
        this.currentDimensionEntries.add(new PendingPriority(pos, side, priority));
    }

    public void clear() {
        this.currentDimensionEntries.clear();
        this.highlightedDimension = Integer.MIN_VALUE;
    }

    @SubscribeEvent
    public void renderWorldLast(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || minecraft.player == null) {
            clear();
            return;
        }
        if (this.currentDimensionEntries.isEmpty()) {
            return;
        }
        if (minecraft.world.provider.getDimension() != this.highlightedDimension || !isHoldingPriorityTuner(minecraft)) {
            clear();
            return;
        }

        double camX = minecraft.getRenderManager().viewerPosX;
        double camY = minecraft.getRenderManager().viewerPosY;
        double camZ = minecraft.getRenderManager().viewerPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-camX, -camY, -camZ);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO);
        GlStateManager.glLineWidth(HIGHLIGHT_LINE_WIDTH);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);

        for (PendingPriority entry : this.currentDimensionEntries) {
            for (AxisAlignedBB box : OverlayHighlightBoxes.create(OverlayHighlightShape.WHOLE_BLOCK, entry.pos(),
                entry.side())) {
                RenderGlobal.drawSelectionBoundingBox(box.grow(HIGHLIGHT_GROW),
                    HIGHLIGHT_RED, HIGHLIGHT_GREEN, HIGHLIGHT_BLUE, HIGHLIGHT_ALPHA);
            }
        }

        GlStateManager.glLineWidth(1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.enableTexture2D();

        for (PendingPriority entry : this.currentDimensionEntries) {
            renderPriorityText(minecraft, entry);
        }

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static void renderPriorityText(Minecraft minecraft, PendingPriority entry) {
        String text = Integer.toString(entry.priority());
        BlockPos pos = entry.pos();
        GlStateManager.pushMatrix();
        GlStateManager.translate(pos.getX() + 0.5D, pos.getY() + 1.18D, pos.getZ() + 0.5D);
        GlStateManager.rotate(-minecraft.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(minecraft.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-0.025F, -0.025F, 0.025F);
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();

        int width = minecraft.fontRenderer.getStringWidth(text) / 2;
        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        GlStateManager.disableTexture2D();
        builder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        builder.pos(-width - 2, -2, 0).color(0, 0, 0, 120).endVertex();
        builder.pos(-width - 2, 9, 0).color(0, 0, 0, 120).endVertex();
        builder.pos(width + 2, 9, 0).color(0, 0, 0, 120).endVertex();
        builder.pos(width + 2, -2, 0).color(0, 0, 0, 120).endVertex();
        Tessellator.getInstance().draw();
        GlStateManager.enableTexture2D();

        minecraft.fontRenderer.drawString(text, -width, 0, 0xFFFFFF);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private record PendingPriority(BlockPos pos, @Nullable EnumFacing side, int priority) {
    }
}
