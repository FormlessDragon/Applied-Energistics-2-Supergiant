package ae2.me.service;

import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridServiceProvider;
import ae2.api.networking.provider.IProviderSnapshotService;
import ae2.api.networking.provider.ProviderSnapshot;

import java.util.Objects;
import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.Nullable;

/** Caches one provider discovery result for a grid revision. */
public final class ProviderSnapshotService implements IProviderSnapshotService, IGridServiceProvider {
    private final IGrid grid;
    private long observedRevision = Long.MIN_VALUE;
    private ProviderSnapshot snapshot = new ProviderSnapshot(java.util.List.of());

    public ProviderSnapshotService(IGrid grid) {
        this.grid = Objects.requireNonNull(grid, "grid");
    }

    @Override
    public ProviderSnapshot getSnapshot() {
        long revision = this.grid.getMachineRevision();
        if (revision < 0 || revision != this.observedRevision) {
            this.snapshot = ProviderSnapshot.discover(this.grid);
            this.observedRevision = revision;
        }
        return this.snapshot;
    }

    @Override
    public void addNode(IGridNode gridNode, @Nullable NBTTagCompound savedData) {
        this.observedRevision = Long.MIN_VALUE;
    }

    @Override
    public void removeNode(IGridNode gridNode) {
        this.observedRevision = Long.MIN_VALUE;
    }
}
