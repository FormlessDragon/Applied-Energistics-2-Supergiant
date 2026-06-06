package ae2.hooks;

import ae2.api.client.AEKeyRendering;
import ae2.api.stacks.GenericStack;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

public final class RenderItemHooks {
    private static final ThreadLocal<ItemStack> OVERRIDING_FOR = new ThreadLocal<>();

    private RenderItemHooks() {
    }

    public static boolean onRenderItemAndEffectIntoGui(ItemStack stack, int x, int y) {
        if (OVERRIDING_FOR.get() == stack) {
            return false;
        }

        GenericStack genericStack = GenericStack.unwrapItemStack(stack);
        if (genericStack == null) {
            return false;
        }

        OVERRIDING_FOR.set(stack);
        try {
            AEKeyRendering.drawInGui(Minecraft.getMinecraft(), x, y, genericStack.what());
        } finally {
            OVERRIDING_FOR.remove();
        }

        return true;
    }

    public static boolean onRenderItemOverlayIntoGui(ItemStack stack) {
        return GenericStack.unwrapItemStack(stack) != null;
    }
}
