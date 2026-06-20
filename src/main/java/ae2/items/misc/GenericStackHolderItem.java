package ae2.items.misc;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * ItemStacks that can expose a generic AE resource for client rendering and GUI logic.
 */
public interface GenericStackHolderItem {
    /**
     * Reads the resource key represented by this item stack without requiring callers to deserialize the full stack.
     *
     * @param stack The item stack to read from.
     * @return The represented resource key, or null if the stack does not hold a readable resource.
     */
    @Nullable
    AEKey unwrapWhat(ItemStack stack);

    /**
     * Reads the represented resource amount without requiring callers to deserialize the full stack.
     *
     * @param stack The item stack to read from.
     * @return The represented amount, or 0 if the stack does not hold a readable amount.
     */
    long unwrapAmount(ItemStack stack);

    /**
     * Reads the complete represented resource stack for callers that need both key and amount.
     *
     * @param stack The item stack to read from.
     * @return The represented resource stack, or null if the key cannot be read.
     */
    @Nullable
    default GenericStack getGenericStack(ItemStack stack) {
        AEKey what = unwrapWhat(stack);
        return what == null ? null : new GenericStack(what, unwrapAmount(stack));
    }
}
