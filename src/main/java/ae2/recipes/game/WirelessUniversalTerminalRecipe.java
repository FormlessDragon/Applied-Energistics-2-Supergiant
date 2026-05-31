package ae2.recipes.game;

import ae2.items.tools.powered.WirelessTerminalItem;
import ae2.items.tools.powered.WirelessTerminalRegistry;
import ae2.items.tools.powered.WirelessUniversalTerminalItem;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.registries.IForgeRegistryEntry;

import java.util.LinkedHashSet;

public final class WirelessUniversalTerminalRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {

    private final WirelessUniversalTerminalItem universalTerminal;

    public WirelessUniversalTerminalRecipe(WirelessUniversalTerminalItem universalTerminal) {
        this.universalTerminal = universalTerminal;
    }

    @Override
    public boolean matches(InventoryCrafting inv, World worldIn) {
        return !getCraftingResult(inv).isEmpty();
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        ItemStack universal = ItemStack.EMPTY;
        boolean hasTerminal = false;
        var terminals = new LinkedHashSet<WirelessTerminalItem>();
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }

            if (stack.getItem() == this.universalTerminal) {
                if (!universal.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                universal = stack.copy();
                universal.setCount(1);
                continue;
            }

            if (!(stack.getItem() instanceof WirelessTerminalItem terminal)
                || stack.getItem() instanceof WirelessUniversalTerminalItem
                || WirelessTerminalRegistry.ofItem(terminal) == null
                || this.universalTerminal.hasTerminal(universal, terminal)) {
                return ItemStack.EMPTY;
            }
            hasTerminal = true;
            if (!terminals.add(terminal)) {
                return ItemStack.EMPTY;
            }
        }

        if (!hasTerminal) {
            return ItemStack.EMPTY;
        }

        if (universal.isEmpty()) {
            universal = new ItemStack(this.universalTerminal);
        }

        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof WirelessTerminalItem terminal
                && WirelessTerminalRegistry.ofItem(terminal) != null) {
                this.universalTerminal.addTerminal(universal, stack, terminal);
            }
        }

        return universal;
    }

    @Override
    public boolean canFit(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return new ItemStack(this.universalTerminal);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv) {
        return ForgeHooks.defaultRecipeGetRemainingItems(inv);
    }
}
