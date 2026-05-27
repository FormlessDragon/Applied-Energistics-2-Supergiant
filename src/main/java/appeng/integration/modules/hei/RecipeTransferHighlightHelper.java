package appeng.integration.modules.hei;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntCollection;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import java.awt.Color;
import java.util.Map;

final class RecipeTransferHighlightHelper {
    private static final Color MISSING_SLOT_COLOR = new Color(1.0f, 0.0f, 0.0f, 0.4f);
    private static final Color CRAFTABLE_SLOT_COLOR = new Color(0.2f, 0.45f, 1.0f, 0.4f);

    private RecipeTransferHighlightHelper() {
    }

    static void drawHighlights(Minecraft minecraft, IRecipeLayout recipeLayout, IntCollection missingGuiSlots,
                               IntCollection craftableGuiSlots, int recipeX, int recipeY) {
        Map<Integer, ? extends IGuiIngredient<ItemStack>> ingredients = recipeLayout.getItemStacks()
                                                                                    .getGuiIngredients();
        drawHighlights(minecraft, ingredients, missingGuiSlots, MISSING_SLOT_COLOR, recipeX, recipeY);
        drawHighlights(minecraft, ingredients, craftableGuiSlots, CRAFTABLE_SLOT_COLOR, recipeX, recipeY);
    }

    private static void drawHighlights(Minecraft minecraft, Map<Integer, ? extends IGuiIngredient<ItemStack>> ingredients,
                                       IntCollection slots, Color color, int recipeX, int recipeY) {
        if (ingredients instanceof Int2ObjectMap<? extends IGuiIngredient<ItemStack>> ingredientMap) {
            for (int slotIndex : slots) {
                drawHighlight(minecraft, ingredientMap.get(slotIndex), color, recipeX, recipeY);
            }
            return;
        }

        for (int slotIndex : slots) {
            drawHighlight(minecraft, ingredients.get(slotIndex), color, recipeX, recipeY);
        }
    }

    private static void drawHighlight(Minecraft minecraft, IGuiIngredient<ItemStack> ingredient, Color color,
                                      int recipeX, int recipeY) {
        if (ingredient != null) {
            ingredient.drawHighlight(minecraft, color, recipeX, recipeY);
        }
    }
}
