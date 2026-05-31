package ae2.api.behaviors;

import ae2.api.config.Actionable;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.parts.automation.StackWorldBehaviors;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import org.jetbrains.annotations.ApiStatus;

/**
 * Strategy to export stacks into adjacent blocks from the grid. Used by the export bus.
 */
@ApiStatus.Experimental
public interface StackExportStrategy {
    static void register(AEKeyType type, Factory factory) {
        StackWorldBehaviors.registerExportStrategy(type, factory);
    }

    /**
     * Transfer from the network inventory in {@param context} to the external inventory this strategy was created for.
     */
    long transfer(StackTransferContext context, AEKey what, long maxAmount);

    /**
     * Tries inserting into the adjacent inventory and returns the amount that was pushed.
     */
    long push(AEKey what, long maxAmount, Actionable mode);

    @FunctionalInterface
    interface Factory {
        StackExportStrategy create(WorldServer level, BlockPos fromPos, EnumFacing fromSide);
    }
}
