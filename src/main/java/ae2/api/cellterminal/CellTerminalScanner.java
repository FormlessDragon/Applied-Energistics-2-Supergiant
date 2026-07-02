package ae2.api.cellterminal;

import ae2.api.networking.IGrid;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Scanner extension that discovers targets from an ME grid.
 * <p>
 * Sealed hierarchy ensures type safety while enabling unified processing.
 * All scanner types use the same {@link #scan(IGrid)} method name with covariant return types.
 */
public sealed interface CellTerminalScanner permits
    CellTerminalScanner.Storage,
    CellTerminalScanner.Bus,
    CellTerminalScanner.Subnet {

    /**
     * Returns the stable scanner identifier used for registration and duplicate detection.
     */
    ResourceLocation getId();

    /**
     * Scans the grid and returns discovered targets.
     * <p>
     * This is the unified entry point allowing polymorphic iteration over all scanners.
     * Specific scanner types return their specialized target types via covariant returns.
     *
     * @param grid The ME grid to scan.
     * @return Discovered targets (maybe empty).
     */
    @NotNull
    List<? extends CellTerminalTarget> scan(IGrid grid);

    /**
     * Discovers direct storage targets (ME Drives, ME Chests, etc.) from the grid.
     */
    non-sealed interface Storage extends CellTerminalScanner {
        @Override
        @NonNull
        List<? extends CellTerminalStorageTarget> scan(IGrid grid);
    }

    /**
     * Discovers storage-bus targets from the grid.
     */
    non-sealed interface Bus extends CellTerminalScanner {
        @Override
        @NonNull
        List<? extends CellTerminalBusTarget> scan(IGrid grid);
    }

    /**
     * Discovers subnet targets reachable from the grid.
     */
    non-sealed interface Subnet extends CellTerminalScanner {
        @Override
        @NonNull
        List<? extends CellTerminalSubnetTarget> scan(IGrid grid);
    }
}
