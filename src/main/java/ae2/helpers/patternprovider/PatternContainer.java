package ae2.helpers.patternprovider;

import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.IGrid;
import ae2.api.stacks.AEItemKey;
import net.minecraft.entity.player.EntityPlayer;

import org.jetbrains.annotations.Nullable;

public interface PatternContainer {
    @Nullable
    IGrid getGrid();

    default boolean isVisibleInTerminal() {
        return true;
    }

    default boolean isAssemblerPatternContainer() {
        return false;
    }

    InternalInventory getTerminalPatternInventory();

    boolean containsPattern(AEItemKey pattern);

    default long getTerminalSortOrder() {
        return 0;
    }

    default void openTerminalPatternContainerGui(EntityPlayer player) {
    }

    default boolean canEditTerminalName() {
        return false;
    }

    default void setTerminalCustomName(@Nullable String name) {
    }

    default boolean canModifyTerminalVisibility() {
        return false;
    }

    default void setTerminalVisibility(boolean visible) {
    }

    /**
     * Whether this container's pattern slots are read-only in the pattern access and pattern encoding terminals.
     * <p>
     * Read-only containers reject pattern insertion, extraction, swapping, and quick-moving on the server side. The
     * flag is also sent to clients so the pattern access terminal disables slot interaction for the provider. A
     * container that is both read-only and has an empty terminal pattern inventory is hidden from the pattern access
     * terminal regardless of its configured display mode.
     *
     * @return {@code true} if players may not modify this container's pattern slots
     */
    default boolean isReadOnly() {
        return false;
    }

    PatternContainerGroup getTerminalGroup();
}
