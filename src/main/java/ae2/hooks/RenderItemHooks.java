package ae2.hooks;

import ae2.api.client.AEKeyRendering;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.client.gui.Icon;
import ae2.items.misc.GenericResourcePackageItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;

import java.util.WeakHashMap;

public final class RenderItemHooks {
    private static final ThreadLocal<ItemStack> OVERRIDING_FOR = new ThreadLocal<>();
    private static final WeakHashMap<ItemStack, AEKey> OVERRIDING = new WeakHashMap<>();

    private RenderItemHooks() {
    }

    public static boolean onRenderItemAndEffectIntoGui(ItemStack stack, int x, int y) {
        if (OVERRIDING_FOR.get() == stack) {
            return false;
        }

        AEKey aeKey = OVERRIDING.computeIfAbsent(stack, RenderItemHooks::unwrapWhat);
        if (aeKey == null) {
            return false;
        }

        OVERRIDING_FOR.set(stack);
        try {
            AEKeyRendering.drawInGui(Minecraft.getMinecraft(), x, y, aeKey);
            if (stack.getItem() instanceof GenericResourcePackageItem) {
                drawPackageOverlay(x, y);
            }
        } finally {
            OVERRIDING_FOR.remove();
        }

        return true;
    }

    public static boolean onRenderItemOverlayIntoGui(ItemStack stack) {
        return OVERRIDING.containsKey(stack);
    }

    private static AEKey unwrapWhat(ItemStack stack) {
        return GenericStack.unwrapWhat(stack);
    }

    private static void drawPackageOverlay(int x, int y) {
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Icon.GENERIC_RESOURCE_PACKAGE_FRAME.getBlitter().dest(x, y).blit();
    }
}
