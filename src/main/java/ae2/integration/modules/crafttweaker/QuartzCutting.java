package ae2.integration.modules.crafttweaker;

import ae2.recipes.quartzcutting.QuartzCuttingRecipe;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IIngredient;
import crafttweaker.api.item.IItemStack;
import crafttweaker.api.minecraft.CraftTweakerMC;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

@ZenRegister
@ZenClass("mods.appliedenergistics2.QuartzCutting")
public final class QuartzCutting {
    private QuartzCutting() {
    }

    @ZenMethod
    public static void addRecipe(String id, IItemStack output, IIngredient[] inputs) {
        Ingredient[] ingredients = new Ingredient[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            ingredients[i] = CraftTweakerMC.getIngredient(inputs[i]);
        }
        var recipe = new QuartzCuttingRecipe(CraftTweakerMC.getItemStack(output),
            NonNullList.from(Ingredient.EMPTY, ingredients));
        CraftTweakerAPI.apply(AE2CraftTweakerActions.registerForgeRecipe(recipe,
            AE2CraftTweakerRecipes.safeRecipeId("quartz_cutting", id),
            "Adding AE2 quartz cutting recipe"));
    }
}
