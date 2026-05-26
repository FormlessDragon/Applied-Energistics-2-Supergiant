package appeng.integration.modules.hei;

import appeng.api.stacks.GenericStack;
import mezz.jei.bookmarks.BookmarkGroup;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.config.Config;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.List;

final class AEMissingBookmarkGroup extends BookmarkGroup {

    AEMissingBookmarkGroup(int id, List<GenericStack> missingStacks) {
        super(id);

        for (var stack : missingStacks) {
            Object ingredient = GenericIngredientHelper.stackToIngredient(stack);
            if (ingredient == null) {
                continue;
            }

            if (!Config.isBookmarkOverlayEnabled()) {
                Config.toggleBookmarkEnabled();
            }

            BookmarkItem<Object> bookmarkItem = new BookmarkItem<>(ingredient);
            bookmarkItem.amount = Math.max(1, stack.amount());
            addItemInternal(bookmarkItem);
        }
    }

    @Override
    public boolean addItem(@NotNull BookmarkItem<?> item) {
        return false;
    }

    @Override
    public boolean canAddItem(@NotNull BookmarkItem<?> item) {
        return false;
    }

    @Override
    public boolean acceptsChanges() {
        return false;
    }

    @Override
    public int getColor() {
        return Color.blue.getRGB();
    }
}
