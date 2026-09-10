package ae2.container.crafting;

import ae2.api.inventories.InternalInventory;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.item.ItemStack;

/**
 * Server-side helpers for the crafting terminal's grid tweaks: rotating, balancing and filling the crafting grid.
 */
public final class CraftingGridTweaks {
    private static final int GRID_COLUMNS = 3;
    private static final int GRID_SIZE = GRID_COLUMNS * GRID_COLUMNS;

    private CraftingGridTweaks() {
    }

    public static void rotate(InternalInventory grid, boolean counterClockwise) {
        ItemStack[] rotated = new ItemStack[GRID_SIZE];
        for (int slot = 0; slot < GRID_SIZE; slot++) {
            rotated[slot] = grid.getStackInSlot(slot);
        }
        int[] ring = {0, 1, 2, 5, 8, 7, 6, 3};
        for (int sourceSlot : ring) {
            rotated[rotateRingSlot(sourceSlot, counterClockwise)] = grid.getStackInSlot(sourceSlot);
        }
        for (int slot = 0; slot < GRID_SIZE; slot++) {
            grid.setItemDirect(slot, rotated[slot]);
        }
    }

    static int rotateRingSlot(int sourceSlot, boolean counterClockwise) {
        int[] ring = {0, 1, 2, 5, 8, 7, 6, 3};
        for (int i = 0; i < ring.length; i++) {
            if (ring[i] == sourceSlot) {
                int offset = counterClockwise ? ring.length - 1 : 1;
                return ring[(i + offset) % ring.length];
            }
        }
        return sourceSlot;
    }

    public static void balance(InternalInventory grid) {
        boolean[] grouped = new boolean[GRID_SIZE];
        for (int first = 0; first < GRID_SIZE; first++) {
            ItemStack template = grid.getStackInSlot(first);
            if (template.isEmpty() || template.getMaxStackSize() <= 1 || grouped[first]) {
                continue;
            }

            IntList slots = new IntArrayList();
            slots.add(first);
            String groupKey = balanceGroupKey(template);
            for (int other = first + 1; other < GRID_SIZE; other++) {
                ItemStack stack = grid.getStackInSlot(other);
                if (!stack.isEmpty() && groupKey.equals(balanceGroupKey(stack))) {
                    slots.add(other);
                }
            }

            int total = 0;
            for (int slot : slots) {
                total += grid.getStackInSlot(slot).getCount();
            }
            int[] counts = distributeEvenly(total, slots.size());
            for (int i = 0; i < slots.size(); i++) {
                int slot = slots.getInt(i);
                ItemStack updated = grid.getStackInSlot(slot).copy();
                updated.setCount(counts[i]);
                grid.setItemDirect(slot, updated);
                grouped[slot] = true;
            }
        }
    }

    public static int[] distributeEvenly(int total, int bucketCount) {
        int[] counts = new int[Math.max(0, bucketCount)];
        if (bucketCount <= 0) {
            return counts;
        }

        int base = total / bucketCount;
        int remainder = total % bucketCount;
        for (int i = 0; i < bucketCount; i++) {
            counts[i] = base + (i < remainder ? 1 : 0);
        }
        return counts;
    }

    public static void spread(InternalInventory grid) {
        while (true) {
            int largestSlot = -1;
            int largestCount = 1;
            for (int slot = 0; slot < GRID_SIZE; slot++) {
                ItemStack stack = grid.getStackInSlot(slot);
                if (!stack.isEmpty() && stack.getCount() > largestCount) {
                    largestSlot = slot;
                    largestCount = stack.getCount();
                }
            }
            if (largestSlot == -1) {
                return;
            }

            ItemStack largest = grid.getStackInSlot(largestSlot);
            boolean ranOut = false;
            for (int slot = 0; slot < GRID_SIZE; slot++) {
                if (!grid.getStackInSlot(slot).isEmpty()) {
                    continue;
                }
                if (largest.getCount() <= 1) {
                    ranOut = true;
                    break;
                }
                ItemStack split = largest.copy();
                split.setCount(1);
                largest.shrink(1);
                grid.setItemDirect(slot, split);
            }
            if (!ranOut) {
                break;
            }
        }
        balance(grid);
    }

    private static String balanceGroupKey(ItemStack stack) {
        return stack.getItem().getTranslationKey(stack) + '@' + stack.getItemDamage();
    }

}
