package ae2.recipes.handlers;

import ae2.recipes.AERecipeTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

public class InscriberRecipe {
    private final Ingredient middleInput;
    private final Ingredient topOptional;
    private final Ingredient bottomOptional;
    private final ItemStack output;
    private final InscriberProcessType processType;

    public InscriberRecipe(Ingredient middleInput, ItemStack output, Ingredient topOptional, Ingredient bottomOptional,
                           InscriberProcessType processType) {
        this.middleInput = middleInput;
        this.topOptional = topOptional;
        this.bottomOptional = bottomOptional;
        this.output = output.copy();
        this.processType = processType;
    }

    private static boolean matchesOptional(Ingredient ingredient, ItemStack stack) {
        return ingredient == Ingredient.EMPTY ? stack.isEmpty() : ingredient.apply(stack);
    }

    public static @Nullable InscriberRecipe findRecipe(ItemStack top, ItemStack middle, ItemStack bottom) {
        for (var recipe : AERecipeTypes.INSCRIBER.getRecipes()) {
            if (recipe.matches(top, middle, bottom)) {
                return recipe;
            }
        }
        return null;
    }

    public Ingredient getMiddleInput() {
        return this.middleInput;
    }

    public Ingredient getTopOptional() {
        return this.topOptional;
    }

    public Ingredient getBottomOptional() {
        return this.bottomOptional;
    }

    public ItemStack getResultItem() {
        return this.output.copy();
    }

    public InscriberProcessType getProcessType() {
        return this.processType;
    }

    public boolean matches(ItemStack top, ItemStack middle, ItemStack bottom) {
        return matchesOptional(this.topOptional, top)
            && this.middleInput.apply(middle)
            && matchesOptional(this.bottomOptional, bottom);
    }
}
