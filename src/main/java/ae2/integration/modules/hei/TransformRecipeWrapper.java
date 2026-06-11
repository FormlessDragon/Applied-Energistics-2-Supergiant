package ae2.integration.modules.hei;

import ae2.api.client.AEKeyRendering;
import ae2.api.stacks.AEFluidKey;
import ae2.recipes.transform.TransformCircumstance;
import ae2.recipes.transform.TransformRecipe;
import ae2.util.EmptyArrays;
import com.google.common.base.Splitter;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@ParametersAreNonnullByDefault
class TransformRecipeWrapper implements IRecipeWrapper {
    private final TransformRecipe recipe;
    private final List<List<ItemStack>> inputs;
    private final List<ItemStack> outputs;
    private String titleText = "";
    private int titleX;
    private int fluidX;
    private int fluidY;

    TransformRecipeWrapper(TransformRecipe recipe) {
        this.recipe = recipe;
        ObjectList<List<ItemStack>> inputLists = new ObjectArrayList<>();
        for (var ingredient : this.recipe.getIngredients()) {
            ItemStack[] stacks = getMatchingStacks(ingredient);
            inputLists.add(stacks.length == 0 ? ObjectLists.singleton(ItemStack.EMPTY) : Arrays.asList(stacks));
        }
        this.inputs = ObjectLists.unmodifiable(inputLists);
        this.outputs = ObjectLists.singleton(this.recipe.getResultItem());
    }

    private static ItemStack[] getMatchingStacks(Ingredient ingredient) {
        if (ingredient == null || ingredient == Ingredient.EMPTY) {
            return EmptyArrays.EMPTY_ITEM_STACK_ARRAY;
        }

        ItemStack[] stacks = ingredient.getMatchingStacks();
        return stacks == null ? EmptyArrays.EMPTY_ITEM_STACK_ARRAY : stacks;
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        ingredients.setInputLists(VanillaTypes.ITEM, this.inputs);
        ingredients.setOutput(VanillaTypes.ITEM, this.recipe.getResultItem());
    }

    @Override
    public void drawInfo(Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        minecraft.fontRenderer.drawString(this.titleText, this.titleX, 15, 0x7E7E7E);
        Fluid fluid = this.recipe.getCircumstance().getFluidForRendering();
        if (fluid != null && this.fluidX > 0) {
            FluidBlockDrawable.draw(fluid, this.fluidX, this.fluidY);
        }
    }

    @Override
    public List<String> getTooltipStrings(int mouseX, int mouseY) {
        Fluid fluid = this.recipe.getCircumstance().getFluidForRendering();
        if (fluid == null || !isHoveringFluid(mouseX, mouseY)) {
            return Collections.emptyList();
        }

        AEFluidKey fluidKey = AEFluidKey.of(new FluidStack(fluid, Fluid.BUCKET_VOLUME));
        if (fluidKey == null) {
            return Collections.emptyList();
        }

        List<String> tooltip = new ObjectArrayList<>();
        for (ITextComponent line : AEKeyRendering.getTooltip(fluidKey)) {
            tooltip.addAll(Splitter.on('\n').splitToList(line.getFormattedText()));
        }
        return tooltip;
    }

    void setTitleDrawData(int titleX, String titleText) {
        this.titleX = titleX;
        this.titleText = titleText;
    }

    void setFluidDrawData(int fluidX, int fluidY) {
        this.fluidX = fluidX;
        this.fluidY = fluidY;
    }

    TransformCircumstance getCircumstance() {
        return this.recipe.getCircumstance();
    }

    List<List<ItemStack>> getInputs() {
        return this.inputs;
    }

    List<ItemStack> getOutputs() {
        return this.outputs;
    }

    private boolean isHoveringFluid(int mouseX, int mouseY) {
        return this.fluidX > 0
            && mouseX >= this.fluidX
            && mouseX < this.fluidX + 16
            && mouseY >= this.fluidY
            && mouseY < this.fluidY + 16;
    }
}
