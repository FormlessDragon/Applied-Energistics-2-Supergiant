package ae2.recipes.game;

import ae2.api.upgrades.IUpgradeableItem;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.jetbrains.annotations.Nullable;

public final class RemoveItemUpgradeRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
    private static @Nullable RemovalResult attemptRemoval(InventoryCrafting inv) {
        ItemStack found = ItemStack.EMPTY;
        int foundSlot = -1;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (!found.isEmpty()) {
                return null;
            }
            found = stack;
            foundSlot = i;
        }

        if (found.isEmpty() || !(found.getItem() instanceof IUpgradeableItem upgradableItem)) {
            return null;
        }

        ItemStack upgraded = found.copy();
        var upgrades = upgradableItem.getUpgrades(upgraded);
        for (int i = 0; i < upgrades.size(); i++) {
            ItemStack upgrade = upgrades.extractItem(i, 1, false);
            if (!upgrade.isEmpty()) {
                return new RemovalResult(upgraded, upgrade, foundSlot);
            }
        }

        return null;
    }

    @Override
    public boolean matches(InventoryCrafting inv, World worldIn) {
        return attemptRemoval(inv) != null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        RemovalResult result = attemptRemoval(inv);
        return result != null ? result.upgrade : ItemStack.EMPTY;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv) {
        RemovalResult result = attemptRemoval(inv);
        if (result == null) {
            return ForgeHooks.defaultRecipeGetRemainingItems(inv);
        }

        NonNullList<ItemStack> remaining = NonNullList.withSize(inv.getSizeInventory(), ItemStack.EMPTY);
        remaining.set(result.slot, result.upgradedItem);
        return remaining;
    }

    @Override
    public boolean canFit(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return ItemStack.EMPTY;
    }

    private record RemovalResult(ItemStack upgradedItem, ItemStack upgrade, int slot) {
    }
}
