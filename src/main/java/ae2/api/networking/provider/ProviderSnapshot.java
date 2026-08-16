package ae2.api.networking.provider;

import ae2.api.networking.IGrid;
import ae2.helpers.patternprovider.PatternContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable view of the active pattern providers in one grid. */
public record ProviderSnapshot(List<PatternContainer> providers) {
    public ProviderSnapshot {
        providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
    }

    public static ProviderSnapshot discover(IGrid grid) {
        Objects.requireNonNull(grid, "grid");
        List<PatternContainer> providers = new ArrayList<>();
        for (Class<?> type : grid.getMachineClasses()) {
            if (!PatternContainer.class.isAssignableFrom(type)) {
                continue;
            }
            for (Object machine : grid.getActiveMachines(type.asSubclass(PatternContainer.class))) {
                if (machine instanceof PatternContainer provider) {
                    providers.add(provider);
                }
            }
        }
        return new ProviderSnapshot(providers);
    }

    /** Reads the grid service when available, preserving compatibility with unregistered custom grids. */
    public static ProviderSnapshot get(IGrid grid) {
        try {
            return grid.getService(IProviderSnapshotService.class).getSnapshot();
        } catch (IllegalArgumentException ignored) {
            return discover(grid);
        }
    }
}
