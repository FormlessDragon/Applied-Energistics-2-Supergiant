package ae2.integration.modules.crafttweaker;

import ae2.recipes.AERecipeTypes;
import ae2.recipes.entropy.EntropyMode;
import ae2.recipes.entropy.EntropyRecipe;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.block.IBlock;
import crafttweaker.api.liquid.ILiquidStack;
import crafttweaker.api.minecraft.CraftTweakerMC;
import net.minecraftforge.fluids.FluidStack;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ZenRegister
@ZenClass("mods.appliedenergistics2.Entropy")
public final class Entropy {
    private Entropy() {
    }

    @ZenMethod
    public static void addBlockRecipe(String mode, IBlock input, IBlock output) {
        EntropyRecipe recipe = new EntropyRecipe(EntropyMode.fromString(mode),
            new EntropyRecipe.Input(Optional.of(new EntropyRecipe.BlockInput(CraftTweakerMC.getBlock(input),
                Collections.emptyMap())), Optional.empty()),
            new EntropyRecipe.Output(Optional.of(new EntropyRecipe.BlockOutput(CraftTweakerMC.getBlock(output), -1,
                false, Collections.emptyMap())), Optional.empty(), List.of()));
        CraftTweakerAPI.apply(AE2CraftTweakerActions.addAERecipe(AERecipeTypes.ENTROPY, recipe,
            "Adding AE2 entropy block recipe"));
    }

    @ZenMethod
    public static void addFluidRecipe(String mode, ILiquidStack input, ILiquidStack output) {
        FluidStack inputStack = CraftTweakerMC.getLiquidStack(input);
        FluidStack outputStack = CraftTweakerMC.getLiquidStack(output);
        EntropyRecipe recipe = new EntropyRecipe(EntropyMode.fromString(mode),
            new EntropyRecipe.Input(Optional.empty(), Optional.of(new EntropyRecipe.FluidInput(inputStack.getFluid(),
                Collections.emptyMap()))),
            new EntropyRecipe.Output(Optional.empty(), Optional.of(new EntropyRecipe.FluidOutput(outputStack.getFluid(),
                false, Collections.emptyMap())), List.of()));
        CraftTweakerAPI.apply(AE2CraftTweakerActions.addAERecipe(AERecipeTypes.ENTROPY, recipe,
            "Adding AE2 entropy fluid recipe"));
    }

    @ZenMethod
    public static void removeBlock(String mode, IBlock input) {
        EntropyMode entropyMode = EntropyMode.fromString(mode);
        var block = CraftTweakerMC.getBlock(input);
        CraftTweakerAPI.apply(AE2CraftTweakerActions.removeAERecipes(AERecipeTypes.ENTROPY,
            recipe -> recipe.mode() == entropyMode
                && recipe.input().block().isPresent()
                && recipe.input().block().get().block() == block,
            "Removing AE2 entropy block recipes"));
    }

    @ZenMethod
    public static void removeFluid(String mode, ILiquidStack input) {
        EntropyMode entropyMode = EntropyMode.fromString(mode);
        FluidStack stack = CraftTweakerMC.getLiquidStack(input);
        CraftTweakerAPI.apply(AE2CraftTweakerActions.removeAERecipes(AERecipeTypes.ENTROPY,
            recipe -> recipe.mode() == entropyMode
                && recipe.input().fluid().isPresent()
                && recipe.input().fluid().get().fluid() == stack.getFluid(),
            "Removing AE2 entropy fluid recipes"));
    }
}
