package ae2.client.render.overlay;

import ae2.api.implementations.items.MemoryCardColors;
import ae2.api.util.AEColor;
import ae2.core.definitions.AEItems;
import ae2.items.tools.advancedmemorycard.AdvancedMemoryCardP2PEntry;
import ae2.util.Platform;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class AdvancedMemoryCardHighlightHandler {
    public static final AdvancedMemoryCardHighlightHandler INSTANCE = new AdvancedMemoryCardHighlightHandler();
    private static final MemoryCardColors UNHIGHLIGHTED_COLORS = MemoryCardColors.DEFAULT;

    private final ObjectSet<BlockPos> currentDimensionPositions = new ObjectLinkedOpenHashSet<>();
    private int highlightedDimension = Integer.MIN_VALUE;
    private short highlightedFrequency;

    private AdvancedMemoryCardHighlightHandler() {
    }

    private static boolean isHoldingAdvancedMemoryCard(Minecraft minecraft) {
        return AEItems.ADVANCED_MEMORY_CARD.is(minecraft.player.getHeldItemMainhand())
            || AEItems.ADVANCED_MEMORY_CARD.is(minecraft.player.getHeldItemOffhand());
    }

    public void showFrequency(Minecraft minecraft, short frequency, List<AdvancedMemoryCardP2PEntry> entries) {
        if (minecraft.player == null || minecraft.world == null || frequency == 0) {
            clear();
            return;
        }

        int currentDimension = minecraft.world.provider.getDimension();
        this.highlightedDimension = currentDimension;
        this.highlightedFrequency = frequency;
        this.currentDimensionPositions.clear();

        for (AdvancedMemoryCardP2PEntry entry : entries) {
            if (entry.frequency() == frequency && entry.dimension() == currentDimension) {
                this.currentDimensionPositions.add(entry.pos());
            }
        }
    }

    public void clear() {
        this.currentDimensionPositions.clear();
        this.highlightedDimension = Integer.MIN_VALUE;
        this.highlightedFrequency = 0;
    }

    public MemoryCardColors getMemoryCardColors() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || this.highlightedFrequency == 0 || this.currentDimensionPositions.isEmpty()
            || minecraft.world.provider.getDimension() != this.highlightedDimension) {
            return UNHIGHLIGHTED_COLORS;
        }

        AEColor[] colors = Platform.p2p().toColors(this.highlightedFrequency);
        return MemoryCardColors.repeatedPairs(colors[0], colors[1], colors[2], colors[3]);
    }

    @SubscribeEvent
    public void renderWorldLast(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || minecraft.player == null) {
            clear();
            return;
        }
        if (this.currentDimensionPositions.isEmpty()) {
            return;
        }
        if (minecraft.world.provider.getDimension() != this.highlightedDimension || this.highlightedFrequency == 0) {
            clear();
            return;
        }
        if (!isHoldingAdvancedMemoryCard(minecraft)) {
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

        for (BlockPos pos : this.currentDimensionPositions) {
            AxisAlignedBB box = new AxisAlignedBB(pos).grow(0.01D);
            RenderGlobal.drawSelectionBoundingBox(box, 0.0F, 0.85F, 1.0F, 0.85F);
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
