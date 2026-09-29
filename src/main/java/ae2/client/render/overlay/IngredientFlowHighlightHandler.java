package ae2.client.render.overlay;

import ae2.api.util.FlowSearchDTO;
import ae2.client.gui.me.common.FlowRateFormatter;
import ae2.core.localization.PlayerMessages;
import ae2.crafting.execution.CraftingSupplierLocator;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Map;

public class IngredientFlowHighlightHandler {

    public static final IngredientFlowHighlightHandler INSTANCE = new IngredientFlowHighlightHandler();

    private static final long DURATION_MS = 20_000L;
    private static final float HIGHLIGHT_LINE_WIDTH = 3.0F;
    private static final double HIGHLIGHT_GROW = 0.03D;
    private static final float HIGHLIGHT_ENTER_RED = 0.333F;
    private static final float HIGHLIGHT_ENTER_GREEN = 1.0F;
    private static final float HIGHLIGHT_ENTER_BLUE = 0.333F;
    private static final float HIGHLIGHT_LEAVE_RED = 1.0F;
    private static final float HIGHLIGHT_LEAVE_GREEN = 0.333F;
    private static final float HIGHLIGHT_LEAVE_BLUE = 0.333F;
    private static final float HIGHLIGHT_ALPHA = 0.95F;

    private final ObjectArrayList<OverlayHighlightLocation> currentDimensionLocations = new ObjectArrayList<>();
    private final Map<OverlayHighlightLocation, Long> highlightedNetTotals = new Object2LongOpenHashMap<>();
    private long expiresAt;
    private int highlightedDimension = Integer.MIN_VALUE;

    private IngredientFlowHighlightHandler() {
    }

    public void showFlowLocations(Minecraft minecraft, List<FlowSearchDTO> flows, ITextComponent ingredientName) {
        if (minecraft.player == null || minecraft.world == null) {
            return;
        }

        if (flows.isEmpty()) {
            minecraft.player.sendStatusMessage(
                PlayerMessages.IngredientFlowNotFound.text(ingredientName), true);
            return;
        }

        List<OverlayHighlightLocation> locations = new ObjectArrayList<>(flows.size());
        this.highlightedNetTotals.clear();
        int currentDimension = minecraft.world.provider.getDimension();

        for (FlowSearchDTO flow : flows) {
            BlockPos pos = flow.location.getPos();
            int dimension = flow.location.getLevel().provider.getDimension();
            ITextComponent rate = FlowRateFormatter.formatTotalForChat(flow.netTotal());

            if (dimension == currentDimension) {
                OverlayHighlightLocation location =
                    new OverlayHighlightLocation(dimension, pos, null, OverlayHighlightShape.WHOLE_BLOCK);
                locations.add(location);
                this.highlightedNetTotals.put(location, flow.netTotal());
                sendFlowMessage(minecraft, PlayerMessages.IngredientFlowHighlighted.text(
                    ingredientName, rate, pos.getX(), pos.getY(), pos.getZ()));
            } else {
                sendFlowMessage(minecraft, PlayerMessages.IngredientFlowInOtherDim.text(
                    ingredientName, rate, CraftingSupplierLocator.getDimensionName(dimension)));
            }
        }

        showLocations(minecraft, locations);
    }

    private static void sendFlowMessage(Minecraft minecraft, ITextComponent message) {
        minecraft.player.sendStatusMessage(message.createCopy(), true);
        minecraft.player.sendMessage(message);
    }

    public void showLocations(Minecraft minecraft, List<OverlayHighlightLocation> locations) {
        if (minecraft.player == null || minecraft.world == null) {
            return;
        }

        this.currentDimensionLocations.clear();
        this.currentDimensionLocations.ensureCapacity(locations.size());
        this.highlightedDimension = minecraft.world.provider.getDimension();
        this.expiresAt = System.currentTimeMillis() + DURATION_MS;

        for (var location : locations) {
            if (location.dimensionId() == this.highlightedDimension) {
                this.currentDimensionLocations.add(location);
            }
        }
    }

    public void clear() {
        this.currentDimensionLocations.clear();
    }

    @SubscribeEvent
    public void renderWorldLast(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || minecraft.player == null) {
            this.currentDimensionLocations.clear();
            return;
        }
        if (this.currentDimensionLocations.isEmpty()) {
            return;
        }
        if (minecraft.world.provider.getDimension() != this.highlightedDimension
            || System.currentTimeMillis() > this.expiresAt) {
            this.currentDimensionLocations.clear();
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

        for (var location : this.currentDimensionLocations) {
            boolean entering = this.highlightedNetTotals.getOrDefault(location, 0L) > 0;
            for (AxisAlignedBB box : OverlayHighlightBoxes.create(location.shape(), location.pos(), location.side())) {
                RenderGlobal.drawSelectionBoundingBox(box.grow(HIGHLIGHT_GROW),
                    entering ? HIGHLIGHT_ENTER_RED : HIGHLIGHT_LEAVE_RED,
                    entering ? HIGHLIGHT_ENTER_GREEN : HIGHLIGHT_LEAVE_GREEN,
                    entering ? HIGHLIGHT_ENTER_BLUE : HIGHLIGHT_LEAVE_BLUE,
                    HIGHLIGHT_ALPHA);
            }
        }

        GlStateManager.glLineWidth(1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
