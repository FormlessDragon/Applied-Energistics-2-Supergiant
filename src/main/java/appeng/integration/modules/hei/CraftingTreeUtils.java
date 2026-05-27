package appeng.integration.modules.hei;

import appeng.api.stacks.GenericStack;
import appeng.client.ClientTickHandler;
import appeng.core.AELog;
import mezz.jei.Internal;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.bookmarks.BookmarkList;
import mezz.jei.config.Config;
import mezz.jei.input.InputHandler;

import java.lang.reflect.Field;

public class CraftingTreeUtils {

    public static Field inputHandler = null;
    public static Field bookmarkList = null;

    static {
        // I Just want to get the BookmarkList...
        try {
            Field inputHandler = Internal.class.getDeclaredField("inputHandler");
            inputHandler.setAccessible(true);
            CraftingTreeUtils.inputHandler = inputHandler;

            Field bookmarkList = InputHandler.class.getDeclaredField("bookmarkList");
            bookmarkList.setAccessible(true);
            CraftingTreeUtils.bookmarkList = bookmarkList;
        } catch (NoSuchFieldException e) {
            AELog.warn(e);
        }
    }

    public static IJeiRuntime getJeiRuntime() {
        return Internal.getRuntime();
    }

    public static boolean addIngredientToBookmarkList(GenericStack stack) {
        if (inputHandler == null || bookmarkList == null || stack == null) {
            return false;
        }

        Object ingredient = GenericIngredientHelper.stackToIngredient(stack);
        if (ingredient == null) {
            return false;
        }

        try {
            InputHandler handler = (InputHandler) inputHandler.get(null);
            BookmarkList bookmark = (BookmarkList) bookmarkList.get(handler);

            if (!Config.isBookmarkOverlayEnabled()) {
                Config.toggleBookmarkEnabled();
            }
            BookmarkItem<Object> bookmarkItem = new BookmarkItem<>(ingredient);
            bookmarkItem.amount = Math.max(1, stack.amount());
            bookmark.add(bookmarkItem);
        } catch (IllegalAccessException e) {
            AELog.warn(e);
        }

        return true;
    }

    public static boolean showStackFocus(GenericStack stack, final IFocus.Mode mode) {
        ClientTickHandler.addTask(() -> {
            IJeiRuntime runtime = CraftingTreeUtils.getJeiRuntime();
            Object ingredient = GenericIngredientHelper.stackToIngredient(stack);
            if (ingredient == null) {
                return;
            }
            IFocus<Object> focus = runtime.getRecipeRegistry().createFocus(mode, ingredient);
            runtime.getRecipesGui().show(focus);
        });
        return true;
    }

}
