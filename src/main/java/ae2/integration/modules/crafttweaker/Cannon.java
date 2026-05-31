package ae2.integration.modules.crafttweaker;

import ae2.recipes.AERecipeTypes;
import ae2.recipes.mattercannon.MatterCannonAmmo;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IIngredient;
import crafttweaker.api.minecraft.CraftTweakerMC;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

@ZenRegister
@ZenClass("mods.appliedenergistics2.Cannon")
public final class Cannon {
    private Cannon() {
    }

    @ZenMethod
    public static void registerAmmo(IIngredient ammo, float weight) {
        MatterCannonAmmo recipe = new MatterCannonAmmo(CraftTweakerMC.getIngredient(ammo), weight);
        CraftTweakerAPI.apply(AE2CraftTweakerActions.addAERecipe(AERecipeTypes.MATTER_CANNON_AMMO, recipe,
            "Adding AE2 matter cannon ammo for " + ammo));
    }

    @ZenMethod
    public static void removeAmmo(IIngredient ammo) {
        CraftTweakerAPI.apply(AE2CraftTweakerActions.removeAERecipes(AERecipeTypes.MATTER_CANNON_AMMO,
            recipe -> AE2CraftTweakerRecipes.ingredientMatchesAny(recipe.ammo(), ammo),
            "Removing AE2 matter cannon ammo for " + ammo));
    }
}
