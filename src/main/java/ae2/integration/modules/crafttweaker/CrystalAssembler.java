package ae2.integration.modules.crafttweaker;

import ae2.recipes.AERecipeTypes;
import ae2.recipes.handlers.CrystalAssemblerRecipe;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IIngredient;
import crafttweaker.api.item.IItemStack;
import crafttweaker.api.liquid.ILiquidStack;
import crafttweaker.api.minecraft.CraftTweakerMC;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import stanhebben.zenscript.annotations.Optional;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

@ZenRegister
@ZenClass("mods.appliedenergistics2.CrystalAssembler")
public final class CrystalAssembler {
    private CrystalAssembler() {
    }

    @ZenMethod
    public static void addRecipe(IItemStack output, IIngredient[] itemInputs, int[] itemAmounts,
                                 @Optional ILiquidStack fluidInput) {
        var inputs = new ObjectArrayList<CrystalAssemblerRecipe.SizedIngredient>();
        for (int i = 0; i < itemInputs.length; i++) {
            int amount = itemAmounts != null && i < itemAmounts.length && itemAmounts[i] > 0 ? itemAmounts[i] : 1;
            inputs.add(AE2CraftTweakerRecipes.sizedIngredient(itemInputs[i], amount));
        }
        CrystalAssemblerRecipe recipe = new CrystalAssemblerRecipe(inputs, AE2CraftTweakerRecipes.sizedFluid(fluidInput),
            CraftTweakerMC.getItemStack(output));
        CraftTweakerAPI.apply(AE2CraftTweakerActions.addAERecipe(AERecipeTypes.CRYSTAL_ASSEMBLER, recipe,
            "Adding AE2 crystal assembler recipe for " + output));
    }

    @ZenMethod
    public static void removeByOutput(IItemStack output) {
        var stack = CraftTweakerMC.getItemStack(output);
        CraftTweakerAPI.apply(AE2CraftTweakerActions.removeAERecipes(AERecipeTypes.CRYSTAL_ASSEMBLER,
            recipe -> AE2CraftTweakerRecipes.itemEquals(recipe.getResultItem(), stack),
            "Removing AE2 crystal assembler recipes producing " + output));
    }
}
