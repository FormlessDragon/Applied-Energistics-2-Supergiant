package ae2.mixins;

import ae2.hooks.IntrinsicEnchantItem;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentHelper.class)
public class EnchantmentHelperMixin {
    @Inject(method = "getEnchantmentLevel", at = @At("RETURN"), cancellable = true)
    private static void hookGetItemEnchantmentLevel(Enchantment enchID, ItemStack stack,
                                                    CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValueI() == 0 && stack.getItem() instanceof IntrinsicEnchantItem item) {
            int level = item.getIntrinsicEnchantLevel(stack, enchID);
            if (level != 0) {
                cir.setReturnValue(level);
            }
        }
    }
}
