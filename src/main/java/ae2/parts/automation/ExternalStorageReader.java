package ae2.parts.automation;

import ae2.api.stacks.AEKey;
import ae2.api.storage.MEStorage;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import java.util.Map;

final class ExternalStorageReader implements StorageReader {
    private final Map<ae2.api.stacks.AEKeyType, ae2.api.behaviors.ExternalStorageStrategy> strategies;

    ExternalStorageReader(WorldServer level, BlockPos fromPos, EnumFacing fromSide) {
        this.strategies = StackWorldBehaviors.createExternalStorageStrategies(level, fromPos, fromSide);
    }

    @Override
    public long getCurrentStock(AEKey what) {
        var strategy = strategies.get(what.getType());
        if (strategy == null) {
            return 0;
        }
        MEStorage storage = strategy.createWrapper(false, () -> {
        });
        if (storage == null) {
            return 0;
        }
        return Math.max(0, storage.getAvailableStacks().get(what));
    }
}
