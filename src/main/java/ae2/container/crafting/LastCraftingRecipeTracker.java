package ae2.container.crafting;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LastCraftingRecipeTracker {
    private static final Map<UUID, List<ItemStack>> LAST_RECIPES = new Object2ObjectOpenHashMap<>();

    private LastCraftingRecipeTracker() {
    }

    public static void remember(EntityPlayer player, List<ItemStack> inputs) {
        LAST_RECIPES.put(player.getUniqueID(), inputs.stream().map(stack -> {
            ItemStack copy = stack.copy();
            if (!copy.isEmpty()) {
                copy.setCount(1);
            }
            return copy;
        }).toList());
    }

    @Nullable
    public static List<ItemStack> get(EntityPlayer player) {
        List<ItemStack> inputs = LAST_RECIPES.get(player.getUniqueID());
        return inputs == null ? null : inputs.stream().map(ItemStack::copy).toList();
    }

    public static void clear() {
        LAST_RECIPES.clear();
    }
}
