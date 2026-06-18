package ae2.helpers.patternprovider;

import ae2.api.AECapabilities;
import ae2.api.behaviors.ExternalStorageStrategy;
import ae2.api.config.Actionable;
import ae2.api.config.PatternProviderInsertionMode;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.storage.MEStorage;
import ae2.me.storage.CompositeStorage;
import ae2.parts.automation.StackWorldBehaviors;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Internal helpers for resolving targets that a pattern provider can push crafted outputs into.
 * <p>
 * This keeps the target semantics shared between the live pattern push path and UI-only target probing.
 */
@ApiStatus.Internal
public final class PatternProviderTargets {
    private PatternProviderTargets() {
    }

    @Nullable
    static PatternProviderTarget get(World level, BlockPos pos, EnumFacing side, IActionSource src) {
        if (!(level instanceof WorldServer serverLevel)) {
            return null;
        }

        return get(serverLevel, pos, side, src,
            StackWorldBehaviors.createExternalStorageStrategies(serverLevel, pos, side));
    }

    @Nullable
    static PatternProviderTarget get(WorldServer level, BlockPos pos, EnumFacing side, IActionSource src,
                                     Map<AEKeyType, ExternalStorageStrategy> strategies) {
        TargetResolution resolution = resolve(level, pos, side, strategies);
        if (resolution.storage() != null) {
            return wrapMeStorage(resolution.storage(), src);
        }

        if (!resolution.externalTargets().isEmpty()) {
            return wrapTargets(resolution.externalTargets(), src);
        }

        return null;
    }

    /**
     * Checks whether the block side exposes any target that the pattern provider push path can resolve.
     */
    public static boolean hasTarget(World level, BlockPos pos, EnumFacing side) {
        if (!(level instanceof WorldServer serverLevel)) {
            return false;
        }

        Map<AEKeyType, ExternalStorageStrategy> strategies = StackWorldBehaviors.createExternalStorageStrategies(
            serverLevel,
            pos,
            side);
        TargetResolution resolution = resolve(serverLevel, pos, side, strategies);
        return resolution.storage() != null || !resolution.externalTargets().isEmpty();
    }

    private static TargetResolution resolve(WorldServer level, BlockPos pos, EnumFacing side,
                                            Map<AEKeyType, ExternalStorageStrategy> strategies) {
        TileEntity blockEntity = level.getTileEntity(pos);
        MEStorage storage = null;
        if (blockEntity != null && blockEntity.hasCapability(AECapabilities.ME_STORAGE, side)) {
            storage = blockEntity.getCapability(AECapabilities.ME_STORAGE, side);
        }

        if (storage != null) {
            return new TargetResolution(storage, new Reference2ObjectOpenHashMap<>());
        }

        Reference2ObjectMap<AEKeyType, ExternalTarget> externalTargets = new Reference2ObjectOpenHashMap<>(
            strategies.size());
        if (blockEntity != null && blockEntity.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side)) {
            IItemHandler itemHandler = blockEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
            if (itemHandler != null) {
                externalTargets.put(AEKeyType.items(), new ItemHandlerExternalTarget(itemHandler));
            }
        }

        Reference2ObjectMap<AEKeyType, MEStorage> externalStorages = new Reference2ObjectOpenHashMap<>(
            strategies.size());
        for (Entry<AEKeyType, ExternalStorageStrategy> entry : strategies.entrySet()) {
            if (externalTargets.containsKey(entry.getKey())) {
                continue;
            }

            MEStorage wrapper = entry.getValue().createWrapper(false, () -> {
            });
            if (wrapper != null) {
                externalStorages.put(entry.getKey(), wrapper);
            }
        }

        if (!externalStorages.isEmpty()) {
            ExternalTarget storageTarget = new StorageExternalTarget(new CompositeStorage(externalStorages));
            for (Entry<AEKeyType, MEStorage> entry : externalStorages.entrySet()) {
                externalTargets.put(entry.getKey(), storageTarget);
            }
        }

        return new TargetResolution(null, externalTargets);
    }

    private static PatternProviderTarget wrapTargets(Reference2ObjectMap<AEKeyType, ExternalTarget> targets,
                                                     IActionSource src) {
        return new PatternProviderTarget() {
            @Override
            public long insert(AEKey what, long amount, Actionable type) {
                return insert(what, amount, type, PatternProviderInsertionMode.DEFAULT);
            }

            @Override
            public long insert(AEKey what, long amount, Actionable type, PatternProviderInsertionMode insertionMode) {
                ExternalTarget target = targets.get(what.getType());
                return target == null ? 0 : target.insert(what, amount, type, insertionMode, src);
            }

            @Override
            public boolean containsPatternInput(Set<AEKey> patternInputs) {
                for (ExternalTarget target : targets.values()) {
                    if (target.containsPatternInput(patternInputs)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean containsAnyStack() {
                for (ExternalTarget target : targets.values()) {
                    if (target.containsAnyStack()) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean hasEmptySlots() {
                for (ExternalTarget target : targets.values()) {
                    if (target.hasEmptySlots()) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    static PatternProviderTarget wrapMeStorage(MEStorage storage, IActionSource src) {
        return new PatternProviderTarget() {
            @Override
            public long insert(AEKey what, long amount, Actionable type) {
                return insert(what, amount, type, PatternProviderInsertionMode.DEFAULT);
            }

            @Override
            public long insert(AEKey what, long amount, Actionable type, PatternProviderInsertionMode insertionMode) {
                return storage.insert(what, amount, type, src);
            }

            @Override
            public boolean containsPatternInput(Set<AEKey> patternInputs) {
                for (Object2LongMap.Entry<AEKey> stack : storage.getAvailableStacks()) {
                    if (patternInputs.contains(stack.getKey().dropSecondary())) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean containsAnyStack() {
                return !storage.getAvailableStacks().isEmpty();
            }

            @Override
            public boolean hasEmptySlots() {
                return false;
            }
        };
    }

    private record TargetResolution(
        @Nullable MEStorage storage,
        Reference2ObjectMap<AEKeyType, ExternalTarget> externalTargets) {
    }

    private interface ExternalTarget {
        long insert(AEKey what, long amount, Actionable type, PatternProviderInsertionMode insertionMode,
                    IActionSource src);

        boolean containsPatternInput(Set<AEKey> patternInputs);

        boolean containsAnyStack();

        boolean hasEmptySlots();
    }

    private record ItemHandlerExternalTarget(IItemHandler handler) implements ExternalTarget {
        @Override
        public long insert(AEKey what, long amount, Actionable type, PatternProviderInsertionMode insertionMode,
                           IActionSource src) {
            if (!(what instanceof AEItemKey itemKey)) {
                return 0;
            }

            if (type == Actionable.SIMULATE) {
                int requestAmount = (int) Math.min(amount, Integer.MAX_VALUE);
                ItemStack input = itemKey.toStack(requestAmount);
                ItemStack remaining = PatternProviderTargetCache.insertIntoItemHandler(this.handler, input, true,
                    insertionMode);
                return requestAmount - remaining.getCount();
            }

            long remainingAmount = amount;
            while (remainingAmount > 0) {
                int requestAmount = (int) Math.min(remainingAmount, Integer.MAX_VALUE);
                ItemStack input = itemKey.toStack(requestAmount);
                ItemStack remaining = PatternProviderTargetCache.insertIntoItemHandler(this.handler, input, false,
                    insertionMode);
                long inserted = requestAmount - remaining.getCount();
                if (inserted <= 0) {
                    break;
                }
                remainingAmount -= inserted;
            }
            return amount - remainingAmount;
        }

        @Override
        public boolean containsPatternInput(Set<AEKey> patternInputs) {
            for (int slot = 0; slot < this.handler.getSlots(); slot++) {
                AEItemKey what = AEItemKey.of(this.handler.getStackInSlot(slot));
                if (what != null && patternInputs.contains(what.dropSecondary())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean containsAnyStack() {
            for (int slot = 0; slot < this.handler.getSlots(); slot++) {
                if (!this.handler.getStackInSlot(slot).isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean hasEmptySlots() {
            for (int slot = 0; slot < this.handler.getSlots(); slot++) {
                if (this.handler.getStackInSlot(slot).isEmpty()) {
                    return true;
                }
            }
            return false;
        }
    }

    private record StorageExternalTarget(MEStorage storage) implements ExternalTarget {
        @Override
        public long insert(AEKey what, long amount, Actionable type, PatternProviderInsertionMode insertionMode,
                           IActionSource src) {
            return this.storage.insert(what, amount, type, src);
        }

        @Override
        public boolean containsPatternInput(Set<AEKey> patternInputs) {
            for (Object2LongMap.Entry<AEKey> stack : this.storage.getAvailableStacks()) {
                if (patternInputs.contains(stack.getKey().dropSecondary())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean containsAnyStack() {
            return !this.storage.getAvailableStacks().isEmpty();
        }

        @Override
        public boolean hasEmptySlots() {
            return false;
        }
    }
}
