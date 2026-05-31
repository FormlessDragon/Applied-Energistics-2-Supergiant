package ae2.integration.modules.hei;

import ae2.api.config.CondenserOutput;
import ae2.core.localization.Tooltips;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.client.config.HoverChecker;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collections;
import java.util.List;

@ParametersAreNonnullByDefault
class CondenserOutputWrapper implements IRecipeWrapper {
    private final CondenserOutput condenserOutput;
    private final ItemStack outputItem;
    private final List<ItemStack> viableComponents;
    private final HoverChecker buttonHoverChecker;
    private final IDrawable buttonIcon;

    CondenserOutputWrapper(CondenserOutput condenserOutput, ItemStack outputItem, List<ItemStack> viableComponents,
                           IDrawable buttonIcon) {
        this.condenserOutput = condenserOutput;
        this.outputItem = outputItem;
        this.viableComponents = ImmutableList.copyOf(viableComponents);
        this.buttonIcon = buttonIcon;
        this.buttonHoverChecker = new HoverChecker(87, 103, 35, 51, 0);
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        ingredients.setInputLists(VanillaTypes.ITEM, Collections.singletonList(this.viableComponents));
        ingredients.setOutput(VanillaTypes.ITEM, this.outputItem);
    }

    @Override
    public void drawInfo(Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        this.buttonIcon.draw(minecraft, 88, 35);
    }

    @Override
    public List<String> getTooltipStrings(int mouseX, int mouseY) {
        if (!this.buttonHoverChecker.checkHover(mouseX, mouseY)) {
            return Collections.emptyList();
        }

        Tooltips key;
        switch (this.condenserOutput) {
            case MATTER_BALLS -> key = Tooltips.MatterBalls;
            case SINGULARITY -> key = Tooltips.Singularity;
            default -> {
                return Collections.emptyList();
            }
        }

        return Splitter.on("\n").splitToList(key.getLocal(this.condenserOutput.requiredPower));
    }
}
