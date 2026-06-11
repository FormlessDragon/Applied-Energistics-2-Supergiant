package ae2.parts.automation;

import ae2.api.behaviors.StackImportStrategy;
import ae2.api.behaviors.StackTransferContext;
import ae2.api.config.Actionable;
import ae2.core.AELog;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Strategy for efficiently importing stacks from external storage into an internal
 * {@link ae2.api.storage.MEStorage}.
 */
public class StorageImportStrategy<T, S> implements StackImportStrategy {
    private final Capability<T> capability;
    private final HandlerStrategy<T, S> conversion;
    private final WorldServer level;
    private final BlockPos fromPos;
    private final EnumFacing fromSide;

    public StorageImportStrategy(Capability<T> capability,
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

    public static StackImportStrategy createItem(WorldServer level, BlockPos fromPos, EnumFacing fromSide) {
        return new StorageImportStrategy<>(
            CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
            HandlerStrategy.ITEMS,
            level,
            fromPos,
            fromSide);
    }

    public static StackImportStrategy createFluid(WorldServer level, BlockPos fromPos, EnumFacing fromSide) {
        return new StorageImportStrategy<>(
            CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
            HandlerStrategy.FLUIDS,
            level,
            fromPos,
            fromSide);
    }

    private @Nullable T getAdjacentHandler() {
        var blockEntity = level.getTileEntity(fromPos);
        if (blockEntity == null) {
            return null;
        }

        return blockEntity.getCapability(capability, fromSide);
    }

    @Override
    public boolean transfer(StackTransferContext context) {
        if (!context.isKeyTypeEnabled(conversion.getKeyType())) {
            return false;
        }

        var adjacentHandler = getAdjacentHandler();
        if (adjacentHandler == null) {
            return false;
        }

        var adjacentStorage = conversion.getFacade(adjacentHandler);

        long remainingTransferAmount = context.getOperationsRemaining()
            * (long) conversion.getKeyType().getAmountPerOperation();

        var inv = context.getInternalStorage();

        // Try to find an extractable resource that fits our filter
        for (int i = 0; i < adjacentStorage.getSlots() && remainingTransferAmount > 0; i++) {
            var resource = adjacentStorage.getStackInSlot(i);
            if (resource == null
                // Regard a filter that is set on the bus
                || context.isInFilter(resource.what()) == context.isInverted()) {
                continue;
            }

            // Check how much of *this* resource we can actually insert into the network, it might be 0
            // if the cells are partitioned or there's not enough types left, etc.
            var amountForThisResource = inv.getInventory().insert(resource.what(), remainingTransferAmount,
                Actionable.SIMULATE,
                context.getActionSource());

            // Try to simulate-extract it
            var amount = adjacentStorage.extract(resource.what(), amountForThisResource, Actionable.MODULATE,
                context.getActionSource());
            if (amount > 0) {
                var inserted = inv.getInventory().insert(resource.what(), amount, Actionable.MODULATE,
                    context.getActionSource());

                if (inserted < amount) {
                    // Be nice and try to give the overflow back
                    long leftover = amount - inserted;
                    leftover -= adjacentStorage.insert(resource.what(), leftover, Actionable.MODULATE,
                        context.getActionSource());
                    if (leftover > 0) {
                        AELog.warn("Extracted %dx%s from adjacent storage and voided it because network refused insert",
                            leftover, resource.what());
                    }
                }

                var opsUsed = Math.max(1, inserted / conversion.getKeyType().getAmountPerOperation());
                context.reduceOperationsRemaining(opsUsed);
                remainingTransferAmount -= inserted;
            }
        }

        return context.hasDoneWork();
    }
}
