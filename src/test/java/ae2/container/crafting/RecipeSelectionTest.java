package ae2.container.crafting;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RecipeSelectionTest {
    private static final ResourceLocation FIRST_RECIPE = new ResourceLocation("ae2", "first");
    private static final ResourceLocation LAST_USED_RECIPE = new ResourceLocation("ae2", "last_used");
    private static final ResourceLocation MISSING_RECIPE = new ResourceLocation("ae2", "missing");

    @Test
    void retainsTheLastSuccessfulRecipeWhenItStillMatches() {
        var first = new RecipeSelection.Candidate(FIRST_RECIPE, null, ItemStack.EMPTY);
        var lastUsed = new RecipeSelection.Candidate(LAST_USED_RECIPE, null, ItemStack.EMPTY);

        var selected = RecipeSelection.select(List.of(first, lastUsed), LAST_USED_RECIPE);

        assertEquals(lastUsed, selected);
    }

    @Test
    void fallsBackToTheFirstCurrentMatchWhenTheLastRecipeNoLongerMatches() {
        var first = new RecipeSelection.Candidate(FIRST_RECIPE, null, ItemStack.EMPTY);
        var lastUsed = new RecipeSelection.Candidate(LAST_USED_RECIPE, null, ItemStack.EMPTY);

        var selected = RecipeSelection.select(List.of(first, lastUsed), MISSING_RECIPE);

        assertEquals(first, selected);
    }

    @Test
    void hasNoSelectionWhenThereAreNoMatchingRecipes() {
        assertNull(RecipeSelection.select(List.of(), LAST_USED_RECIPE));
    }
}
