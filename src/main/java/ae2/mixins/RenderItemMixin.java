package ae2.mixins;

import ae2.hooks.RenderItemHooks;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderItem.class)
public class RenderItemMixin {

    @Inject(method = "renderItemAndEffectIntoGUI(Lnet/minecraft/item/ItemStack;II)V", at = @At("HEAD"), cancellable = true)
    private void ae2_renderWrappedStack(ItemStack stack, int xPosition, int yPosition, CallbackInfo ci) {
        if (RenderItemHooks.onRenderItemAndEffectIntoGui(stack, xPosition, yPosition)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderItemAndEffectIntoGUI(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;II)V", at = @At("HEAD"), cancellable = true)
    private void ae2_renderWrappedStack(EntityLivingBase livingBase, ItemStack stack, int x, int y, CallbackInfo ci) {
        if (RenderItemHooks.onRenderItemAndEffectIntoGui(stack, x, y)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderItemOverlayIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void ae2_skipWrappedStackOverlay(FontRenderer fontRenderer, ItemStack stack, int xPosition,
                                             int yPosition, String text, CallbackInfo ci) {
        if (RenderItemHooks.onRenderItemOverlayIntoGui(stack)) {
            ci.cancel();
        }
    }
}
