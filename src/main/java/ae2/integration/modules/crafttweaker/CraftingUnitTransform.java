package ae2.integration.modules.crafttweaker;

import ae2.recipes.AERecipeTypes;
import ae2.recipes.game.CraftingUnitTransformRecipe;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.block.IBlock;
import crafttweaker.api.item.IItemStack;
import crafttweaker.api.minecraft.CraftTweakerMC;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

@ZenRegister
@ZenClass("mods.appliedenergistics2.CraftingUnitTransform")
public final class CraftingUnitTransform {
    private CraftingUnitTransform() {
    }

    @ZenMethod
    public static void addRecipe(IBlock upgradedBlock, IItemStack upgradeItem) {
        CraftingUnitTransformRecipe recipe = new CraftingUnitTransformRecipe(CraftTweakerMC.getBlock(upgradedBlock),
            CraftTweakerMC.getItemStack(upgradeItem).getItem());
        CraftTweakerAPI.apply(AE2CraftTweakerActions.addAERecipe(AERecipeTypes.CRAFTING_UNIT_TRANSFORM, recipe,
            "Adding AE2 crafting unit transform recipe"));
    }

    @ZenMethod
    public static void remove(IBlock upgradedBlock) {
        var block = CraftTweakerMC.getBlock(upgradedBlock);
        CraftTweakerAPI.apply(AE2CraftTweakerActions.removeAERecipes(AERecipeTypes.CRAFTING_UNIT_TRANSFORM,
            recipe -> recipe.upgradedBlock() == block,
            "Removing AE2 crafting unit transform recipes"));
    }
}
