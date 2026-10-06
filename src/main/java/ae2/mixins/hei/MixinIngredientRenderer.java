package ae2.mixins.hei;

import ae2.client.NetworkAmount;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import mezz.jei.render.IngredientRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = IngredientRenderer.class, remap = false)
public abstract class MixinIngredientRenderer {
    /**
     * Covers the HEI ingredient list and the bookmark overlay, which draw their tooltip themselves.
     */
    @ModifyReturnValue(method = "getIngredientTooltipSafe", at = @At(value = "RETURN", ordinal = 0))
    private static List<String> ae2$addNetworkAmount(List<String> original, @Local(name = "ingredient") Object ingredient) {
        List<String> tooltip = new ArrayList<>(original);
        NetworkAmount.appendTooltipLine(ingredient, tooltip);
        return tooltip;
    }
}
