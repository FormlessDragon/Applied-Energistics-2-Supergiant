package ae2.api.crafting.cpu;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Registry that drives crafting unit upgrade and downgrade rules.
 */
public interface ICraftingUnitTransformationRegistry {
    void register(Block baseBlock, Block upgradedBlock, Item upgradeItem);

    @Nullable
    Block findUpgrade(Block baseBlock, ItemStack upgradeItem);

    ItemStack getRemovedUpgrade(Block upgradedBlock);

    @Nullable
    Block getBaseBlock(Block upgradedBlock);
}
