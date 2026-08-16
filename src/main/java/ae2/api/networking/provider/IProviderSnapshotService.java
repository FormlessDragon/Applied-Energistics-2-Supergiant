package ae2.api.networking.provider;

import ae2.api.networking.IGridService;

/** Provides the current immutable pattern-provider snapshot for a grid. */
public interface IProviderSnapshotService extends IGridService {
    ProviderSnapshot getSnapshot();
}
