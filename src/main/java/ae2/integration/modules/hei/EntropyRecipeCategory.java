package ae2.integration.modules.hei;

import ae2.core.AppEng;
import ae2.core.definitions.AEItems;
import ae2.core.localization.HeiText;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@ParametersAreNonnullByDefault
class EntropyRecipeCategory implements IRecipeCategory<EntropyRecipeWrapper> {
    static final String UID = "ae2.entropy";

    private final IDrawable background;
    private final IDrawable slotDrawable;
    private final IDrawable arrow;
    private final String localizedName;

    EntropyRecipeCategory(IGuiHelper guiHelper) {
        ResourceLocation location = new ResourceLocation(AppEng.MOD_ID, "textures/guis/jei.png");
        this.background = new HeiBackgroundDrawable(130, 60);
        this.slotDrawable = guiHelper.createDrawable(location, 0, 34, 18, 18);
        this.arrow = guiHelper.createDrawable(location, 0, 0, 24, 17);
        this.localizedName = AEItems.ENTROPY_MANIPULATOR.stack().getDisplayName();
    }

    @Override
    public String getUid() {
        return UID;
    }

    @Override
    public String getTitle() {
        return this.localizedName;
    }

    @Override
    public String getModName() {
        return AppEng.MOD_NAME;
    }

    @Override
    public IDrawable getBackground() {
        return this.background;
    }

    @Override
    public void drawExtras(Minecraft minecraft) {
        this.arrow.draw(minecraft, 49, 22);
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, EntropyRecipeWrapper recipeWrapper, IIngredients ingredients) {
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        IGuiFluidStackGroup fluidStacks = recipeLayout.getFluidStacks();

        int inputX = 14;
        if (recipeWrapper.hasInputItem()) {
            itemStacks.init(0, true, inputX, 22);
            itemStacks.setBackground(0, this.slotDrawable);
            itemStacks.set(0, recipeWrapper.getInputItem());
        } else if (recipeWrapper.hasInputFluid()) {
            fluidStacks.init(0, true, inputX, 22, 16, 16, 1000, false, null);
            fluidStacks.set(0, recipeWrapper.getInputFluid());
        }

        int outputX = 82;
        if (recipeWrapper.hasOutputFluid()) {
            fluidStacks.init(1, false, outputX, 22, 16, 16, 1000, false, null);
            fluidStacks.set(1, recipeWrapper.getOutputFluid());
        }

        List<ItemStack> outputItems = recipeWrapper.getOutputItems();
        for (int i = 0; i < outputItems.size(); i++) {
            itemStacks.init(100 + i, false, outputX + (recipeWrapper.hasOutputFluid() ? 22 : 0) + i * 18, 22);
            itemStacks.setBackground(100 + i, this.slotDrawable);
            itemStacks.set(100 + i, outputItems.get(i));
        }

        itemStacks.addTooltipCallback((slotIndex, input, ingredient, tooltip) -> {
            if (ingredient == null) {
                return;
            }
            if (!input && recipeWrapper.isOutputConsumed() && slotIndex == 100) {
                tooltip.add(HeiText.Consumed.getLocal());
            }
        });
        fluidStacks.addTooltipCallback((slotIndex, input, ingredient, tooltip) -> {
            if (ingredient == null) {
                return;
            }
            if ((input && slotIndex == 0 && recipeWrapper.isInputFlowing())
                || (!input && slotIndex == 1 && recipeWrapper.isOutputFlowing())) {
                if (!tooltip.isEmpty()) {
                    tooltip.set(0, HeiText.FlowingFluidName.getLocal(tooltip.getFirst()));
                }
            }
            if (!input && recipeWrapper.isOutputConsumed() && slotIndex == 1) {
                tooltip.add(HeiText.Consumed.getLocal());
            }
        });
    }
}
