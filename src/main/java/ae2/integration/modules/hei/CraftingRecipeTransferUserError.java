package ae2.integration.modules.hei;

import ae2.core.localization.ItemModText;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntCollections;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.gui.recipes.RecipeLayout;
import mezz.jei.gui.recipes.RecipeTransferButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.config.GuiUtils;
import org.jspecify.annotations.NonNull;

import java.util.List;

final class CraftingRecipeTransferUserError implements IRecipeTransferError {
    private final IRecipeLayout recipeLayout;
    private final List<String> tooltip;
    private final IntCollection missingGuiSlots;
    private final IntCollection craftableGuiSlots;

    private CraftingRecipeTransferUserError(IRecipeLayout recipeLayout,
                                            List<String> tooltip,
                                            IntCollection missingGuiSlots,
                                            IntCollection craftableGuiSlots) {
        this.recipeLayout = recipeLayout;
        this.tooltip = List.copyOf(tooltip);
        this.missingGuiSlots = IntCollections.unmodifiable(missingGuiSlots);
        this.craftableGuiSlots = IntCollections.unmodifiable(craftableGuiSlots);
    }

    static IRecipeTransferError create(IRecipeLayout recipeLayout, CraftingRecipeTransferAnalysis analysis) {
        List<String> tooltip = new ObjectArrayList<>();
        tooltip.add(I18n.format("jei.tooltip.transfer"));

        if (analysis.outcome() == CraftingRecipeTransferAnalysis.Outcome.READY) {
            throw new IllegalArgumentException("Ready transfers do not need a user-facing error");
        }

        if (analysis.hasImmediatelyAvailableIngredients()) {
            tooltip.add(TextFormatting.RED + ItemModText.RecipeTransferImportsAvailable.getLocal());
        }
        if (analysis.hasCraftableMissingIngredients()) {
            tooltip.add(TextFormatting.RED + ItemModText.RecipeTransferRequestsCraftableMissing.getLocal());
        }
        if (analysis.hasUncraftableMissingIngredients()) {
            if (analysis.hasCraftableMissingIngredients()) {
                tooltip.add(TextFormatting.RED + ItemModText.RecipeTransferLeavesMissing.getLocal());
            } else if (!analysis.hasImmediatelyAvailableIngredients()) {
                tooltip.add(TextFormatting.RED + ItemModText.NoItems.getLocal());
            } else {
                tooltip.add(TextFormatting.RED + ItemModText.RecipeTransferLeavesMissing.getLocal());
            }
        }

        return new CraftingRecipeTransferUserError(recipeLayout, tooltip, analysis.getMissingGuiSlots(),
            analysis.getCraftableGuiSlots());
    }

    @Override
    public Type getType() {
        enableTransferButton();
        return Type.USER_FACING;
    }

    @Override
    public void showError(Minecraft minecraft, int mouseX, int mouseY, @NonNull IRecipeLayout recipeLayout, int recipeX,
                          int recipeY) {
        int screenWidth = minecraft.currentScreen != null ? minecraft.currentScreen.width : minecraft.displayWidth;
        int screenHeight = minecraft.currentScreen != null ? minecraft.currentScreen.height : minecraft.displayHeight;

        RecipeTransferHighlightHelper.drawHighlights(minecraft, recipeLayout, missingGuiSlots, craftableGuiSlots,
            recipeX, recipeY);

        GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, screenWidth, screenHeight, 150,
            minecraft.fontRenderer);
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
