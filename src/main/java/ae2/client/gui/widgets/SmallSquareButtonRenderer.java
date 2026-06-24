package ae2.client.gui.widgets;

import ae2.client.gui.Icon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;

/**
 * Shared renderer for compact square icon buttons used in list rows and headers.
 */
public final class SmallSquareButtonRenderer {
    private static final int SOURCE_SIZE = 16;
    private static final int BACKGROUND_Z_OFFSET = 10;
    private static final int CONTENT_Z_OFFSET = 20;

    private SmallSquareButtonRenderer() {
    }

    public static void drawBackground(int x, int y, int width, int height, boolean hovered) {
        Icon backgroundIcon = hovered
            ? Icon.SMALL_SQUARE_BUTTON_BACKGROUND_HOVER
            : Icon.SMALL_SQUARE_BUTTON_BACKGROUND;
        drawScaledIcon(
            x,
            y,
            backgroundIcon,
            Math.min(width / (float) SOURCE_SIZE, height / (float) SOURCE_SIZE),
            BACKGROUND_Z_OFFSET);
    }

    public static void drawIcon(int x, int y, int width, int height, Icon icon, int inset) {
        int contentWidth = Math.max(1, width - inset * 2);
        int contentHeight = Math.max(1, height - inset * 2);
        float scale = Math.min(contentWidth / (float) icon.width, contentHeight / (float) icon.height);
        int drawWidth = scaledDimension(icon.width, scale);
        int drawHeight = scaledDimension(icon.height, scale);
        drawScaledIcon(
            x + (width - drawWidth) / 2,
            y + (height - drawHeight) / 2,
            icon,
            scale,
            CONTENT_Z_OFFSET);
    }

    public static void drawItemStack(int x, int y, int width, int height, ItemStack stack, int inset) {
        int contentWidth = Math.max(1, width - inset * 2);
        int contentHeight = Math.max(1, height - inset * 2);
        float scale = Math.min(contentWidth / 16.0f, contentHeight / 16.0f);
        int drawWidth = scaledDimension(16, scale);
        int drawHeight = scaledDimension(16, scale);
        drawScaledItemStack(
            x + (width - drawWidth) / 2,
            y + (height - drawHeight) / 2,
            stack,
            scale);
    }

    private static int scaledDimension(int size, float scale) {
        return Math.max(1, Math.round(size * scale));
    }

    private static void drawScaledIcon(int x, int y, Icon icon, float scale, int zOffset) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale, scale, 1.0f);
        icon.getBlitter().dest(0, 0).zOffset(zOffset).blit();
        GlStateManager.popMatrix();
    }

    private static void drawScaledItemStack(int x, int y, ItemStack stack, float scale) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, CONTENT_Z_OFFSET);
        GlStateManager.scale(scale, scale, 1.0f);
        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);
        Minecraft.getMinecraft().getRenderItem().renderItemOverlayIntoGUI(
            Minecraft.getMinecraft().fontRenderer,
            stack,
            0,
            0,
            null);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.popMatrix();
    }
}
