package ae2.items.tools.advancedmemorycard;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record AdvancedMemoryCardP2PIdentity(int dimension, BlockPos pos, @Nullable EnumFacing side) {

    public static AdvancedMemoryCardP2PIdentity of(AdvancedMemoryCardP2PEntry entry) {
        return new AdvancedMemoryCardP2PIdentity(entry.dimension(), entry.pos(), entry.side());
    }

    @Nullable
    public AdvancedMemoryCardP2PEntry findIn(List<AdvancedMemoryCardP2PEntry> entries) {
        for (AdvancedMemoryCardP2PEntry entry : entries) {
            if (matches(entry)) {
                return entry;
            }
        }
        return null;
    }

    private boolean matches(AdvancedMemoryCardP2PEntry entry) {
        return this.dimension == entry.dimension()
            && this.pos.equals(entry.pos())
            && this.side == entry.side();
    }
}
