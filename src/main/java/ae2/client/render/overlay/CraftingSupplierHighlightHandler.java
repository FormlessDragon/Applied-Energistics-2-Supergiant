package ae2.client.render.overlay;

import ae2.core.localization.PlayerMessages;
import ae2.crafting.execution.CraftingSupplierLocation;
import ae2.crafting.execution.CraftingSupplierLocator;
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

public class CraftingSupplierHighlightHandler {
    public static final CraftingSupplierHighlightHandler INSTANCE = new CraftingSupplierHighlightHandler();
    private static final long DURATION_MS = 20_000L;

    private final List<OverlayHighlightLocation> currentDimensionLocations = new ObjectArrayList<>();
    private long expiresAt;
    private int highlightedDimension = Integer.MIN_VALUE;

    private CraftingSupplierHighlightHandler() {
    }

    public void showLocations(Minecraft minecraft, List<CraftingSupplierLocation> locations) {
        List<OverlayHighlightLocation> highlightLocations = new ObjectArrayList<>(locations.size());
        for (CraftingSupplierLocation location : locations) {
            highlightLocations.add(new OverlayHighlightLocation(
                location.dimensionId(),
                location.toBlockPos(),
                null,
                OverlayHighlightShape.WHOLE_BLOCK));
        }
        showHighlightLocations(minecraft, highlightLocations);
    }

    public void showHighlightLocations(Minecraft minecraft, List<OverlayHighlightLocation> locations) {
        if (minecraft.player == null || minecraft.world == null) {
            return;
        }

        this.currentDimensionLocations.clear();
        int currentDimension = minecraft.world.provider.getDimension();
        this.highlightedDimension = currentDimension;
        this.expiresAt = System.currentTimeMillis() + DURATION_MS;

        for (var location : locations) {
            BlockPos pos = location.pos();
            ITextComponent base = PlayerMessages.CraftingSupplierLocation.text(
                pos.getX(), pos.getY(), pos.getZ());
            if (location.dimensionId() == currentDimension) {
                minecraft.player.sendStatusMessage(base.createCopy(), true);
                minecraft.player.sendMessage(base);
                this.currentDimensionLocations.add(location);
            } else {
                String dimensionName = CraftingSupplierLocator.getDimensionName(location.dimensionId());
                ITextComponent otherDim = PlayerMessages.CraftingSupplierLocationInDimension.text(
                    pos.getX(), pos.getY(), pos.getZ(), location.dimensionId(), dimensionName);
                minecraft.player.sendStatusMessage(otherDim.createCopy(), true);
                minecraft.player.sendMessage(otherDim);
            }
        }
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
        if (minecraft.world.provider.getDimension() != this.highlightedDimension || System.currentTimeMillis() > this.expiresAt) {
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
        GlStateManager.glLineWidth(2.0F);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);

        for (var location : this.currentDimensionLocations) {
            for (AxisAlignedBB box : OverlayHighlightBoxes.create(location.shape(), location.pos(), location.side())) {
                RenderGlobal.drawSelectionBoundingBox(box.grow(0.01D), 1.0F, 0.0F, 0.0F, 0.85F);
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
