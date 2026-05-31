package ae2.integration.modules.crafttweaker;

import ae2.api.stacks.AEFluidKey;
import ae2.core.Tags;
import ae2.recipes.handlers.CrystalAssemblerRecipe;
import crafttweaker.api.item.IIngredient;
import crafttweaker.api.item.IItemStack;
import crafttweaker.api.liquid.ILiquidStack;
import crafttweaker.api.minecraft.CraftTweakerMC;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

final class AE2CraftTweakerRecipes {
    private AE2CraftTweakerRecipes() {
    }

    static CrystalAssemblerRecipe.SizedIngredient sizedIngredient(IIngredient ingredient, int amount) {
        return new CrystalAssemblerRecipe.SizedIngredient(CraftTweakerMC.getIngredient(ingredient), amount);
    }

    static CrystalAssemblerRecipe.SizedFluidIngredient sizedFluid(ILiquidStack liquid) {
        FluidStack fluidStack = CraftTweakerMC.getLiquidStack(liquid);
        if (fluidStack == null) {
            return null;
        }
        AEFluidKey key = AEFluidKey.of(fluidStack);
        if (key == null) {
            return null;
        }
        return new CrystalAssemblerRecipe.SizedFluidIngredient(key, fluidStack.amount);
    }

    static Ingredient optionalIngredient(IIngredient ingredient) {
        return ingredient == null ? Ingredient.EMPTY : CraftTweakerMC.getIngredient(ingredient);
    }

    static boolean itemEquals(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty() && a.isItemEqual(b);
    }

    static ResourceLocation safeRecipeId(String category, String id) {
        String safe = id == null ? "recipe" : id.toLowerCase().replaceAll("[^a-z0-9_/.-]", "_");
        return new ResourceLocation(Tags.MOD_ID, "crt/" + category + "/" + safe);
    }

    static Item item(IItemStack stack) {
        return CraftTweakerMC.getItemStack(stack).getItem();
    }

    static Block block(IItemStack stack) {
        Item item = CraftTweakerMC.getItemStack(stack).getItem();
        return item instanceof ItemBlock itemBlock ? itemBlock.getBlock() : null;
    }

    static boolean ingredientMatchesAny(Ingredient recipeIngredient, IIngredient input) {
        if (input == null || input.getItems() == null) {
            return false;
        }
        ItemStack[] examples = CraftTweakerMC.getExamples(input);
        for (ItemStack example : examples) {
            if (recipeIngredient.apply(example)) {
                return true;
            }
        }
        return false;
    }
}
