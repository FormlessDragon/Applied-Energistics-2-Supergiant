package ae2.mixins.hei;

import mezz.jei.gui.ghost.GhostIngredientDragManager;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import mezz.jei.gui.overlay.bookmarks.ILeftAreaContent;
import mezz.jei.gui.overlay.bookmarks.LeftAreaDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Routes clicks outside the bookmark panel to its active ghost drag, like JEI's ingredient-list overlay does. */
@Mixin(value = LeftAreaDispatcher.class, remap = false)
public abstract class MixinLeftAreaDispatcher {
    @Shadow
    @Final
    private List<ILeftAreaContent> contents;

    @Shadow
    private int current;

    @Inject(method = "handleMouseClicked", at = @At("HEAD"), cancellable = true)
    private void ae2$handleActiveBookmarkDrag(int mouseX, int mouseY, int mouseButton,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (this.current >= 0
            && this.current < this.contents.size()
            && this.contents.get(this.current) instanceof BookmarkOverlay bookmarkOverlay
            && ae2$handleBookmarkDrag(bookmarkOverlay, mouseX, mouseY)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private static boolean ae2$handleBookmarkDrag(BookmarkOverlay bookmarkOverlay, int mouseX, int mouseY) {
        GhostIngredientDragManager manager = ((AccessorBookmarkOverlay) bookmarkOverlay).ae2$getDragManager();
        return ((InvokerGhostIngredientDragManager) manager).ae2$handleMouseClicked(mouseX, mouseY);
    }
}
