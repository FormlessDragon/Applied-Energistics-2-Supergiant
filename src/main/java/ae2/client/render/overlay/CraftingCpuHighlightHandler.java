package ae2.client.render.overlay;

import ae2.core.localization.PlayerMessages;
import ae2.crafting.execution.CraftingSupplierLocator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

public class CraftingCpuHighlightHandler {
    public static final CraftingCpuHighlightHandler INSTANCE = new CraftingCpuHighlightHandler();
    private static final long DURATION_MS = 20_000L;
    private static final float HIGHLIGHT_LINE_WIDTH = 3.0F;
    private static final double HIGHLIGHT_GROW = 0.03D;
    private static final float HIGHLIGHT_RED = 0.35F;
    private static final float HIGHLIGHT_GREEN = 0.8F;
    private static final float HIGHLIGHT_BLUE = 1.0F;
    private static final float HIGHLIGHT_ALPHA = 0.95F;

    private int highlightedDimension = Integer.MIN_VALUE;
    private long expiresAt;
    private AxisAlignedBB currentDimensionBox;

    private CraftingCpuHighlightHandler() {
    }

    public void showCpu(Minecraft minecraft, int dimensionId, BlockPos corePos, BlockPos boundsMin, BlockPos boundsMax) {
        if (minecraft.player == null || minecraft.world == null) {
            clear();
            return;
        }

        int currentDimension = minecraft.world.provider.getDimension();
        this.highlightedDimension = currentDimension;
        this.expiresAt = System.currentTimeMillis() + DURATION_MS;
        this.currentDimensionBox = null;

        ITextComponent message;
        if (dimensionId == currentDimension) {
            message = PlayerMessages.CraftingCpuCoreLocation.text(corePos.getX(), corePos.getY(), corePos.getZ());
            this.currentDimensionBox = createStructureBox(boundsMin, boundsMax);
        } else {
            String dimensionName = CraftingSupplierLocator.getDimensionName(dimensionId);
            message = PlayerMessages.CraftingCpuCoreLocationInDimension.text(
                corePos.getX(),
                corePos.getY(),
                corePos.getZ(),
                dimensionId,
                dimensionName);
        }

        minecraft.player.sendStatusMessage(message.createCopy(), true);
        minecraft.player.sendMessage(message);
    }

    public void clear() {
        this.highlightedDimension = Integer.MIN_VALUE;
        this.expiresAt = 0L;
        this.currentDimensionBox = null;
    }

    @SubscribeEvent
    public void renderWorldLast(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || minecraft.player == null) {
            clear();
            return;
        }
        if (this.currentDimensionBox == null) {
            return;
        }
        if (minecraft.world.provider.getDimension() != this.highlightedDimension || System.currentTimeMillis() > this.expiresAt) {
            clear();
            return;
        }
        if (((System.currentTimeMillis() / 300L) & 1L) == 1L) {
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

        RenderGlobal.drawSelectionBoundingBox(this.currentDimensionBox.grow(HIGHLIGHT_GROW),
            HIGHLIGHT_RED,
            HIGHLIGHT_GREEN,
            HIGHLIGHT_BLUE,
            HIGHLIGHT_ALPHA);

        GlStateManager.glLineWidth(1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static AxisAlignedBB createStructureBox(BlockPos boundsMin, BlockPos boundsMax) {
        return new AxisAlignedBB(
            boundsMin.getX(),
            boundsMin.getY(),
            boundsMin.getZ(),
            boundsMax.getX() + 1,
            boundsMax.getY() + 1,
            boundsMax.getZ() + 1);
    }
}
