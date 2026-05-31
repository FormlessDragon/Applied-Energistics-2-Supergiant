package ae2.parts.automation;

import ae2.api.behaviors.ExternalStorageStrategy;
import ae2.api.storage.MEStorage;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import org.jetbrains.annotations.Nullable;

public class ForgeExternalStorageStrategy<T, S> implements ExternalStorageStrategy {
    private final Capability<T> capability;
    private final HandlerStrategy<T, S> conversion;
    private final WorldServer level;
    private final BlockPos fromPos;
    private final EnumFacing fromSide;

    public ForgeExternalStorageStrategy(Capability<T> capability,
                                        HandlerStrategy<T, S> conversion,
                                        WorldServer level,
                                        BlockPos fromPos,
                                        EnumFacing fromSide) {
        this.capability = capability;
        this.conversion = conversion;
        this.level = level;
        this.fromPos = fromPos;
        this.fromSide = fromSide;
    }

    public static ExternalStorageStrategy createItem(WorldServer level, BlockPos fromPos, EnumFacing fromSide) {
        return new ForgeExternalStorageStrategy<>(
            CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
            HandlerStrategy.ITEMS,
            level,
            fromPos,
            fromSide);
    }

    public static ExternalStorageStrategy createFluid(WorldServer level, BlockPos fromPos, EnumFacing fromSide) {
        return new ForgeExternalStorageStrategy<>(
            CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
            HandlerStrategy.FLUIDS,
            level,
            fromPos,
            fromSide);
    }

    @Nullable
    private T getCapability() {
        var blockEntity = level.getTileEntity(fromPos);
        if (blockEntity == null) {
            return null;
        }

        return blockEntity.getCapability(capability, fromSide);
    }

    @Nullable
    @Override
    public MEStorage createWrapper(boolean extractableOnly, Runnable injectOrExtractCallback) {
        var storage = getCapability();
        if (storage == null) {
            return null;
        }

        var result = conversion.getFacade(storage);
        result.setChangeListener(injectOrExtractCallback);
        result.setExtractableOnly(extractableOnly);
        return result;
    }
}
