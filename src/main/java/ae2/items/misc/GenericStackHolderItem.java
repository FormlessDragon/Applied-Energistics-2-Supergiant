package ae2.items.misc;

import ae2.api.stacks.GenericStack;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * ItemStacks that can expose a generic AE resource for client rendering and GUI logic.
 */
public interface GenericStackHolderItem {
    @Nullable
    GenericStack getGenericStack(ItemStack stack);
}
