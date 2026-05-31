package ae2.api.behaviors;

import ae2.api.stacks.AEKeyType;
import ae2.api.storage.MEStorage;
import ae2.parts.automation.StackWorldBehaviors;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface ExternalStorageStrategy {
    static void register(AEKeyType type, Factory factory) {
        StackWorldBehaviors.registerExternalStorageStrategy(type, factory);
    }

    @Nullable
    MEStorage createWrapper(boolean extractableOnly, Runnable injectOrExtractCallback);

    @FunctionalInterface
    interface Factory {
        ExternalStorageStrategy create(WorldServer level, BlockPos fromPos, EnumFacing fromSide);
    }
}
