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

    PatternContainerGroup getTerminalGroup();
}
