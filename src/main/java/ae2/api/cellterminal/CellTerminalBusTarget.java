package ae2.api.cellterminal;

import ae2.api.config.AccessRestriction;
import ae2.api.config.FuzzyMode;
import ae2.api.config.StorageFilter;
import ae2.api.config.YesNo;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.util.ConfigInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Storage target that represents a storage-bus style bridge to an external inventory.
 * <p>
 * Implementations expose the bus partition inventory, its currently active capacity, and mode settings used by
 * GUI-independent server actions.
 */
public interface CellTerminalBusTarget extends CellTerminalStorageTarget {
    /**
     * Returns the current access restriction configured on this storage bus.
     *
     * @return The current access restriction.
     */
    AccessRestriction getAccessRestriction();

    /**
     * Returns the current storage filter mode configured on this storage bus.
     *
     * @return The current storage filter mode.
     */
    StorageFilter getStorageFilter();

    /**
     * Returns whether extract filtering is enabled on this storage bus.
     *
     * @return The current filter-on-extract setting.
     */
    YesNo getFilterOnExtract();

    /**
     * Returns the current fuzzy mode configured on this storage bus.
     *
     * @return The current fuzzy mode.
     */
    FuzzyMode getFuzzyMode();

    /**
     * Returns whether the bus is operating in extract-only mode.
     *
     * @return {@code true} when inserts are intentionally blocked by the bus configuration.
     */
    boolean isExtractableOnly();

    /**
     * Returns the display name of the block or inventory this storage bus is currently connected to.
     * <p>
     * GUI layers show this as auxiliary attached-inventory information while keeping the storage bus itself as the
     * target icon and primary target name.
     *
     * @return The connected inventory display name, or {@code null} when the bus is not attached.
     */
    @Nullable
    ITextComponent connectedDisplayName();

    /**
     * Returns the currently visible external storage contents seen through this storage bus.
     *
     * @return The visible bus contents snapshot.
     */
    KeyCounter getAvailableStacks();

    /**
     * Returns the current storage-bus partition/filter inventory.
     *
     * @return The current filter inventory.
     */
    ConfigInventory getConfigInventory();

    /**
     * Returns the number of partition/filter slots that the storage bus currently exposes for editing.
     * <p>
     * Storage buses may keep a larger native configuration inventory than the slots enabled by installed capacity
     * cards. Callers use this value to size partition UIs and to validate partition writes against the active bus
     * capacity.
     *
     * @return The currently active writable partition/filter slot count.
     */
    int getPartitionSlotCapacity();

    /**
     * Returns the live upgrade inventory for this storage bus.
     *
     * @return The current upgrade inventory.
     */
    IUpgradeInventory getUpgradeInventory();

    /**
     * Returns the current IO and filter settings as a single writable snapshot.
     *
     * @return The current IO/filter mode snapshot.
     */
    CellTerminalIoFilterMode getIoFilterMode();

    /**
     * Writes the IO and filter settings exposed by this storage bus.
     *
     * @param mode The new IO/filter mode snapshot.
     */
    void setIoFilterMode(CellTerminalIoFilterMode mode);

    /**
     * Returns an immutable partition/filter snapshot for this storage bus.
     *
     * @return The current bus partition snapshot.
     */
    CellTerminalPartitionSnapshot getPartitionSnapshot();

    /**
     * Returns the shape used by this storage bus partition/filter editor.
     *
     * @return The current partition editor mode.
     */
    default CellTerminalBusPartitionMode getPartitionMode() {
        return CellTerminalBusPartitionMode.SLOTS;
    }

    /**
     * Returns an immutable text partition/filter snapshot for text based storage buses.
     *
     * @return The current text partition snapshot.
     */
    default CellTerminalBusTextPartitionSnapshot getTextPartitionSnapshot() {
        return CellTerminalBusTextPartitionSnapshot.empty();
    }

    /**
     * Replaces the storage-bus partition/filter inventory.
     *
     * @param partitionSlots Slot-ordered partition entries. Empty slots are represented as {@code null}. Extra entries
     *                       beyond native capacity are rejected.
     */
    void setPartition(List<? extends @Nullable GenericStack> partitionSlots);

    /**
     * Writes a single text partition/filter expression.
     *
     * @param fieldId    Field identifier. Native Cell Terminal buses use {@code mod}, {@code odWhite}, and
     *                   {@code odBlack}.
     * @param expression New expression text.
     */
    default void setTextPartition(String fieldId, String expression) {
        throw new UnsupportedOperationException(
            "Cell Terminal target does not support text partition writes: " + stableTargetId());
    }

    /**
     * Returns an immutable upgrade snapshot for this storage bus.
     *
     * @return The current upgrade snapshot.
     */
    CellTerminalUpgradeSnapshot getUpgradeSnapshot();

    /**
     * Replaces the storage-bus upgrade inventory.
     *
     * @param upgradeStacks Slot-ordered upgrade stacks. Extra entries beyond native capacity are rejected.
     */
    void setUpgrades(List<ItemStack> upgradeStacks);
}
