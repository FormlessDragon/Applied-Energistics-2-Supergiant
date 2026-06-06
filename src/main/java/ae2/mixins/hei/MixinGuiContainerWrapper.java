package ae2.mixins.hei;

import ae2.api.stacks.GenericStack;
import ae2.integration.modules.hei.GenericIngredientHelper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mezz.jei.input.ClickedIngredient;
import mezz.jei.input.GuiContainerWrapper;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.awt.Rectangle;

@Mixin(value = GuiContainerWrapper.class, remap = false)
public class MixinGuiContainerWrapper {

    @WrapOperation(method = "getIngredientUnderMouse", at = @At(value = "INVOKE", target = "Lmezz/jei/input/ClickedIngredient;create(Ljava/lang/Object;Ljava/awt/Rectangle;)Lmezz/jei/input/ClickedIngredient;"))
    private ClickedIngredient<Object> wrapFluidPacket(final Object value, final Rectangle area, final Operation<ClickedIngredient<Object>> original) {
        if (value instanceof ItemStack i) {
            var stack = GenericStack.unwrapItemStack(i);
            if (stack != null) {
                var converted = GenericIngredientHelper.stackToIngredient(stack);
                if (converted != null) {
                    return original.call(converted, area);
                }
            }
        }
        return original.call(value, area);
    }
}
