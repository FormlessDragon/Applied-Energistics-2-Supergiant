package ae2.integration.modules.hei;

import ae2.api.stacks.GenericStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.bookmarks.BookmarkList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

public final class HeiBookmarkHelper {
    private static int fallbackNextGroupId = 1_000_000_000;

    private HeiBookmarkHelper() {
    }

    public static void addBookmarkGroup(List<GenericStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return;
        }

        IJeiRuntime runtime = HeiPlugin.getRuntime();
        if (runtime == null) {
            return;
        }

        Object overlay = runtime.getBookmarkOverlay();
        if (overlay == null) {
            return;
        }

        Object bookmarkListObject = readBookmarkListField(overlay);
        if (!(bookmarkListObject instanceof BookmarkList bookmarkList)) {
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

        var group = new AEMissingBookmarkGroup(nextBookmarkGroupId(bookmarkList), convertibleStacks);
        bookmarkList.add(group);
    }

    public static List<GenericStack> getBookmarkedStacks() {
        IJeiRuntime runtime = HeiPlugin.getRuntime();
        if (runtime == null) {
            return Collections.emptyList();
        }

        Object overlay = runtime.getBookmarkOverlay();
        if (overlay == null) {
            return Collections.emptyList();
        }

        Object bookmarkList = readBookmarkListField(overlay);
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

    @Nullable
    private static Object readBookmarkListField(Object instance) {
        Field field = findField(instance.getClass(), "bookmarkList");
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

    private static int nextBookmarkGroupId(BookmarkList bookmarkList) {
        Object nextId = invokeNoArg(bookmarkList, "nextId");
        if (nextId instanceof Integer id) {
            return id;
        }

        Field field = findField(bookmarkList.getClass(), "nextId");
        if (field == null) {
            return fallbackNextGroupId++;
        }

        try {
            field.setAccessible(true);
            int id = field.getInt(bookmarkList);
            field.setInt(bookmarkList, id + 1);
            return id;
        } catch (ReflectiveOperationException ignored) {
            return fallbackNextGroupId++;
        }
    }

    @Nullable
    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
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
