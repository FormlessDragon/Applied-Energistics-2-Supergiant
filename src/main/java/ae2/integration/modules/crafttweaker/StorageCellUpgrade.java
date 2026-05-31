package ae2.integration.modules.crafttweaker;

import ae2.recipes.game.StorageCellUpgradeRecipe;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IItemStack;
import crafttweaker.api.minecraft.CraftTweakerMC;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

@ZenRegister
@ZenClass("mods.appliedenergistics2.StorageCellUpgrade")
public final class StorageCellUpgrade {
    private StorageCellUpgrade() {
    }

    @ZenMethod
    public static void addRecipe(String id, IItemStack inputCell, IItemStack inputComponent, IItemStack resultCell,
                                 IItemStack resultComponent) {
        var recipe = new StorageCellUpgradeRecipe(
            CraftTweakerMC.getItemStack(inputCell).getItem(),
            CraftTweakerMC.getItemStack(inputComponent).getItem(),
            CraftTweakerMC.getItemStack(resultCell).getItem(),
            CraftTweakerMC.getItemStack(resultComponent).getItem());
        CraftTweakerAPI.apply(AE2CraftTweakerActions.registerForgeRecipe(recipe,
            AE2CraftTweakerRecipes.safeRecipeId("storage_cell_upgrade", id),
            "Adding AE2 storage cell upgrade recipe"));
    }
}
