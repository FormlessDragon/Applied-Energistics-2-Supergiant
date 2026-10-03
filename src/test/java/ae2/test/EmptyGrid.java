package ae2.test;

import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridService;
import ae2.api.networking.events.GridEvent;
import com.google.gson.stream.JsonWriter;

import java.util.List;
import java.util.Set;

/**
 * An empty {@link IGrid} for tests that need a grid instance but do not exercise any grid service.
 */
public final class EmptyGrid implements IGrid {

    /**
     * {@inheritDoc}
     * <p/>
     * This grid has no services at all, so every lookup yields {@code null}, matching a real grid that simply does not
     * have the requested service registered.
     */
    @Override
    public <C extends IGridService> C getService(Class<C> type) {
        return null;
    }

    @Override
    public <T extends GridEvent> T postEvent(T event) {
        return event;
    }

    @Override
    public Iterable<Class<?>> getMachineClasses() {
        return List.of();
    }

    @Override
    public Iterable<IGridNode> getMachineNodes(Class<?> type) {
        return List.of();
    }

    @Override
    public <T> Set<T> getMachines(Class<T> type) {
        return Set.of();
    }

    @Override
    public <T> Set<T> getActiveMachines(Class<T> type) {
        return Set.of();
    }

    @Override
    public Iterable<IGridNode> getNodes() {
        return List.of();
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public IGridNode getPivot() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public void export(JsonWriter writer) {
    }
}
