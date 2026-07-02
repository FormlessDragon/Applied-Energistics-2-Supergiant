package ae2.api.cellterminal;

import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Objects;

/**
 * Immutable upgrade inventory snapshot.
 *
 * @param slots Slot-ordered upgrade stacks.
 */
public record CellTerminalUpgradeSnapshot(List<ItemStack> slots) {
    public CellTerminalUpgradeSnapshot {
        Objects.requireNonNull(slots, "slots");
        slots = slots.stream()
                     .map(stack -> Objects.requireNonNull(stack, "upgrade stack").copy())
                     .toList();
    }
}
