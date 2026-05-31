package ae2.recipes.game;

import ae2.recipes.AERecipeTypes;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jspecify.annotations.Nullable;

public record CraftingUnitTransformRecipe(Block upgradedBlock, Item upgradeItem) {

    public static ItemStack getRemovedUpgrade(Block upgradedBlock) {
        for (var recipe : AERecipeTypes.CRAFTING_UNIT_TRANSFORM.getRecipes()) {
            if (recipe.upgradedBlock == upgradedBlock) {
                return new ItemStack(recipe.upgradeItem);
            }
        }
        return ItemStack.EMPTY;
    }

    public static @Nullable Block getUpgradedBlock(ItemStack upgradeItem) {
        for (var recipe : AERecipeTypes.CRAFTING_UNIT_TRANSFORM.getRecipes()) {
            if (!upgradeItem.isEmpty() && upgradeItem.getItem() == recipe.upgradeItem) {
                return recipe.upgradedBlock;
            }
        }
        return null;
    }
}
