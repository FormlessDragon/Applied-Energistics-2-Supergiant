package ae2.helpers;

import ae2.api.storage.ISubGuiHost;

/**
 * Exposes a configurable server-side work interval for sub-GUIs that edit machine timing.
 * <p>
 * This is used by machines whose user interface needs a dedicated numeric editor for
 * "run once every N ticks" behavior.
 */
public interface IWorkIntervalHost extends ISubGuiHost {

    /**
     * Returns the current work interval in ticks.
     *
     * @return the configured tick interval, always {@code >= 1}
     */
    long getWorkInterval();

    /**
     * Updates the current work interval in ticks.
     *
     * @param newValue the new tick interval, expected to be {@code >= 1}
     */
    void setWorkInterval(long newValue);
}
