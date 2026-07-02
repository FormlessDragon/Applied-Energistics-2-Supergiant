package ae2.api.cellterminal;

import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;

/**
 * Base description of a target exposed to Cell Terminal scanning.
 * <p>
 * Sealed hierarchy ensures type safety while enabling pattern matching (Java 17+).
 * Note: CellTerminalBusTarget extends CellTerminalStorageTarget, so it's not directly listed in permits.
 */
public sealed interface CellTerminalTarget permits
    CellTerminalStorageTarget,
    CellTerminalSubnetTarget {

    /**
     * Returns the stable target identifier for this target.
     *
     * @return The stable target identifier.
     */
    String stableTargetId();

    /**
     * Returns the display name used by GUI and logging layers.
     *
     * @return The target display name.
     */
    ITextComponent displayName();

    /**
     * Returns the icon shown next to this target in the GUI.
     * <p>
     * Storage targets use their block item. Native storage bus targets use the attached inventory or block item resolved
     * from the side the bus faces, so bus pages visually represent the external target being accessed. May be empty.
     *
     * @return The display icon, or an empty stack.
     */
    default ItemStack icon() {
        return ItemStack.EMPTY;
    }

    /**
     * Returns the immutable world locator used to re-resolve this target for future server-side write actions.
     *
     * @return The in-world locator for this target.
     */
    CellTerminalTargetLocator locator();

    /**
     * Returns whether this target supports the requested public Cell Terminal capability.
     *
     * @param capability The capability being queried by an outer UI or server action layer.
     * @return {@code true} when this target exposes the requested behavior.
     */
    boolean supportsCapability(CellTerminalCapability capability);
}
