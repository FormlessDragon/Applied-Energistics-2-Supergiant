package ae2.api.cellterminal;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Immutable in-world locator for a Cell Terminal target.
 * <p>
 * Future server-side write actions use this data to re-resolve the live world object immediately before mutating it so
 * cached target instances do not outlive the actual block or part they describe.
 */
public record CellTerminalTargetLocator(ResourceLocation kindId, int dimensionId, BlockPos pos,
                                        @Nullable EnumFacing side) {
    public CellTerminalTargetLocator {
        Objects.requireNonNull(kindId, "kindId");
        Objects.requireNonNull(pos, "pos");
        if (kindId.getPath().isEmpty()) {
            throw new IllegalArgumentException("kindId path must not be empty");
        }
    }
}
