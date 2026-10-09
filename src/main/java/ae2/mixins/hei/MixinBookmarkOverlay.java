package ae2.mixins.hei;

import mezz.jei.bookmarks.BookmarkList;
import mezz.jei.config.Config;
import mezz.jei.gui.ghost.GhostIngredientDragManager;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Repairs plain JEI cancelling bookmark drags when the left overlay temporarily has no room. */
@Mixin(value = BookmarkOverlay.class, remap = false)
public abstract class MixinBookmarkOverlay {
    @Shadow
    @Final
    private BookmarkList bookmarkList;

    @Shadow
    private boolean hasRoom;

    @Shadow
    @Final
    private GhostIngredientDragManager ghostIngredientDragManager;

    @Redirect(
        method = "drawScreen",
        at = @At(value = "INVOKE", target = "Lmezz/jei/gui/ghost/GhostIngredientDragManager;stopDrag()V"))
    private void ae2$stopDragOnlyWhenBookmarksAreHidden(GhostIngredientDragManager manager) {
        if (!Config.isBookmarkOverlayEnabled() || this.bookmarkList.isEmpty()) {
            manager.stopDrag();
        }
    }

    @Inject(method = "drawTooltips", at = @At("HEAD"))
    private void ae2$drawDragWhileOverlayHasNoRoom(Minecraft minecraft, int mouseX, int mouseY, CallbackInfo ci) {
        if (!this.hasRoom && Config.isBookmarkOverlayEnabled() && !this.bookmarkList.isEmpty()) {
            this.ghostIngredientDragManager.drawTooltips(minecraft, mouseX, mouseY);
        }
    }
}
