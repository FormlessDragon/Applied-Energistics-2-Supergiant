package ae2.api.behaviors;

import ae2.api.stacks.AEKeyType;
import ae2.parts.automation.StackWorldBehaviors;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/**
 * Strategy to import from adjacent blocks into the grid. Used by the import bus.
 */
public interface StackImportStrategy {
    static void register(AEKeyType keyType, Factory factory) {
        StackWorldBehaviors.registerImportStrategy(keyType, factory);
    }

    boolean transfer(StackTransferContext context);

    @FunctionalInterface
    interface Factory {
        StackImportStrategy create(WorldServer level, BlockPos fromPos, EnumFacing fromSide);
    }
}
