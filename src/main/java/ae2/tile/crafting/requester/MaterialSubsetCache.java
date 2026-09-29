package ae2.tile.crafting.requester;

import ae2.api.networking.IGrid;
import ae2.api.stacks.AEKey;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

/**
 * Per-slot cache of the material closure a request consumes, used to trim the crafting inventory snapshot.
 *
 * <p>An entry is only handed out while the request key, the grid and the craftables revision all still match:
 * editing the request explores different patterns, swapping grids means different providers, and a revision bump
 * means the pattern structure changed.</p>
 */
public final class MaterialSubsetCache {
    private final Entry[] entries;

    public MaterialSubsetCache(int slotCount) {
        this.entries = new Entry[slotCount];
    }

    /**
     * Returns the cached closure for a request key, or null when no slot holds a valid entry for it. Used when the
     * caller starts a calculation from the request key rather than a known slot.
     */
    @Nullable
    public Collection<AEKey> peekByKey(AEKey key, IGrid grid, long revision) {
        for (Entry entry : this.entries) {
            if (entry != null && entry.matches(key, grid, revision)) {
                return entry.keys();
            }
        }
        return null;
    }

    public void put(int slot, AEKey key, IGrid grid, long revision, Collection<AEKey> keys) {
        this.entries[slot] = new Entry(key, grid, revision, keys);
    }

    public void clear(int slot) {
        this.entries[slot] = null;
    }

    public void clearAll() {
        Arrays.fill(this.entries, null);
    }

    private record Entry(AEKey key, IGrid grid, long revision, Collection<AEKey> keys) {
        boolean matches(AEKey key, IGrid grid, long revision) {
            return this.revision == revision && this.grid == grid && this.key.equals(key);
        }
    }
}
