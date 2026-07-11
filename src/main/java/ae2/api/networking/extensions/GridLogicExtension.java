package ae2.api.networking.extensions;

import net.minecraft.util.EnumFacing;

/**
 * Adds behavior to an AE2-owned grid logic instance without replacing its implementation.
 */
public interface GridLogicExtension {

    /**
     * Called after every extension for the logic instance has been constructed and attached to the logic.
     */
    default void initialize(GridLogicContext context) {
    }

    /**
     * Called after the machine's upgrade inventory changes.
     */
    default void onUpgradesChanged() {
    }

    /**
     * Called when an adjacent block relevant to the machine changes.
     */
    default void onNeighborChanged(EnumFacing side) {
    }
}
