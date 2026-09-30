package ae2.integration.modules.hei;

import ae2.api.stacks.GenericStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.bookmarks.BookmarkList;
import mezz.jei.config.Config;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * Marks materials in HEI's bookmark list, which is what puts them on top of the item list.
 * <p>
 * Both HEI and plain JEI have bookmarks. They differ in what a bookmark can be: HEI bookmarks carry an amount and can be
 * grouped, plain JEI only knows the ingredient. The parts that need more live in {@link HeiBookmarkItems}, so this class
 * works with either.
 */
public final class HeiBookmarkHelper {
    private HeiBookmarkHelper() {
    }

    public static void addBookmarkGroup(List<GenericStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return;
        }

        BookmarkList bookmarkList = getBookmarkList();
        if (bookmarkList == null) {
            return;
        }

        List<GenericStack> convertibleStacks = new ObjectArrayList<>();
        for (GenericStack stack : stacks) {
            if (GenericIngredientHelper.stackToIngredient(stack) != null) {
                convertibleStacks.add(stack);
            }
        }

        if (convertibleStacks.isEmpty()) {
            return;
        }

        if (HeiCompat.isHei()) {
            HeiBookmarkItems.addGroup(bookmarkList, convertibleStacks);
            return;
        }

        // Plain JEI has no groups to put them in, so every material becomes a bookmark of its own.
        for (GenericStack stack : convertibleStacks) {
            Object ingredient = GenericIngredientHelper.stackToIngredient(stack);
            if (ingredient != null) {
                addItem(bookmarkList, ingredient, stack.amount());
            }
        }
    }

    /**
     * Adds a single ingredient to the given bookmark list.
     */
    @SuppressWarnings("deprecation")
    public static void addItem(BookmarkList bookmarkList, Object ingredient, long amount) {
        if (HeiCompat.isHei()) {
            HeiBookmarkItems.addItem(bookmarkList, ingredient, amount);
            return;
        }

        enableBookmarkOverlay();
        bookmarkList.add(ingredient);
    }

    public static List<GenericStack> getBookmarkedStacks() {
        BookmarkList bookmarkList = getBookmarkList();
        if (bookmarkList == null) {
            return Collections.emptyList();
        }

        Object ingredientList = invokeNoArg(bookmarkList, "getIngredientList");
        if (!(ingredientList instanceof Iterable<?> iterable)) {
            return Collections.emptyList();
        }

        List<GenericStack> result = new ObjectArrayList<>();
        for (Object element : iterable) {
            Object ingredient = invokeNoArg(element, "getIngredient");
            GenericStack stack = GenericIngredientHelper.ingredientToStack(ingredient);
            if (stack != null) {
                result.add(stack);
            }
        }
        return result;
    }

    /**
     * Turns the bookmark overlay on, so the bookmarks that are about to be added are visible right away.
     */
    private static void enableBookmarkOverlay() {
        if (!Config.isBookmarkOverlayEnabled()) {
            Config.toggleBookmarkEnabled();
        }
    }

    @Nullable
    private static BookmarkList getBookmarkList() {
        IJeiRuntime runtime = HeiPlugin.getRuntime();
        if (runtime == null) {
            return null;
        }

        Object overlay = runtime.getBookmarkOverlay();
        if (overlay == null) {
            return null;
        }

        Object bookmarkListObject = readBookmarkListField(overlay);
        return bookmarkListObject instanceof BookmarkList bookmarkList ? bookmarkList : null;
    }

    @Nullable
    private static Object readBookmarkListField(Object instance) {
        Field field = findBookmarkListField(instance.getClass());
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    /**
     * The bookmark list sits in a private field of the overlay, and in one of its superclasses on some versions.
     */
    @Nullable
    private static Field findBookmarkListField(Class<?> type) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField("bookmarkList");
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    @Nullable
    private static Object invokeNoArg(Object instance, String methodName) {
        try {
            Method method = instance.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(instance);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
