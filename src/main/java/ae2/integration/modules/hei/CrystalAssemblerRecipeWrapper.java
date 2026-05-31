package ae2.integration.modules.hei;

import ae2.recipes.handlers.CrystalAssemblerRecipe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.List;

class CrystalAssemblerRecipeWrapper implements IRecipeWrapper {
    private final CrystalAssemblerRecipe recipe;
    private final List<List<ItemStack>> itemInputs;
    private final ItemStack output;

    CrystalAssemblerRecipeWrapper(CrystalAssemblerRecipe recipe) {
        this.recipe = recipe;
        this.itemInputs = buildItemInputs(recipe);
        this.output = recipe.getResultItem();
    }

    private static List<ItemStack> getStacks(Ingredient ingredient) {
        ItemStack[] stacks = ingredient.getMatchingStacks();
        return stacks.length == 0 ? List.of(ItemStack.EMPTY) : Arrays.asList(stacks);
    }

    private static List<List<ItemStack>> buildItemInputs(CrystalAssemblerRecipe recipe) {
        var inputLists = new ObjectArrayList<List<ItemStack>>();
        for (var input : recipe.getInputs()) {
            var stacks = new ObjectArrayList<ItemStack>();
            for (ItemStack stack : getStacks(input.ingredient())) {
                ItemStack copy = stack.copy();
                copy.setCount(input.amount());
                stacks.add(copy);
            }
            inputLists.add(stacks);
        }
        return inputLists;
    }

    List<List<ItemStack>> getItemInputs() {
        return this.itemInputs;
    }

    ItemStack getOutput() {
        return this.output.copy();
    }

    @Override
    public void getIngredients(@NonNull IIngredients ingredients) {
        ingredients.setInputLists(VanillaTypes.ITEM, this.itemInputs);
        var fluid = this.recipe.getFluid();
        if (fluid != null) {
            ingredients.setInput(VanillaTypes.FLUID, fluid.toFluidStack());
        }
        ingredients.setOutput(VanillaTypes.ITEM, this.output);
    }
}
