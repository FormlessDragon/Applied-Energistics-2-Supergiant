package ae2.api.cellterminal;

import net.minecraft.item.ItemStack;

import java.util.Objects;

/**
 * Result of a server-side storage cell slot write.
 *
 * @param changedStack   The stack that was removed from the target, or {@link ItemStack#EMPTY} when no stack was
 *                       removed.
 * @param remainderStack The stack that could not be inserted, or {@link ItemStack#EMPTY} when the operation consumed
 *                       the entire input stack.
 */
public record CellTerminalCellSlotMutation(ItemStack changedStack, ItemStack remainderStack) {
    public CellTerminalCellSlotMutation {
        Objects.requireNonNull(changedStack, "changedStack");
        Objects.requireNonNull(remainderStack, "remainderStack");
    }
}
