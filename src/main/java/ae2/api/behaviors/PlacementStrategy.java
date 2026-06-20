package ae2.api.behaviors;

import ae2.api.config.Actionable;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.parts.automation.StackWorldBehaviors;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface PlacementStrategy {
    /**
     * A placement strategy that simply does nothing.
     */
    static PlacementStrategy noop() {
        return NoopPlacementStrategy.INSTANCE;
    }

    static void register(AEKeyType type, Factory factory) {
        StackWorldBehaviors.registerPlacementStrategy(type, factory);
    }

    void clearBlocked();

    /**
     * @return The amount actually placed
     */
    long placeInWorld(AEKey what, long amount, Actionable type, boolean placeAsEntity);

    @FunctionalInterface
    interface Factory {
        PlacementStrategy create(WorldServer level, BlockPos fromPos, EnumFacing fromSide, TileEntity host,
                                 @Nullable UUID owningPlayerId);
    }
}
