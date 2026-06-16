package ae2.me.storage;

import ae2.api.config.Actionable;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.MEStorage;
import ae2.core.AELog;
import ae2.core.localization.GuiText;
import ae2.items.misc.GenericResourcePackageItem;
import com.google.common.primitives.Ints;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.IItemHandler;

import org.jetbrains.annotations.Nullable;
import java.util.Set;

/**
 * Adapts external platform storage to behave like an {@link MEStorage}.
 */
public abstract class ExternalStorageFacade implements MEStorage {
    protected boolean extractableOnly;
    @Nullable
    private Runnable changeListener;

    public static ExternalStorageFacade of(IFluidHandler handler) {
        return new FluidHandlerFacade(handler);
    }

    public static ExternalStorageFacade of(IItemHandler handler) {
        return new ItemHandlerFacade(handler);
    }

    public void setChangeListener(@Nullable Runnable listener) {
        this.changeListener = listener;
    }

    public abstract int getSlots();

    @Nullable
    public abstract GenericStack getStackInSlot(int slot);

    public abstract AEKeyType getKeyType();

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        var inserted = insertExternal(what, Ints.saturatedCast(amount), mode);
        if (inserted > 0 && mode == Actionable.MODULATE) {
            if (this.changeListener != null) {
                this.changeListener.run();
            }
        }
        return inserted;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        var extracted = extractExternal(what, Ints.saturatedCast(amount), mode);
        if (extracted > 0 && mode == Actionable.MODULATE) {
            if (this.changeListener != null) {
                this.changeListener.run();
            }
        }
        return extracted;
    }

    @Override
    public ITextComponent getDescription() {
        return GuiText.ExternalStorage.text(getKeyType().getDescription());
    }

    protected abstract int insertExternal(AEKey what, int amount, Actionable mode);

    protected abstract int extractExternal(AEKey what, int amount, Actionable mode);

    public abstract boolean containsAnyFuzzy(Set<AEKey> keys);

    public void setExtractableOnly(boolean extractableOnly) {
        this.extractableOnly = extractableOnly;
    }

    private static class ItemHandlerFacade extends ExternalStorageFacade {
        private final IItemHandler handler;

        public ItemHandlerFacade(IItemHandler handler) {
            this.handler = handler;
        }

        /**
         * Extracts as much as possible from a single slot of an item handler, ignoring the usual max stack size
         * restriction.
         */
        private static int extractFromHandler(IItemHandler handler, int slot, AEItemKey itemKey, int maxExtract,
                                              Actionable actionable) {
            ItemStack stackInInventorySlot = handler.getStackInSlot(slot);
            if (!itemKey.matches(stackInInventorySlot)) {
                return 0;
            }

            return switch (actionable) {
                case SIMULATE -> {
                    // Query amount before the stack potentially gets modified
                    int amountInSlot = stackInInventorySlot.getCount();

                    int extracted = wrapHandlerExtract(handler, slot, maxExtract, true);
                    // Heuristic for simulation: looping in case of simulations is pointless, since the state of the
                    // underlying inventory does not change after a simulated extraction. To still support
                    // inventories that report stacks that are larger than maxStackSize, we use this heuristic
                    if (extracted == itemKey.getMaxStackSize() && maxExtract > itemKey.getMaxStackSize()
                        && amountInSlot > itemKey.getMaxStackSize()) {
                        yield Math.min(amountInSlot, maxExtract);
                    } else {
                        yield extracted;
                    }
                }
                case MODULATE -> {
                    // We have to loop here because according to the docs, the handler shouldn't return a stack with
                    // size > maxSize, even if we request more. So even if it returns a valid stack, it might have more
                    // stuff.
                    int totalExtracted = 0;
                    while (true) {
                        int extracted = wrapHandlerExtract(handler, slot, maxExtract - totalExtracted, false);
                        if (extracted > 0) {
                            totalExtracted += extracted;
                        } else {
                            break;
                        }
                    }
                    yield totalExtracted;
                }
            };
        }

        /**
         * Guards {@link IItemHandler#extractItem(int, int, boolean)} to make sure that we don't extract more than
         * requested.
         */
        private static int wrapHandlerExtract(IItemHandler handler, int slot, int maxExtract, boolean simulate) {
            int extracted = handler.extractItem(slot, maxExtract, simulate).getCount();
            if (extracted > maxExtract) {
                // Something broke. It should never return more than we requested...
                // We're going to silently eat the remainder
                AELog.warn(
                    "Mod that provided item handler %s is broken. Returned %d items while only requesting %d.",
                    handler.getClass().getSimpleName(), extracted, maxExtract);
                return maxExtract;
            } else {
                return extracted;
            }
        }

        @Override
        public int getSlots() {
            return handler.getSlots();
        }

        @Nullable
        @Override
        public GenericStack getStackInSlot(int slot) {
            ItemStack stack = handler.getStackInSlot(slot);
            GenericStack packaged = GenericResourcePackageItem.unwrap(stack);
            return packaged != null ? packaged : GenericStack.fromItemStack(stack);
        }

        @Override
        public AEKeyType getKeyType() {
            return AEKeyType.items();
        }

        @Override
        public int insertExternal(AEKey what, int amount, Actionable mode) {
            if (!(what instanceof AEItemKey itemKey)) {
                return 0;
            }

            ItemStack orgInput = itemKey.toStack(Ints.saturatedCast(amount));
            ItemStack remaining = orgInput;

            int slotCount = handler.getSlots();
            boolean simulate = mode == Actionable.SIMULATE;

            // This uses a brute force approach and tries to jam it in every slot the inventory exposes.
            for (int i = 0; i < slotCount && !remaining.isEmpty(); i++) {
                remaining = handler.insertItem(i, remaining, simulate);
            }

            // At this point, we still have some items left...
            if (remaining == orgInput) {
                // The stack remained unmodified, target inventory is full
                return 0;
            }

            return amount - remaining.getCount();
        }

        @Override
        public int extractExternal(AEKey what, int amount, Actionable mode) {
            int packagedExtracted = extractPackagedResource(what, amount, mode);
            if (packagedExtracted > 0) {
                return packagedExtracted;
            }

            if (!(what instanceof AEItemKey itemKey)) {
                return 0;
            }

            int totalExtracted = 0;

            for (int i = 0; i < handler.getSlots(); i++) {
                int extracted = extractFromHandler(handler, i, itemKey, amount - totalExtracted, mode);
                totalExtracted += extracted;

                // Done?
                if (amount == totalExtracted) {
                    break;
                }
            }

            return totalExtracted;
        }

        private int extractPackagedResource(AEKey what, int amount, Actionable mode) {
            int totalExtracted = 0;
            for (int i = 0; i < handler.getSlots() && totalExtracted < amount; i++) {
                ItemStack stack = handler.getStackInSlot(i);
                GenericStack packaged = GenericResourcePackageItem.unwrap(stack);
                if (packaged == null || !packaged.what().equals(what)) {
                    continue;
                }

                int extracted = Math.min(Ints.saturatedCast(packaged.amount()), amount - totalExtracted);
                if (mode == Actionable.MODULATE) {
                    long simulatedLeftover = packaged.amount() - extracted;
                    if (simulatedLeftover > 0 && !canReturnLeftoverPackage(what, simulatedLeftover)) {
                        AELog.warn("Cannot extract %d of %s from generic resource package: leftover package cannot be returned",
                            extracted, what);
                        continue;
                    }

                    ItemStack removed = handler.extractItem(i, 1, false);
                    GenericStack removedPackaged = GenericResourcePackageItem.unwrap(removed);
                    if (removedPackaged == null || !removedPackaged.what().equals(what)) {
                        AELog.warn("Item handler changed generic resource package while extracting %s", what);
                        continue;
                    }

                    int removedAmount = Math.min(Ints.saturatedCast(removedPackaged.amount()),
                        amount - totalExtracted);
                    long leftover = removedPackaged.amount() - removedAmount;
                    if (leftover > 0) {
                        ItemStack leftoverPackage = GenericResourcePackageItem.wrap(what, leftover);
                        for (int slot = 0; slot < handler.getSlots() && !leftoverPackage.isEmpty(); slot++) {
                            leftoverPackage = handler.insertItem(slot, leftoverPackage, false);
                        }
                        if (!leftoverPackage.isEmpty()) {
                            AELog.warn("Voided %s of %s because leftover generic resource package could not be returned",
                                leftover, what);
                        }
                    }
                    extracted = removedAmount;
                }
                totalExtracted += extracted;
            }
            return totalExtracted;
        }

        private boolean canReturnLeftoverPackage(AEKey what, long amount) {
            ItemStack leftoverPackage = GenericResourcePackageItem.wrap(what, amount);
            for (int slot = 0; slot < handler.getSlots() && !leftoverPackage.isEmpty(); slot++) {
                leftoverPackage = handler.insertItem(slot, leftoverPackage, true);
            }
            return leftoverPackage.isEmpty();
        }

        @Override
        public boolean containsAnyFuzzy(Set<AEKey> keys) {
            for (int i = 0; i < handler.getSlots(); i++) {
                GenericStack packaged = GenericResourcePackageItem.unwrap(handler.getStackInSlot(i));
                var what = packaged != null ? packaged.what() : AEItemKey.of(handler.getStackInSlot(i));
                if (what != null) {
                    if (keys.contains(what.dropSecondary())) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            for (int i = 0; i < handler.getSlots(); i++) {
                // Skip resources that cannot be extracted if that filter was enabled
                var stack = handler.getStackInSlot(i);
                if (stack.isEmpty()) {
                    continue;
                }

                if (extractableOnly) {
                    if (handler.extractItem(i, 1, true).isEmpty()) {
                        if (handler.extractItem(i, stack.getCount(), true).isEmpty()) {
                            continue;
                        }
                    }
                }

                GenericStack packaged = GenericResourcePackageItem.unwrap(stack);
                if (packaged != null) {
                    out.add(packaged.what(), packaged.amount());
                } else {
                    out.add(AEItemKey.of(stack), stack.getCount());
                }
            }
        }
    }

    private static class FluidHandlerFacade extends ExternalStorageFacade {
        private final IFluidHandler handler;

        public FluidHandlerFacade(IFluidHandler handler) {
            this.handler = handler;
        }

        @Override
        public int getSlots() {
            return handler.getTankProperties().length;
        }

        @Nullable
        @Override
        public GenericStack getStackInSlot(int slot) {
            var tanks = handler.getTankProperties();
            if (slot < 0 || slot >= tanks.length) {
                return null;
            }
            return GenericStack.fromFluidStack(tanks[slot].getContents());
        }

        @Override
        public AEKeyType getKeyType() {
            return AEKeyType.fluids();
        }

        @Override
        protected int insertExternal(AEKey what, int amount, Actionable mode) {
            if (!(what instanceof AEFluidKey fluidKey)) {
                return 0;
            }

            return handler.fill(fluidKey.toStack(amount), mode.getFluidAction());
        }

        @Override
        public int extractExternal(AEKey what, int amount, Actionable mode) {
            if (!(what instanceof AEFluidKey fluidKey)) {
                return 0;
            }

            var fluidStack = fluidKey.toStack(Ints.saturatedCast(amount));

            // Drain the fluid from the tank
            FluidStack gathered = handler.drain(fluidStack, mode.getFluidAction());
            if (gathered == null || gathered.amount <= 0) {
                // If nothing was pulled from the tank, return null
                return 0;
            }

            return gathered.amount;
        }

        @Override
        public boolean containsAnyFuzzy(Set<AEKey> keys) {
            for (IFluidTankProperties tank : handler.getTankProperties()) {
                var what = AEFluidKey.of(tank.getContents());
                if (what != null) {
                    if (keys.contains(what.dropSecondary())) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            for (IFluidTankProperties tank : handler.getTankProperties()) {
                // Skip resources that cannot be extracted if that filter was enabled
                var stack = tank.getContents();
                if (stack == null || stack.amount <= 0) {
                    continue;
                }

                if (extractableOnly) {
                    var simulated = handler.drain(stack, false);
                    if (simulated == null || simulated.amount <= 0) {
                        continue;
                    }
                }

                out.add(AEFluidKey.of(stack), stack.amount);
            }
        }
    }
}
