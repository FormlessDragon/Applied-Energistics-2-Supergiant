package ae2.integration.modules.hei;

import ae2.api.stacks.GenericStack;
import ae2.mixins.hei.AccessorBookmarkItem;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.bookmarks.BookmarkList;
import mezz.jei.config.Config;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The bookmark handling HEI adds to JEI: bookmark items that carry an amount, and bookmark groups to put them in.
 * <p>
 * Plain JEI has bookmarks as well, but neither of the two, so this class is only touched after
 * {@link HeiCompat#isHei()} said yes. It is also the only place that may touch {@link AccessorBookmarkItem}: a mixin
 * class can only be loaded while the mixin is applied, so shared code must never refer to it.
 */
public final class HeiBookmarkItems {
    /**
     * The ids HEI hands out for its own groups start at zero and grow slowly, so a group holding the materials of an
     * order takes an id far above them and stays out of the way.
     */
    private static int nextGroupId = 1_000_000_000;

    private HeiBookmarkItems() {
    }

    /**
     * True if the given ingredient is a HEI bookmark item, which wraps another ingredient and the amount it was
     * bookmarked with.
     */
    public static boolean isBookmarkItem(Object ingredient) {
        return ingredient instanceof AccessorBookmarkItem<?>;
    }

    /**
     * The ingredient a HEI bookmark item wraps. Only meaningful when {@link #isBookmarkItem(Object)} said yes.
     */
    public static Object unwrapBookmark(Object ingredient) {
        return ((AccessorBookmarkItem<?>) ingredient).i_getIngredient();
    }

    /**
     * The amount a HEI bookmark item was bookmarked with. Only meaningful when {@link #isBookmarkItem(Object)} said
     * yes.
     */
    public static long bookmarkAmount(Object ingredient) {
        return ((AccessorBookmarkItem<?>) ingredient).i_getAmount();
    }

    /**
     * The stack a HEI bookmark item stands for, amount included, or null when the ingredient is not a bookmark item.
     */
    @Nullable
    public static GenericStack asBookmarkStack(Object ingredient) {
        if (!isBookmarkItem(ingredient)) {
            return null;
        }

        GenericStack stack = GenericIngredientHelper.ingredientToStack(unwrapBookmark(ingredient));
        long amount = bookmarkAmount(ingredient);
        if (stack != null && amount > 0) {
            return new GenericStack(stack.what(), amount);
        }
        return stack;
    }

    /**
     * True if the given ingredient is a HEI bookmark item that wraps an ItemStack, in which case the tooltip HEI draws
     * for it is the one of that item stack.
     */
    public static boolean isItemStack(Object ingredient) {
        if (!isBookmarkItem(ingredient)) {
            return false;
        }

        return unwrapBookmark(ingredient) instanceof ItemStack;
    }

    /**
     * Adds the given materials to the bookmark list as one group.
     */
    static void addGroup(BookmarkList bookmarkList, List<GenericStack> stacks) {
        bookmarkList.add(new AEMissingBookmarkGroup(nextGroupId++, stacks));
    }

    /**
     * Adds a single ingredient to the bookmark list, remembered with the amount it is needed in.
     */
    static void addItem(BookmarkList bookmarkList, Object ingredient, long amount) {
        bookmarkList.add(createItem(ingredient, amount));
    }

    static BookmarkItem<?> createItem(Object ingredient, long amount) {
        if (!Config.isBookmarkOverlayEnabled()) {
            Config.toggleBookmarkEnabled();
        }

        //noinspection DataFlowIssue
        AccessorBookmarkItem<?> bookmarkItem = (AccessorBookmarkItem<?>) new BookmarkItem<>(ingredient);
        bookmarkItem.i_setAmount(Math.max(1, amount));
        return (BookmarkItem<?>) bookmarkItem;
    }
}
