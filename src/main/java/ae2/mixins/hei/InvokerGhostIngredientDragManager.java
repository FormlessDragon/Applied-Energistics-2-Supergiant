package ae2.mixins.hei;

import mezz.jei.gui.ghost.GhostIngredientDragManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Invokes plain JEI's two-argument drag completion method without reflective calls. */
@Mixin(value = GhostIngredientDragManager.class, remap = false)
public interface InvokerGhostIngredientDragManager {
    @Invoker("handleMouseClicked")
    boolean ae2$handleMouseClicked(int mouseX, int mouseY);
}
