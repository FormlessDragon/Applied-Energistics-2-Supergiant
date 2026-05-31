package ae2.integration.modules.crafttweaker;

import ae2.recipes.AERecipeTypes;
import ae2.recipes.handlers.CrystalFixerRecipe;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.block.IBlock;
import crafttweaker.api.item.IIngredient;
import crafttweaker.api.minecraft.CraftTweakerMC;
import stanhebben.zenscript.annotations.Optional;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

@ZenRegister
@ZenClass("mods.appliedenergistics2.CrystalFixer")
public final class CrystalFixer {
    private CrystalFixer() {
    }

    @ZenMethod
    public static void addRecipe(IBlock input, IBlock output, IIngredient fuel, int fuelAmount,
                                 @Optional(valueLong = CrystalFixerRecipe.FULL_CHANCE) int chance) {
        CrystalFixerRecipe recipe = new CrystalFixerRecipe(
            CraftTweakerMC.getBlock(input),
            CraftTweakerMC.getBlock(output),
            CraftTweakerMC.getIngredient(fuel),
            fuelAmount,
            chance);
        CraftTweakerAPI.apply(AE2CraftTweakerActions.addAERecipe(AERecipeTypes.CRYSTAL_FIXER, recipe,
            "Adding AE2 crystal fixer recipe"));
    }

    @ZenMethod
    public static void remove(IBlock input, IBlock output) {
        var inputBlock = CraftTweakerMC.getBlock(input);
        var outputBlock = CraftTweakerMC.getBlock(output);
        CraftTweakerAPI.apply(AE2CraftTweakerActions.removeAERecipes(AERecipeTypes.CRYSTAL_FIXER,
            recipe -> recipe.input() == inputBlock && recipe.output() == outputBlock,
            "Removing AE2 crystal fixer recipes"));
    }
}
