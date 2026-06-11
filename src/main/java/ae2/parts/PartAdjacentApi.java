package ae2.parts;

import ae2.api.parts.IPartHost;
import ae2.util.Platform;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;

import org.jetbrains.annotations.Nullable;

/**
 * Utility class to resolve an API that is adjacent to a part.
 */
public class PartAdjacentApi<T> {
    private final AEBasePart part;
    private final Capability<T> capability;
    private final Runnable invalidationListener;
    private TileEntity lastAdjacentTileEntity;
    private boolean lastHadCapability;
    private boolean hasTrackedAdjacent;

    public PartAdjacentApi(AEBasePart part, Capability<T> capability) {
        this(part, capability, () -> {
        });
    }

    public PartAdjacentApi(AEBasePart part, Capability<T> capability, Runnable invalidationListener) {
        this.part = part;
        this.capability = capability;
        this.invalidationListener = invalidationListener;
    }

    public static boolean isPartValid(AEBasePart part) {
        TileEntity be = part.getTileEntity();
        if (!(be instanceof IPartHost host)) {
            return false;
        }

        return host.getPart(part.getSide()) == part
            && !be.isInvalid();
    }

    @Nullable
    public T find() {
        ResolvedAdjacent<T> resolved = resolveAdjacent();
        updateAdjacentState(resolved.tileEntity, resolved.capability != null, false);
        return resolved.capability;
    }

    public void onNeighborChanged(BlockPos neighborPos) {
        if (!isAdjacentBlock(neighborPos)) {
            return;
        }

        ResolvedAdjacent<T> resolved = resolveAdjacent();
        updateAdjacentState(resolved.tileEntity, resolved.capability != null,
            this.hasTrackedAdjacent && (this.lastHadCapability || resolved.capability != null));
    }

    private boolean isAdjacentBlock(BlockPos neighborPos) {
        IPartHost host = part.getHost();
        EnumFacing side = part.getSide();
        if (host == null || side == null) {
            return false;
        }

        TileEntity hostTile = host.getTileEntity();
        if (hostTile == null) {
            return false;
        }

        return hostTile.getPos().offset(side).equals(neighborPos);
    }

    private ResolvedAdjacent<T> resolveAdjacent() {
        IPartHost host = part.getHost();
        EnumFacing side = part.getSide();
        if (host == null || side == null) {
            return ResolvedAdjacent.empty();
        }

        TileEntity hostTile = host.getTileEntity();
        if (hostTile == null) {
            return ResolvedAdjacent.empty();
        }

        World level = hostTile.getWorld();
        BlockPos targetPos = hostTile.getPos().offset(side);
        if (!Platform.areBlockEntitiesTicking(level, targetPos)) {
            return ResolvedAdjacent.empty();
        }

        TileEntity adjacent = level.getTileEntity(targetPos);
        if (adjacent == null || adjacent.isInvalid()) {
            return ResolvedAdjacent.empty();
        }

        if (!adjacent.hasCapability(capability, side.getOpposite())) {
            return new ResolvedAdjacent<>(adjacent, null);
        }

        T resolved = adjacent.getCapability(capability, side.getOpposite());
        if (resolved == null) {
            invalidationListener.run();
        }
        return new ResolvedAdjacent<>(adjacent, resolved);
    }

    private void updateAdjacentState(@Nullable TileEntity adjacent, boolean hasCapability, boolean notifyIfUnchanged) {
        boolean changed = this.lastAdjacentTileEntity != adjacent || this.lastHadCapability != hasCapability;
        if ((changed || notifyIfUnchanged) && this.hasTrackedAdjacent) {
            invalidationListener.run();
        }

        this.lastAdjacentTileEntity = adjacent;
        this.lastHadCapability = hasCapability;
        this.hasTrackedAdjacent = true;
    }

    private record ResolvedAdjacent<T>(@Nullable TileEntity tileEntity, @Nullable T capability) {
        private static final ResolvedAdjacent<?> EMPTY = new ResolvedAdjacent<>(null, null);

        @SuppressWarnings("unchecked")
        private static <T> ResolvedAdjacent<T> empty() {
            return (ResolvedAdjacent<T>) EMPTY;
        }
    }
}




