package ae2.integration.modules.crafttweaker;

import ae2.recipes.AERecipeTypes;
import ae2.recipes.handlers.ChargerRecipe;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IIngredient;
import crafttweaker.api.item.IItemStack;
import crafttweaker.api.minecraft.CraftTweakerMC;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

@ZenRegister
@ZenClass("mods.appliedenergistics2.Charger")
public final class Charger {
    private Charger() {
    }

    @ZenMethod
    public static void addRecipe(IIngredient input, IItemStack output) {
        ChargerRecipe recipe = new ChargerRecipe(CraftTweakerMC.getIngredient(input), CraftTweakerMC.getItemStack(output));
        CraftTweakerAPI.apply(AE2CraftTweakerActions.addAERecipe(AERecipeTypes.CHARGER, recipe,
            "Adding AE2 charger recipe for " + output));
    }

    @ZenMethod
    public static void removeByInput(IIngredient input) {
        CraftTweakerAPI.apply(AE2CraftTweakerActions.removeAERecipes(AERecipeTypes.CHARGER,
            recipe -> AE2CraftTweakerRecipes.ingredientMatchesAny(recipe.getIngredient(), input),
            "Removing AE2 charger recipes for " + input));
    }

    @ZenMethod
    public static void removeByOutput(IItemStack output) {
        var stack = CraftTweakerMC.getItemStack(output);
        CraftTweakerAPI.apply(AE2CraftTweakerActions.removeAERecipes(AERecipeTypes.CHARGER,
            recipe -> AE2CraftTweakerRecipes.itemEquals(recipe.getResultItem(), stack),
            "Removing AE2 charger recipes producing " + output));
    }
}
