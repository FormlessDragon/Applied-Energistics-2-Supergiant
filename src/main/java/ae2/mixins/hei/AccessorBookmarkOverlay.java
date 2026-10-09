package ae2.mixins.hei;

import mezz.jei.gui.ghost.GhostIngredientDragManager;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accesses the separate ghost drag manager owned by plain JEI's bookmark overlay. */
@Mixin(value = BookmarkOverlay.class, remap = false)
public interface AccessorBookmarkOverlay {
    @Accessor("ghostIngredientDragManager")
    GhostIngredientDragManager ae2$getDragManager();
}
