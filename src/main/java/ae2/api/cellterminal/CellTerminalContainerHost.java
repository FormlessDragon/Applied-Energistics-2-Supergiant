package ae2.api.cellterminal;

import ae2.api.inventories.InternalInventory;
import ae2.api.networking.IGridNode;
import ae2.api.storage.ILinkStatus;
import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.Nullable;

/**
 * Shared host contract used by the first Cell Terminal consumer layer.
 * <p>
 * The container needs one stable access point for the current grid node and link state so wired parts and wireless GUI
 * hosts can participate in the same read-only scan flow.
 */
public interface CellTerminalContainerHost {
    /**
     * Returns the grid node currently backing Cell Terminal reads.
     * <p>
     * The container uses this node as the entry point for server-side storage and storage-bus scans.
     *
     * @return The active grid node candidate, or {@code null} when the host currently has no reachable node.
     */
    @Nullable
    IGridNode getGridNode();

    /**
     * Returns the temporary cell inventory owned by this host.
     * <p>
     * Cell Terminal uses this inventory as the persistent backing store for the read-only Temp Cells view and for the
     * synchronized container slots that mirror it to the client.
     *
     * @return The host-owned temporary cell inventory.
     */
    InternalInventory getTempCellStorage();

    /**
     * Loads the persisted subnet metadata visible to this host.
     * <p>
     * Implementations may store this data on a part stack, terminal item NBT or a future world/player preference
     * backend. The container treats the returned ledger as owned by the current GUI session.
     *
     * @return Serialized subnet metadata for the current host.
     */
    default NBTTagCompound loadCellTerminalSubnetLedgerTag() {
        return new NBTTagCompound();
    }

    /**
     * Persists subnet metadata after a GUI action mutates it.
     *
     * @param tag Serialized subnet metadata to store.
     */
    default void saveCellTerminalSubnetLedgerTag(NBTTagCompound tag) {
    }

    /**
     * Returns the current player-facing link status.
     * <p>
     * Wired hosts usually mirror their managed node state while wireless hosts provide richer diagnostics such as power,
     * range and singularity issues.
     *
     * @return The current link status shown by the client GUI.
     */
    default ILinkStatus getLinkStatus() {
        IGridNode node = getGridNode();
        return node != null && node.isOnline() ? ILinkStatus.ofConnected() : ILinkStatus.ofDisconnected();
    }
}
