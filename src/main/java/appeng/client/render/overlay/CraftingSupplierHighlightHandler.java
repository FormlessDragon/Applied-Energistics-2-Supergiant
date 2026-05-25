package appeng.client.render.overlay;

import appeng.crafting.execution.CraftingSupplierLocation;
import appeng.crafting.execution.CraftingSupplierLocator;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class CraftingSupplierHighlightHandler {
    public static final CraftingSupplierHighlightHandler INSTANCE = new CraftingSupplierHighlightHandler();
    private static final long DURATION_MS = 20_000L;

    private final List<CraftingSupplierLocation> currentDimensionLocations = new ObjectArrayList<>();
    private long expiresAt;
    private int highlightedDimension = Integer.MIN_VALUE;

    private CraftingSupplierHighlightHandler() {
    }

    public void showLocations(Minecraft minecraft, List<CraftingSupplierLocation> locations) {
        if (minecraft.player == null || minecraft.world == null) {
            return;
        }

        this.currentDimensionLocations.clear();
        int currentDimension = minecraft.world.provider.getDimension();
        this.highlightedDimension = currentDimension;
        this.expiresAt = System.currentTimeMillis() + DURATION_MS;

        for (var location : locations) {
            String base = "供应器位置: " + location.x() + " " + location.y() + " " + location.z();
            if (location.dimensionId() == currentDimension) {
                minecraft.player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(base), true);
                minecraft.player.sendMessage(new net.minecraft.util.text.TextComponentString(base));
                this.currentDimensionLocations.add(location);
            } else {
                String dimensionName = CraftingSupplierLocator.getDimensionName(location.dimensionId());
                String otherDim = "供应器位置: " + location.x() + " " + location.y() + " " + location.z()
                    + " (维度 " + location.dimensionId() + " - " + dimensionName + ")";
                minecraft.player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(otherDim), true);
                minecraft.player.sendMessage(new net.minecraft.util.text.TextComponentString(otherDim));
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
            AxisAlignedBB box = new AxisAlignedBB(location.toBlockPos()).grow(0.01D);
            RenderGlobal.drawSelectionBoundingBox(box, 1.0F, 0.0F, 0.0F, 0.85F);
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
