package ae2.api.behaviors;

import ae2.api.stacks.AEKey;
import org.jetbrains.annotations.Nullable;

/**
 * Exposes generic AE resources for GUI-only slot display without requiring an intermediate ItemStack wrapper.
 */
public interface GenericStackDisplayInventory {

    /**
     * Returns whether the given slot currently has a generic resource to display.
     */
    boolean hasGenericDisplayStack(int slot);

    /**
     * Returns the resource key displayed in the given slot.
     */
    @Nullable
    AEKey getDisplayKey(int slot);

    /**
     * Returns the displayed resource amount for the given slot.
     */
    long getDisplayAmount(int slot);
}
