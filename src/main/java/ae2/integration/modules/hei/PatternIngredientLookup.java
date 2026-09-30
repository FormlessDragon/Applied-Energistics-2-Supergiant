package ae2.integration.modules.hei;

import ae2.api.crafting.PatternDetailsHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class PatternIngredientLookup {
    private PatternIngredientLookup() {
    }

    public static Object redirectToPrimaryOutput(Object ingredient, @Nullable World level) {
        Object lookupIngredient = HeiCompat.isHei() && HeiBookmarkItems.isBookmarkItem(ingredient)
            ? HeiBookmarkItems.unwrapBookmark(ingredient)
            : ingredient;
        Object unwrappedIngredient = GenericIngredientHelper.unwrapWrappedIngredient(lookupIngredient);
        if (level == null || !(unwrappedIngredient instanceof ItemStack patternStack)) {
            return unwrappedIngredient;
        }

        var pattern = PatternDetailsHelper.decodePattern(patternStack, level);
        if (pattern == null) {
            return unwrappedIngredient;
        }

        Object primaryOutput = GenericIngredientHelper.stackToIngredient(pattern.getPrimaryOutput());
        return primaryOutput != null ? primaryOutput : unwrappedIngredient;
    }
}
