package ae2.client.render.overlay;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public record OverlayHighlightLocation(
    int dimensionId,
    BlockPos pos,
    @Nullable EnumFacing side,
    OverlayHighlightShape shape) {
}
