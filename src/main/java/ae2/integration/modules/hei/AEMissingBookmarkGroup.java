package ae2.integration.modules.hei;

import ae2.api.stacks.GenericStack;
import mezz.jei.bookmarks.BookmarkGroup;
import mezz.jei.bookmarks.BookmarkItem;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.List;

/**
 * The bookmark group the terminal puts the materials of an order into.
 * <p>
 * Only HEI knows bookmark groups, so this class is only loaded after {@link HeiCompat#isHei()} said yes.
 */
final class AEMissingBookmarkGroup extends BookmarkGroup {

    AEMissingBookmarkGroup(int id, List<GenericStack> missingStacks) {
        super(id);

        for (var stack : missingStacks) {
            Object ingredient = GenericIngredientHelper.stackToIngredient(stack);
            if (ingredient == null) {
                continue;
            }

            addItemInternal(HeiBookmarkItems.createItem(ingredient, stack.amount()));
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
