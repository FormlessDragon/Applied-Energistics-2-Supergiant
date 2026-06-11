package ae2.parts.automation;

import ae2.api.behaviors.StackExportStrategy;
import ae2.api.behaviors.StackTransferContext;
import ae2.api.config.Actionable;
import ae2.api.stacks.AEKey;
import ae2.api.storage.StorageHelper;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StorageExportStrategy<T, S> implements StackExportStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(StorageExportStrategy.class);
    private final Capability<T> capability;
    private final HandlerStrategy<T, S> handlerStrategy;
    private final WorldServer level;
    private final BlockPos fromPos;
    private final EnumFacing fromSide;

    public StorageExportStrategy(Capability<T> capability,
                                 HandlerStrategy<T, S> handlerStrategy,
                                 WorldServer level,
                                 BlockPos fromPos,
                                 EnumFacing fromSide) {
        this.capability = capability;
        this.handlerStrategy = handlerStrategy;
        this.level = level;
        this.fromPos = fromPos;
        this.fromSide = fromSide;
    }

    public static StackExportStrategy createItem(WorldServer level, BlockPos fromPos, EnumFacing fromSide) {
        return new StorageExportStrategy<>(
            CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
            HandlerStrategy.ITEMS,
            level,
            fromPos,
            fromSide);
    }

    public static StackExportStrategy createFluid(WorldServer level, BlockPos fromPos, EnumFacing fromSide) {
        return new StorageExportStrategy<>(
            CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
            HandlerStrategy.FLUIDS,
            level,
            fromPos,
            fromSide);
    }

    private @Nullable T getAdjacentStorage() {
        var blockEntity = level.getTileEntity(fromPos);
        if (blockEntity == null) {
            return null;
        }

        return blockEntity.getCapability(capability, fromSide);
    }

    @Override
    public long transfer(StackTransferContext context, AEKey what, long amount) {
        if (!handlerStrategy.isSupported(what)) {
            return 0;
        }

        var adjacentStorage = getAdjacentStorage();
        if (adjacentStorage == null) {
            return 0;
        }

        var inv = context.getInternalStorage();

        var extracted = StorageHelper.poweredExtraction(
            context.getEnergySource(),
            inv.getInventory(),
            what,
            amount,
            context.getActionSource(),
            Actionable.SIMULATE);

        long wasInserted = handlerStrategy.insert(adjacentStorage, what, extracted, Actionable.SIMULATE);

        if (wasInserted > 0) {
            extracted = StorageHelper.poweredExtraction(
                context.getEnergySource(),
                inv.getInventory(),
                what,
                wasInserted,
                context.getActionSource(),
                Actionable.MODULATE);

            wasInserted = handlerStrategy.insert(adjacentStorage, what, extracted, Actionable.MODULATE);

            if (wasInserted < extracted) {
                // Be nice and try to give the overflow back
                long leftover = extracted - wasInserted;
                leftover -= inv.getInventory().insert(what, leftover, Actionable.MODULATE, context.getActionSource());
                if (leftover > 0) {
                    LOG.error("Storage export: adjacent block unexpectedly refused insert, voided {}x{}", leftover,
                        what);
                }
            }
        }

        return wasInserted;
    }

    @Override
    public long push(AEKey what, long amount, Actionable mode) {
        if (!handlerStrategy.isSupported(what)) {
            return 0;
        }

        var adjacentStorage = getAdjacentStorage();
        if (adjacentStorage == null) {
            return 0;
        }

        return handlerStrategy.insert(adjacentStorage, what, amount, mode);
    }
}
