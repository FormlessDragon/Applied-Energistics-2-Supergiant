package ae2.integration.modules.hei;

import ae2.api.integrations.hei.IngredientConverter;
import ae2.api.integrations.hei.IngredientConverters;
import ae2.api.stacks.GenericStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.bookmarks.BookmarkItem;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public final class GenericIngredientHelper {
    private GenericIngredientHelper() {
    }

    @Nullable
    public static GenericStack ingredientToStack(Object ingredient) {
        if (ingredient == null) return null;
        if (ingredient instanceof BookmarkItem<?> bookmarkItem) {
            GenericStack stack = ingredientToStack(bookmarkItem.ingredient);
            if (stack != null && bookmarkItem.amount > 0) {
                return new GenericStack(stack.what(), bookmarkItem.amount);
            }
            return stack;
        }

        for (IngredientConverter<?> converter : IngredientConverters.getConverters()) {
            GenericStack stack = tryConvertToStack(converter, ingredient);
            if (stack != null) {
                return stack;
            }
        }
        return null;
    }

    public static boolean isRegistered(Object ingredient) {
        if (ingredient == null) return false;
        if (ingredient instanceof BookmarkItem<?> bookmarkItem) {
            return isRegistered(bookmarkItem.ingredient);
        }

        for (IngredientConverter<?> converter : IngredientConverters.getConverters()) {
            boolean isIngredient = converter.getIngredientType().getIngredientClass().isInstance(ingredient);
            if (isIngredient) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static Object stackToIngredient(GenericStack stack) {
        if (stack == null) return null;
        for (IngredientConverter<?> converter : IngredientConverters.getConverters()) {
            Object ingredient = tryConvertToIngredient(converter, stack);
            if (ingredient != null) {
                return ingredient;
            }
        }
        return null;
    }

    public static List<List<GenericStack>> getIngredients(IRecipeLayout recipeLayout, boolean input,
                                                          boolean craftingLayout, int craftingGridSize) {
        List<List<GenericStack>> result = new ObjectArrayList<>();
        if (recipeLayout == null) {
            return result;
        }

        if (craftingLayout) {
            for (int i = 0; i < craftingGridSize; i++) {
                result.add(new ObjectArrayList<>());
            }
        }

        for (IngredientConverter<?> converter : IngredientConverters.getConverters()) {
            addIngredients(result, recipeLayout, converter, input, craftingLayout, craftingGridSize);
        }
        return result;
    }

    private static <T> void addIngredients(List<List<GenericStack>> result, IRecipeLayout recipeLayout,
                                           IngredientConverter<T> converter, boolean input, boolean craftingLayout,
                                           int craftingGridSize) {
        int processingSlot = result.size();
        var ingredientGroup = converter.getIngredientGroup(recipeLayout);
        if (ingredientGroup == null) {
            return;
        }

        Map<Integer, ? extends IGuiIngredient<T>> ingredients = ingredientGroup.getGuiIngredients();
        if (ingredients == null || ingredients.isEmpty()) {
            return;
        }

        for (var entry : ingredients.entrySet()) {
            IGuiIngredient<T> guiIngredient = entry.getValue();
            if (guiIngredient == null || guiIngredient.isInput() != input) {
                continue;
            }

            int slotIndex;
            if (craftingLayout) {
                Integer guiSlotKey = entry.getKey();
                if (guiSlotKey == null) {
                    continue;
                }
                int guiSlot = guiSlotKey;
                slotIndex = guiSlot - 1;
                if (guiSlot == 0 || slotIndex < 0 || slotIndex >= craftingGridSize) {
                    continue;
                }
            } else {
                slotIndex = processingSlot++;
            }

            List<GenericStack> stacks = collectStacks(converter, guiIngredient);
            if (!stacks.isEmpty()) {
                ensureSize(result, slotIndex + 1);
                result.set(slotIndex, stacks);
            }
        }
    }

    private static <T> List<GenericStack> collectStacks(IngredientConverter<T> converter,
                                                        IGuiIngredient<T> guiIngredient) {
        ObjectList<GenericStack> stacks = new ObjectArrayList<>();
        T displayed = guiIngredient.getDisplayedIngredient();
        addConvertedStack(stacks, converter, displayed);

        List<T> allIngredients = guiIngredient.getAllIngredients();
        if (allIngredients != null) {
            for (T ingredient : allIngredients) {
                addConvertedStack(stacks, converter, ingredient);
            }
        }
        return stacks;
    }

    private static <T> void addConvertedStack(List<GenericStack> stacks, IngredientConverter<T> converter,
                                              @Nullable T ingredient) {
        if (ingredient == null) {
            return;
        }

        GenericStack stack = converter.getStackFromIngredient(ingredient);
        if (isValidStack(stack)) {
            stacks.add(stack);
        }
    }

    @Nullable
    private static <T> GenericStack tryConvertToStack(IngredientConverter<T> converter, Object ingredient) {
        if (converter.getIngredientType().getIngredientClass().isInstance(ingredient)) {
            GenericStack stack = converter.getStackFromIngredient(
                converter.getIngredientType().getIngredientClass().cast(ingredient));
            return isValidStack(stack) ? stack : null;
        }
        return null;
    }

    @Nullable
    private static <T> T tryConvertToIngredient(IngredientConverter<T> converter, GenericStack stack) {
        return converter.getIngredientFromStack(stack);
    }

    private static void ensureSize(List<List<GenericStack>> result, int size) {
        while (result.size() < size) {
            result.add(new ObjectArrayList<>());
        }
    }

    private static boolean isValidStack(@Nullable GenericStack stack) {
        return stack != null && stack.what() != null && stack.amount() > 0;
    }
}
