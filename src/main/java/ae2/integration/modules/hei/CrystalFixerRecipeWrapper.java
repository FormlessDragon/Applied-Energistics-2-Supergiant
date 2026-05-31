package ae2.integration.modules.hei;

import ae2.recipes.handlers.CrystalFixerRecipe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.List;

class CrystalFixerRecipeWrapper implements IRecipeWrapper {
    private static final DecimalFormat PERCENT = new DecimalFormat("#.#%", new DecimalFormatSymbols());

    private final CrystalFixerRecipe recipe;
    private final ItemStack input;
    private final ItemStack output;
    private final List<List<ItemStack>> fuelInputs;

    CrystalFixerRecipeWrapper(CrystalFixerRecipe recipe) {
        this.recipe = recipe;
        this.input = new ItemStack(recipe.input());
        this.output = new ItemStack(recipe.output());
        this.fuelInputs = buildFuelInputs(recipe);
    }

    private static List<List<ItemStack>> buildFuelInputs(CrystalFixerRecipe recipe) {
        var fuels = new ObjectArrayList<List<ItemStack>>();
        var alternatives = new ObjectArrayList<ItemStack>();
        Arrays.stream(recipe.fuel().getMatchingStacks()).forEach(stack -> {
            ItemStack copy = stack.copy();
            copy.setCount(recipe.fuelAmount());
            alternatives.add(copy);
        });
        fuels.add(alternatives);
        return fuels;
    }

    CrystalFixerRecipe getRecipe() {
        return this.recipe;
    }

    ItemStack getInput() {
        return this.input.copy();
    }

    ItemStack getOutput() {
        return this.output.copy();
    }

    List<List<ItemStack>> getFuelInputs() {
        return this.fuelInputs;
    }

    @Override
    public void getIngredients(@NonNull IIngredients ingredients) {
        var inputs = new ObjectArrayList<List<ItemStack>>();
        inputs.add(List.of(this.input));
        inputs.addAll(this.fuelInputs);
        ingredients.setInputLists(VanillaTypes.ITEM, inputs);
        ingredients.setOutput(VanillaTypes.ITEM, this.output);
    }

    @Override
    public void drawInfo(@NonNull Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        String chance = I18n.format("ae2.hei.crystal_fixer.success_chance",
            PERCENT.format(this.recipe.getChancePercent()));
        minecraft.fontRenderer.drawString(chance, 1, 2, 0x7E7E7E);
    }
}
