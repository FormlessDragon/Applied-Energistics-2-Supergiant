package ae2.recipes;

import ae2.core.AppEng;
import ae2.recipes.entropy.EntropyRecipe;
import ae2.recipes.game.CraftingUnitTransformRecipe;
import ae2.recipes.game.StorageCellDisassemblyRecipe;
import ae2.recipes.handlers.ChargerRecipe;
import ae2.recipes.handlers.CrystalAssemblerRecipe;
import ae2.recipes.handlers.CrystalFixerRecipe;
import ae2.recipes.handlers.InscriberRecipe;
import ae2.recipes.mattercannon.MatterCannonAmmo;
import ae2.recipes.transform.TransformLogic;
import ae2.recipes.transform.TransformRecipe;
import ae2.recipes.types.AERecipeType;

public final class AERecipeTypes {
    public static final AERecipeType<ChargerRecipe> CHARGER = new AERecipeType<>(AppEng.makeId("charger"));
    public static final AERecipeType<InscriberRecipe> INSCRIBER = new AERecipeType<>(AppEng.makeId("inscriber"));
    public static final AERecipeType<CrystalAssemblerRecipe> CRYSTAL_ASSEMBLER = new AERecipeType<>(
        AppEng.makeId("crystal_assembler"));
    public static final AERecipeType<CrystalFixerRecipe> CRYSTAL_FIXER = new AERecipeType<>(
        AppEng.makeId("crystal_fixer"));
    public static final AERecipeType<MatterCannonAmmo> MATTER_CANNON_AMMO = new AERecipeType<>(
        AppEng.makeId("matter_cannon"));
    public static final AERecipeType<TransformRecipe> TRANSFORM = new AERecipeType<>(AppEng.makeId("transform"));
    public static final AERecipeType<CraftingUnitTransformRecipe> CRAFTING_UNIT_TRANSFORM = new AERecipeType<>(
        AppEng.makeId("crafting_unit_transform"));
    public static final AERecipeType<EntropyRecipe> ENTROPY = new AERecipeType<>(AppEng.makeId("entropy"));

    private AERecipeTypes() {
    }

    public static void clear() {
        CHARGER.clear();
        INSCRIBER.clear();
        CRYSTAL_ASSEMBLER.clear();
        CRYSTAL_FIXER.clear();
        MATTER_CANNON_AMMO.clear();
        TRANSFORM.clear();
        TransformLogic.clearCache();
        CRAFTING_UNIT_TRANSFORM.clear();
        ENTROPY.clear();
        StorageCellDisassemblyRecipe.clear();
    }
}
