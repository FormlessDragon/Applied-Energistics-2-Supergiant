package ae2.api.inventories;

/**
 * Optional change version for stable internal inventory views. Consumers may skip all slot reads when inventory
 * identity, size and version are unchanged. This avoids repeated comparisons of large pattern inventories.
 *
 * <p>The owner must advance the version for every real change to visible slot contents, counts, NBT or slot
 * visibility, including changes made through another facade. Simulation never advances it. Returned stacks must
 * not be mutated without notifying their inventory owner. Reads and mutations follow the inventory's server-thread
 * lifecycle; this interface does not make inventory access thread-safe.</p>
 */
public interface VersionedInternalInventory extends InternalInventory {
    /**
     * Returns a monotonically increasing content revision for this inventory object's lifetime. Reading it must
     * neither enumerate slots nor modify state. A replacement inventory object starts a separate version sequence.
     *
     * @return the current content revision
     */
    long getContentsVersion();
}
