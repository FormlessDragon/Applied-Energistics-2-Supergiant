package ae2.api.cellterminal;

import ae2.api.stacks.GenericStack;
import ae2.api.storage.cells.CellState;
import ae2.api.storage.cells.StorageCell;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.util.ConfigInventory;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * View and write handle for one currently known cell slot inside a storage target.
 * <p>
 * Native implementations use the owning target locator to re-resolve the live storage host before mutating the slot.
 */
public interface CellTerminalCellSlotTarget {
    /**
     * Returns the owning storage target that exposed this slot.
     *
     * @return The parent storage target.
     */
    CellTerminalStorageTarget getStorageTarget();

    /**
     * Returns whether this slot supports a cell-slot specific capability.
     * <p>
     * The default delegates to the owning storage target. Slot implementations should override this for capabilities
     * that depend on the mounted cell type, especially {@link CellTerminalCapability#SAFE_UNIQUE_TYPE_REALLOCATION}.
     *
     * @param capability The queried capability.
     * @return {@code true} when the slot supports the requested behavior.
     */
    default boolean supportsCapability(CellTerminalCapability capability) {
        return getStorageTarget().supportsCapability(capability);
    }

    /**
     * Returns the zero-based slot index inside the owning target.
     *
     * @return The slot index.
     */
    int slotIndex();

    /**
     * Returns the currently installed stack for this slot.
     * <p>
     * Callers must treat the returned stack as read-only live state owned by the target.
     *
     * @return The currently installed stack, or {@link ItemStack#EMPTY} when the slot is empty.
     */
    ItemStack getCellStack();

    /**
     * Returns whether the host currently exposes this slot as a mounted storage cell.
     *
     * @return {@code true} when the slot is mounted and readable through the host.
     */
    boolean isMounted();

    /**
     * Returns the current runtime state reported by the owning storage host for this slot.
     *
     * @return The current runtime cell state.
     */
    CellState getCellState();

    /**
     * Returns the current readable storage cell inventory for this slot.
     * <p>
     * This is the data source for future Cell Terminal content pages.
     *
     * @return The readable cell inventory, or {@code null} when the slot has no readable cell.
     */
    @Nullable
    StorageCell getCellInventory();

    /**
     * Returns the current partition inventory for this slot when the installed stack supports workbench
     * configuration.
     * <p>
     * This is the data source for future Cell Terminal partition pages.
     *
     * @return The readable partition inventory, or {@code null} when the installed stack has no partition data.
     */
    @Nullable
    ConfigInventory getConfigInventory();

    /**
     * Returns the upgrade inventory for the currently installed cell when the cell supports workbench upgrades.
     *
     * @return The live upgrade inventory, or {@code null} when the stack has no upgrade inventory.
     */
    @Nullable
    IUpgradeInventory getUpgradeInventory();

    /**
     * Returns a deterministic content preview for the currently installed cell.
     *
     * @return The current cell content snapshot.
     */
    CellTerminalContentSnapshot previewContent();

    /**
     * Returns an immutable partition snapshot for the currently installed cell.
     *
     * @return The current partition snapshot.
     */
    CellTerminalPartitionSnapshot getPartitionSnapshot();

    /**
     * Replaces the cell partition/filter inventory.
     *
     * @param partitionSlots Slot-ordered partition entries. Empty slots are represented as {@code null}. Extra entries
     *                       beyond native capacity are rejected.
     */
    void setPartition(List<? extends @Nullable GenericStack> partitionSlots);

    /**
     * Runs a caller-supplied simulation against this cell while the cell has the supplied partition/filter entries.
     * <p>
     * Implementations must restore the original partition before returning. This is used by network-tool previews and
     * executes to validate resource moves against the exact partition that would be written later, without leaving
     * temporary configuration on the target.
     *
     * @param partitionSlots Slot-ordered temporary partition entries.
     * @param simulation     Simulation callback that may read the live {@link StorageCell}; it must use simulation
     *                       storage actions only.
     * @param <T>            Simulation result type.
     * @return Callback result.
     */
    <T> T simulateWithPartition(List<? extends @Nullable GenericStack> partitionSlots,
                                PartitionSimulation<T> simulation);

    /**
     * Returns an immutable upgrade snapshot for the currently installed cell.
     *
     * @return The current upgrade snapshot.
     */
    CellTerminalUpgradeSnapshot getUpgradeSnapshot();

    /**
     * Replaces the cell upgrade inventory.
     *
     * @param upgradeStacks Slot-ordered upgrade stacks. Extra entries beyond native capacity are rejected.
     */
    void setUpgrades(List<ItemStack> upgradeStacks);

    /**
     * Inserts a cell into this slot.
     *
     * @param stack The input cell stack. The input stack is not mutated.
     * @return A mutation result containing any remainder left after insertion.
     */
    CellTerminalCellSlotMutation insertCell(ItemStack stack);

    /**
     * Ejects the current cell from this slot.
     *
     * @return A mutation result containing the ejected stack.
     */
    CellTerminalCellSlotMutation ejectCell();

    /**
     * Replaces the current cell with a new cell.
     *
     * @param stack The new cell stack. The input stack is not mutated.
     * @return A mutation result containing the ejected stack and any uninserted remainder.
     */
    CellTerminalCellSlotMutation replaceCell(ItemStack stack);

    /**
     * Callback used by {@link #simulateWithPartition(List, PartitionSimulation)}.
     *
     * @param <T> Simulation result type.
     */
    interface PartitionSimulation<T> {
        /**
         * Runs against the mounted storage cell while the temporary partition is active.
         *
         * @param cell Temporarily partitioned storage cell.
         * @return Simulation result.
         */
        T run(StorageCell cell);
    }
}
