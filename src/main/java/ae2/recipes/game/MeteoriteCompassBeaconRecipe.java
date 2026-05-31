package ae2.recipes.game;

import ae2.core.definitions.AEItems;
import ae2.items.misc.MeteoriteCompassItem;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.registries.IForgeRegistryEntry;

public final class MeteoriteCompassBeaconRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
    private static ItemStack getOutput(InventoryCrafting inv) {
        ItemStack compass = ItemStack.EMPTY;
        boolean hasChargedCertus = false;

        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }

            if (AEItems.METEORITE_COMPASS.is(stack)) {
                if (!compass.isEmpty() || MeteoriteCompassItem.hasBeacon(stack)) {
                    return ItemStack.EMPTY;
                }
                compass = stack;
                continue;
            }

            if (AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED.is(stack)) {
                if (hasChargedCertus) {
                    return ItemStack.EMPTY;
                }
                hasChargedCertus = true;
                continue;
            }

            return ItemStack.EMPTY;
        }

        if (compass.isEmpty() || !hasChargedCertus) {
            return ItemStack.EMPTY;
        }

        return MeteoriteCompassItem.createBeaconCompass(compass);
    }

    @Override
    public boolean matches(InventoryCrafting inv, World worldIn) {
        return !getOutput(inv).isEmpty();
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        return getOutput(inv);
    }

    @Override
    public boolean canFit(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return MeteoriteCompassItem.createBeaconCompass();
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv) {
        return ForgeHooks.defaultRecipeGetRemainingItems(inv);
    }
}
