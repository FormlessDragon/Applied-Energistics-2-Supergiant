package ae2.crafting.pattern;

import ae2.api.stacks.AEItemKey;
import ae2.container.ContainerConstants;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AECraftingPatternCombinationTest {
    private static CoupledRecipe recipe;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
        recipe = new CoupledRecipe();
        recipe.setRegistryName("ae2_audit", "coupled_substitutions");
        ForgeRegistries.RECIPES.register(recipe);
    }

    private static AECraftingPattern pattern() {
        return pattern(true);
    }

    private static AECraftingPattern pattern(boolean allowSubstitutes) {
        var encoded = new ItemStack(Items.PAPER);
        var slots = new ItemStack[9];
        slots[0] = new ItemStack(Items.APPLE);
        slots[1] = new ItemStack(Items.POTATO);
        for (int i = 2; i < slots.length; i++) {
            slots[i] = ItemStack.EMPTY;
        }
        AECraftingPattern.encode(encoded, recipe, slots, new ItemStack(Items.CARROT), allowSubstitutes, false);
        return new AECraftingPattern(AEItemKey.of(encoded), null);
    }

    private static InventoryCrafting inventory() {
        return new InventoryCrafting(ContainerConstants.EMPTY_CONTAINER, 3, 3);
    }

    @Test
    void assembleRejectsInvalidCombinationAfterOneFullValidation() {
        var pattern = pattern();
        var actual = inventory();
        actual.setInventorySlotContents(0, new ItemStack(Items.GOLDEN_APPLE));
        actual.setInventorySlotContents(1, new ItemStack(Items.BAKED_POTATO));

        recipe.matchCalls = recipe.resultCalls = 0;
        assertTrue(pattern.assemble(actual, null).isEmpty());
        assertEquals(1, recipe.matchCalls);
        assertEquals(0, recipe.resultCalls);
    }

    @Test
    void assembleKeepsValidCombinationOutput() {
        var pattern = pattern();
        var actual = inventory();
        actual.setInventorySlotContents(0, new ItemStack(Items.GOLDEN_APPLE));
        actual.setInventorySlotContents(1, new ItemStack(Items.POTATO));

        recipe.matchCalls = recipe.resultCalls = 0;
        assertEquals(Items.CARROT, pattern.assemble(actual, null).getItem());
        assertEquals(1, recipe.matchCalls);
        assertEquals(1, recipe.resultCalls);
    }

    @Test
    void assembleRespectsDisabledSubstitution() {
        var pattern = pattern(false);
        var actual = inventory();
        actual.setInventorySlotContents(0, new ItemStack(Items.GOLDEN_APPLE));
        actual.setInventorySlotContents(1, new ItemStack(Items.POTATO));

        recipe.matchCalls = recipe.resultCalls = 0;
        assertTrue(pattern.assemble(actual, null).isEmpty());
        assertEquals(0, recipe.matchCalls);
        assertEquals(0, recipe.resultCalls);
    }

    private static final class CoupledRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IShapedRecipe {
        private int matchCalls;
        private int resultCalls;

        @Override
        public boolean matches(InventoryCrafting inv, World world) {
            matchCalls++;
            var first = inv.getStackInSlot(0).getItem();
            var second = inv.getStackInSlot(1).getItem();
            return (first == Items.APPLE || first == Items.GOLDEN_APPLE)
                && (second == Items.POTATO || second == Items.BAKED_POTATO)
                && !(first == Items.GOLDEN_APPLE && second == Items.BAKED_POTATO);
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting inv) {
            resultCalls++;
            return new ItemStack(Items.CARROT);
        }

        @Override
        public int getRecipeWidth() {
            return 2;
        }

        @Override
        public int getRecipeHeight() {
            return 1;
        }

        @Override
        public boolean canFit(int width, int height) {
            return width >= 2 && height >= 1;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return new ItemStack(Items.CARROT);
        }

        @Override
        public NonNullList<Ingredient> getIngredients() {
            return NonNullList.from(Ingredient.EMPTY,
                Ingredient.fromItems(Items.APPLE, Items.GOLDEN_APPLE),
                Ingredient.fromItems(Items.POTATO, Items.BAKED_POTATO));
        }
    }
}
