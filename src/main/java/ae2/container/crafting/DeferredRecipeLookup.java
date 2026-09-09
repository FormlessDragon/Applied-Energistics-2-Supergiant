package ae2.container.crafting;

/**
 * Coalesces recipe invalidations until the following server tick.
 */
public final class DeferredRecipeLookup {
    private static final long CLEAN = Long.MIN_VALUE;

    private long dirtySinceTick = CLEAN;

    public boolean markDirty(long currentTick) {
        boolean wasClean = this.dirtySinceTick == CLEAN;
        this.dirtySinceTick = currentTick;
        return wasClean;
    }

    public boolean isDue(long currentTick) {
        return this.dirtySinceTick != CLEAN && currentTick > this.dirtySinceTick;
    }

    public boolean isDirty() {
        return this.dirtySinceTick != CLEAN;
    }

    public void clear() {
        this.dirtySinceTick = CLEAN;
    }
}
