package ae2.integration.modules.hei;

import ae2.core.localization.HeiText;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntCollections;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.gui.TooltipRenderer;
import mezz.jei.gui.recipes.RecipeLayout;
import mezz.jei.gui.recipes.RecipeTransferButton;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.NonNull;

final class PatternRecipeTransferUserError implements IRecipeTransferError {
    private final IRecipeLayout recipeLayout;
    private final IntCollection missingGuiSlots;
    private final IntCollection craftableGuiSlots;

    PatternRecipeTransferUserError(IRecipeLayout recipeLayout, IntCollection missingGuiSlots,
                                   IntCollection craftableGuiSlots) {
        this.recipeLayout = recipeLayout;
        this.missingGuiSlots = IntCollections.unmodifiable(missingGuiSlots);
        this.craftableGuiSlots = IntCollections.unmodifiable(craftableGuiSlots);
    }

    @Override
    public Type getType() {
        enableTransferButton();
        return Type.USER_FACING;
    }

    @Override
    public void showError(@NonNull Minecraft minecraft, int mouseX, int mouseY, @NonNull IRecipeLayout recipeLayout, int recipeX,
                          int recipeY) {
        RecipeTransferHighlightHelper.drawHighlights(minecraft, recipeLayout, missingGuiSlots, craftableGuiSlots,
            recipeX, recipeY);
        TooltipRenderer.drawHoveringText(minecraft, HeiText.MoveItems.getLocal(), mouseX, mouseY);
    }

    private void enableTransferButton() {
        if (!(this.recipeLayout instanceof RecipeLayout concreteLayout)) {
            return;
        }

        RecipeTransferButton button = concreteLayout.getRecipeTransferButton();
        if (button != null) {
            button.enabled = true;
            button.visible = true;
        }
    }
}
