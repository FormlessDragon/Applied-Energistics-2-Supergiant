package ae2.recipes.game;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;

public final class StorageCellDisassemblyRecipe {
    private static final Map<Item, StorageCellDisassemblyRecipe> RECIPES = new Object2ObjectLinkedOpenHashMap<>();

    private final List<ItemStack> disassemblyItems;
    private final Item storageCell;

    public StorageCellDisassemblyRecipe(Item storageCell, List<ItemStack> disassemblyItems) {
        this.storageCell = storageCell;
        this.disassemblyItems = copyStacks(disassemblyItems);
    }

    public static void clear() {
        RECIPES.clear();
    }

    public static void register(StorageCellDisassemblyRecipe recipe) {
        if (recipe.canDisassemble()) {
            RECIPES.put(recipe.storageCell, recipe);
        } else {
            RECIPES.remove(recipe.storageCell);
        }
    }

    public static List<ItemStack> getDisassemblyResult(World level, Item cell) {
        var recipe = RECIPES.get(cell);
        return recipe != null ? recipe.getCellDisassemblyItems() : List.of();
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        var result = new ObjectArrayList<ItemStack>(stacks.size());
        for (var stack : stacks) {
            result.add(stack.copy());
        }
        return result;
    }

    public Item getStorageCell() {
        return this.storageCell;
    }

    public List<ItemStack> getCellDisassemblyItems() {
        return copyStacks(this.disassemblyItems);
    }

    public boolean canDisassemble() {
        return !this.disassemblyItems.isEmpty();
    }
}
