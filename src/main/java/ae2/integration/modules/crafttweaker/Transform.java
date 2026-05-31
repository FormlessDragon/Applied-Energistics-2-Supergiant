package ae2.integration.modules.crafttweaker;

import ae2.recipes.AERecipeTypes;
import ae2.recipes.transform.TransformCircumstance;
import ae2.recipes.transform.TransformLogic;
import ae2.recipes.transform.TransformRecipe;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.IAction;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IIngredient;
import crafttweaker.api.item.IItemStack;
import crafttweaker.api.minecraft.CraftTweakerMC;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import stanhebben.zenscript.annotations.Optional;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

@ZenRegister
@ZenClass("mods.appliedenergistics2.Transform")
public final class Transform {
    private Transform() {
    }

    @ZenMethod
    public static void addRecipe(IItemStack output, IIngredient[] inputs, @Optional String circumstance) {
        var ingredients = new ObjectArrayList<Ingredient>();
        for (IIngredient input : inputs) {
            ingredients.add(CraftTweakerMC.getIngredient(input));
        }
        TransformRecipe recipe = new TransformRecipe(ingredients, CraftTweakerMC.getItemStack(output),
            readCircumstance(circumstance));
        CraftTweakerAPI.apply(new IAction() {
            @Override
            public void apply() {
                AERecipeTypes.TRANSFORM.register(recipe);
                TransformLogic.clearCache();
            }

            @Override
            public String describe() {
                return "Adding AE2 transform recipe for " + output;
            }
        });
    }

    @ZenMethod
    public static void removeByOutput(IItemStack output) {
        var stack = CraftTweakerMC.getItemStack(output);
        CraftTweakerAPI.apply(new IAction() {
            private int removed;

            @Override
            public void apply() {
                this.removed = AERecipeTypes.TRANSFORM.removeIf(
                    recipe -> AE2CraftTweakerRecipes.itemEquals(recipe.getResultItem(), stack));
                TransformLogic.clearCache();
            }

            @Override
            public String describe() {
                return "Removing AE2 transform recipes producing " + output + " (" + this.removed + " removed)";
            }
        });
    }

    private static TransformCircumstance readCircumstance(String value) {
        if (value == null || value.isEmpty() || "water".equalsIgnoreCase(value)) {
            return TransformCircumstance.water();
        }
        if ("explosion".equalsIgnoreCase(value)) {
            return TransformCircumstance.explosion();
        }
        return TransformCircumstance.fluid(new ResourceLocation(value));
    }
}
