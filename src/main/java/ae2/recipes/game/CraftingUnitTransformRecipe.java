package ae2.recipes.game;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

public record CraftingUnitTransformRecipe(Block baseBlock, Block upgradedBlock, Item upgradeItem) {
}
