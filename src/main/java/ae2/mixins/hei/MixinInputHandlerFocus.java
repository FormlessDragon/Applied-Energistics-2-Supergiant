package ae2.mixins.hei;

import ae2.integration.modules.hei.PatternIngredientLookup;
import mezz.jei.input.InputHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Redirects what the recipe and uses keybindings look up to the pattern output the hovered ingredient stands for.
 * <p>
 * {@code InputHandler.showRecipeOrUses} only exists in HEI, so {@code AE2MixinPlugin} only applies this mixin when it
 * finds that method. The plain JEI equivalent part is in {@link MixinInputHandler}.
 */
@Mixin(value = InputHandler.class, remap = false)
public class MixinInputHandlerFocus {

    @ModifyArg(
        method = "showRecipeOrUses(Lmezz/jei/api/recipe/IFocus$Mode;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lmezz/jei/gui/Focus;<init>(Lmezz/jei/api/recipe/IFocus$Mode;Ljava/lang/Object;)V"),
        index = 1)
    private Object ae2$redirectKeyLookupToPatternOutput(Object ingredient) {
        return PatternIngredientLookup.redirectToPrimaryOutput(ingredient, Minecraft.getMinecraft().world);
    }
}
