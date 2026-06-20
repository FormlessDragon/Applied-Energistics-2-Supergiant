package ae2.core.registries;

import ae2.api.crafting.cpu.ICraftingUnitTransformationRegistry;
import ae2.core.AELog;
import ae2.core.definitions.AEBlocks;
import ae2.recipes.AERecipeTypes;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public final class CraftingUnitTransformationRegistry implements ICraftingUnitTransformationRegistry {
    private static final CraftingUnitTransformationRegistry INSTANCE = new CraftingUnitTransformationRegistry();

    private final Map<Block, Map<Item, Block>> upgradesByBase = new Object2ObjectLinkedOpenHashMap<>();
    private final Map<Block, Item> removedUpgradesByBlock = new Object2ObjectLinkedOpenHashMap<>();
    private final Map<Block, Block> baseBlocksByUpgrade = new Object2ObjectLinkedOpenHashMap<>();
    private boolean initializedFromRecipes;

    private CraftingUnitTransformationRegistry() {
    }

    public static CraftingUnitTransformationRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized void initFromRecipes() {
        if (this.initializedFromRecipes) {
            return;
        }
        this.initializedFromRecipes = true;
        for (var recipe : AERecipeTypes.CRAFTING_UNIT_TRANSFORM.getRecipes()) {
            this.register(recipe.baseBlock(), recipe.upgradedBlock(), recipe.upgradeItem());
        }
    }

    @Override
    public synchronized void register(Block baseBlock, Block upgradedBlock, Item upgradeItem) {
        Objects.requireNonNull(baseBlock, "baseBlock");
        Objects.requireNonNull(upgradedBlock, "upgradedBlock");
        Objects.requireNonNull(upgradeItem, "upgradeItem");
        this.upgradesByBase.computeIfAbsent(baseBlock, ignored -> new Object2ObjectLinkedOpenHashMap<>())
                           .put(upgradeItem, upgradedBlock);
        Item previousItem = this.removedUpgradesByBlock.put(upgradedBlock, upgradeItem);
        Block previousBase = this.baseBlocksByUpgrade.put(upgradedBlock, baseBlock);
        if ((previousItem != null && previousItem != upgradeItem) || (previousBase != null && previousBase != baseBlock)) {
            AELog.warn("Overwriting crafting unit transformation for block %s", upgradedBlock.getRegistryName());
        }
    }

    @Override
    public synchronized @Nullable Block findUpgrade(Block baseBlock, ItemStack upgradeItem) {
        if (upgradeItem.isEmpty()) {
            return null;
        }
        Map<Item, Block> upgrades = this.upgradesByBase.get(baseBlock);
        return upgrades != null ? upgrades.get(upgradeItem.getItem()) : null;
    }

    @Override
    public synchronized ItemStack getRemovedUpgrade(Block upgradedBlock) {
        Item item = this.removedUpgradesByBlock.get(upgradedBlock);
        return item != null ? new ItemStack(item) : ItemStack.EMPTY;
    }

    @Override
    public synchronized @Nullable Block getBaseBlock(Block upgradedBlock) {
        return this.baseBlocksByUpgrade.getOrDefault(upgradedBlock, AEBlocks.CRAFTING_UNIT.block());
    }
}
