package ae2.integration.modules.crafttweaker;

import ae2.recipes.AERecipeTypes;
import ae2.recipes.handlers.InscriberProcessType;
import ae2.recipes.handlers.InscriberRecipe;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IIngredient;
import crafttweaker.api.item.IItemStack;
import crafttweaker.api.minecraft.CraftTweakerMC;
import net.minecraft.item.crafting.Ingredient;
import stanhebben.zenscript.annotations.Optional;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

@ZenRegister
@ZenClass("mods.appliedenergistics2.Inscriber")
public final class Inscriber {
    private Inscriber() {
    }

    @ZenMethod
    public static void addRecipe(IItemStack output, IIngredient middle, boolean inscribe,
                                 @Optional IIngredient top, @Optional IIngredient bottom) {
        InscriberRecipe recipe = new InscriberRecipe(
            CraftTweakerMC.getIngredient(middle),
            CraftTweakerMC.getItemStack(output),
            AE2CraftTweakerRecipes.optionalIngredient(top),
            AE2CraftTweakerRecipes.optionalIngredient(bottom),
            inscribe ? InscriberProcessType.INSCRIBE : InscriberProcessType.PRESS);
        CraftTweakerAPI.apply(AE2CraftTweakerActions.addAERecipe(AERecipeTypes.INSCRIBER, recipe,
            "Adding AE2 inscriber recipe for " + output));
    }

    @ZenMethod
    public static void removeByOutput(IItemStack output) {
        var stack = CraftTweakerMC.getItemStack(output);
        CraftTweakerAPI.apply(AE2CraftTweakerActions.removeAERecipes(AERecipeTypes.INSCRIBER,
            recipe -> AE2CraftTweakerRecipes.itemEquals(recipe.getResultItem(), stack),
            "Removing AE2 inscriber recipes producing " + output));
    }

    @ZenMethod
    public static void removeByInputs(IIngredient middle, @Optional IIngredient top, @Optional IIngredient bottom) {
        Ingredient topIngredient = AE2CraftTweakerRecipes.optionalIngredient(top);
        Ingredient bottomIngredient = AE2CraftTweakerRecipes.optionalIngredient(bottom);
        CraftTweakerAPI.apply(AE2CraftTweakerActions.removeAERecipes(AERecipeTypes.INSCRIBER,
            recipe -> AE2CraftTweakerRecipes.ingredientMatchesAny(recipe.getMiddleInput(), middle)
                && optionalIngredientsEqual(recipe.getTopOptional(), topIngredient)
                && optionalIngredientsEqual(recipe.getBottomOptional(), bottomIngredient),
            "Removing AE2 inscriber recipes for " + middle));
    }

    private static boolean optionalIngredientsEqual(Ingredient a, Ingredient b) {
        if (a == Ingredient.EMPTY || b == Ingredient.EMPTY) {
            return a == b;
        }
        for (var stack : b.getMatchingStacks()) {
            if (a.apply(stack)) {
                return true;
            }
        }
        return false;
    }
}
