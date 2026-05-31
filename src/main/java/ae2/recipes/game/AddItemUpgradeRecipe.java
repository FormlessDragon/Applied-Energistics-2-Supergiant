package ae2.recipes.game;

import ae2.api.upgrades.IUpgradeableItem;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.registries.IForgeRegistryEntry;

public final class AddItemUpgradeRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
    private static ItemStack attemptUpgrade(InventoryCrafting input) {
        if (input.getSizeInventory() < 2) {
            return ItemStack.EMPTY;
        }

        for (int i = 0; i < input.getSizeInventory(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (!(stack.getItem() instanceof IUpgradeableItem upgradableItem)) {
                continue;
            }

            ItemStack upgraded = stack.copy();
            var upgrades = upgradableItem.getUpgrades(upgraded);

            for (int slot = 0; slot < input.getSizeInventory(); slot++) {
                if (slot == i) {
                    continue;
                }

                ItemStack upgrade = input.getStackInSlot(slot);
                if (upgrade.isEmpty()) {
                    continue;
                }

                ItemStack singleUpgrade = upgrade.copy();
                singleUpgrade.setCount(1);
                if (!upgrades.addItems(singleUpgrade).isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }

            return upgraded;
        }

        return ItemStack.EMPTY;
    }

    @Override
    public boolean matches(InventoryCrafting inv, World worldIn) {
        return !attemptUpgrade(inv).isEmpty();
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        return attemptUpgrade(inv);
    }

    @Override
    public boolean canFit(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return ItemStack.EMPTY;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv) {
        return ForgeHooks.defaultRecipeGetRemainingItems(inv);
    }
}
