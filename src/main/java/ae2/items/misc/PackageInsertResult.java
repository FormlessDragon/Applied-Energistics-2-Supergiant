package ae2.items.misc;

import net.minecraft.item.ItemStack;

/**
 * Result of inserting a resource package into ME storage.
 */
public record PackageInsertResult(ItemStack remainder, long insertedAmount) {
    public boolean changed() {
        return insertedAmount > 0;
    }
}
