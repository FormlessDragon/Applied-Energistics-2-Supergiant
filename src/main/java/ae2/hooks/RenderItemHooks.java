package ae2.hooks;

import ae2.api.client.AEKeyRendering;
import ae2.api.stacks.AEKey;
import ae2.items.misc.WrappedGenericStack;
import net.minecraft.client.Minecraft;
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

        if (!(stack.getItem() instanceof WrappedGenericStack item)) {
            return false;
        }
        AEKey aeKey = OVERRIDING.computeIfAbsent(stack, item::unwrapWhat);
        if (aeKey == null) {
            return false;
        }

        OVERRIDING_FOR.set(stack);
        try {
            AEKeyRendering.drawInGui(Minecraft.getMinecraft(), x, y, aeKey);
        } finally {
            OVERRIDING_FOR.remove();
        }

        return true;
    }

    public static boolean onRenderItemOverlayIntoGui(ItemStack stack) {
        return stack.getItem() instanceof WrappedGenericStack item && item.unwrapWhat(stack) != null;
    }
}
