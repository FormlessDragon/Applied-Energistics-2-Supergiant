package ae2.api.networking;

import ae2.api.networking.crafting.ICraftingWatcherNode;
import ae2.api.networking.storage.IStorageWatcherNode;
import ae2.api.stacks.AEKey;
import org.jetbrains.annotations.ApiStatus;

/**
 * DO NOT IMPLEMENT. Will be injected when adding an {@link IStorageWatcherNode} or {@link ICraftingWatcherNode} to a
 * grid.
 */
@ApiStatus.NonExtendable
public interface IStackWatcher {
    /**
     * Request that ALL changes be broadcast to this watcher.
     *
     * @param watchAll true to enable watching all stacks
     */
    void setWatchAll(boolean watchAll);

    /**
     * Add a specific {@link AEKey} to watch.
     * Supports multiple values, duplicate ones will not be added.
     */
    void add(AEKey stack);

    /**
     * Remove a specific {@link AEKey} from the watcher.
     */
    void remove(AEKey stack);

    /**
     * Removes all watched stacks and resets the watcher to a clean state.
     */
    void reset();
}
