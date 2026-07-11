package ae2.client.gui.me.common;

import ae2.api.client.AEKeyRendering;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AmountFormat;
import ae2.core.AELog;
import ae2.core.definitions.AEBlocks;
import ae2.core.localization.GuiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shows a Windows toast when a crafting job finishes while the game is in the background.
 */
final class WindowsCraftingNotification {
    private static final int ICON_SIZE = 64;
    private static final ExecutorService NOTIFICATION_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "AE2 Windows notification");
        thread.setDaemon(true);
        return thread;
    });
    private static BufferedImage applicationIcon;
    private WindowsCraftingNotification() {
    }

    /**
     * @return true if a native notification was scheduled, false if the in-game toast should be used
     */
    static boolean showWhenUnfocused(AEKey what, long amount) {
        if (!isWindows() || isGameWindowFocused()) {
            return false;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        try {
            BufferedImage icon = renderIcon(minecraft, what);
            BufferedImage appIcon = getApplicationIcon(minecraft, icon);
            String formattedAmount = what.getType().formatAmount(amount, AmountFormat.SLOT);
            String title = GuiText.ToastCraftingJobFinishedTitle.getLocal();
            String body = GuiText.ToastCraftingJobFinishedText.getLocal(formattedAmount,
                AEKeyRendering.getDisplayName(what).getUnformattedText());

            NOTIFICATION_EXECUTOR.execute(() -> {
                if (!WindowsShellNotifier.show(appIcon, icon, title, body)) {
                    minecraft.addScheduledTask(() -> minecraft.getToastGui().add(new FinishedJobToast(what, amount)));
                } else {
                    AELog.info("Submitted Windows crafting notification: %s", body);
                }
            });
            return true;
        } catch (RuntimeException | LinkageError e) {
            AELog.warn(e, "Could not prepare Windows crafting notification");
            return false;
        }
    }

    private static BufferedImage getApplicationIcon(Minecraft minecraft, BufferedImage fallback) {
        if (applicationIcon == null) {
            AEItemKey controller = AEItemKey.of(AEBlocks.CONTROLLER.stack());
            if (controller != null) {
                applicationIcon = renderIcon(minecraft, controller);
            }
        }
        return applicationIcon == null ? fallback : applicationIcon;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private static boolean isGameWindowFocused() {
        try {
            return Display.isActive();
        } catch (RuntimeException | LinkageError e) {
            AELog.debug("Could not determine whether the game window is focused: %s", e);
            return true;
        }
    }

    private static BufferedImage renderIcon(Minecraft minecraft, AEKey what) {
        Framebuffer framebuffer = new Framebuffer(ICON_SIZE, ICON_SIZE, true);
        framebuffer.setFramebufferColor(0.0F, 0.0F, 0.0F, 0.0F);

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        try {
            framebuffer.framebufferClear();
            framebuffer.bindFramebuffer(true);
            GlStateManager.viewport(0, 0, ICON_SIZE, ICON_SIZE);

            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0.0D, 16.0D, 16.0D, 0.0D, 1000.0D, 3000.0D);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.loadIdentity();
            GlStateManager.translate(0.0F, 0.0F, -2000.0F);
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.enableBlend();

            AEKeyRendering.drawInGui(minecraft, 0, 0, what);
            return readFramebuffer(ICON_SIZE, ICON_SIZE);
        } finally {
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            framebuffer.deleteFramebuffer();
            restoreMainFramebuffer(minecraft);
        }
    }

    private static BufferedImage readFramebuffer(int width, int height) {
        ByteBuffer pixelBuffer = BufferUtils.createByteBuffer(width * height * 4);
        int[] pixels = new int[width * height];

        GlStateManager.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        pixelBuffer.clear();
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixelBuffer);

        for (int y = 0; y < height; y++) {
            int sourceRow = height - 1 - y;
            for (int x = 0; x < width; x++) {
                int sourceIndex = (sourceRow * width + x) * 4;
                int red = pixelBuffer.get(sourceIndex) & 0xFF;
                int green = pixelBuffer.get(sourceIndex + 1) & 0xFF;
                int blue = pixelBuffer.get(sourceIndex + 2) & 0xFF;
                int alpha = pixelBuffer.get(sourceIndex + 3) & 0xFF;
                pixels[y * width + x] = alpha << 24 | red << 16 | green << 8 | blue;
            }
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    private static void restoreMainFramebuffer(Minecraft minecraft) {
        if (OpenGlHelper.isFramebufferEnabled()) {
            minecraft.getFramebuffer().bindFramebuffer(true);
        } else {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);
        }
        GlStateManager.viewport(0, 0, minecraft.displayWidth, minecraft.displayHeight);
    }
}
