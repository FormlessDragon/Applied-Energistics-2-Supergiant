package ae2.parts.automation;

import ae2.api.config.Actionable;
import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.me.storage.ExternalStorageFacade;
import com.google.common.primitives.Ints;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import org.jetbrains.annotations.Nullable;

public abstract class HandlerStrategy<C, S> {
    public static final HandlerStrategy<IItemHandler, ItemStack> ITEMS = new HandlerStrategy<>(AEKeyType.items()) {
        @Override
        public boolean isSupported(AEKey what) {
            return AEItemKey.is(what);
        }

        @Override
        public ExternalStorageFacade getFacade(IItemHandler handler) {
            return ExternalStorageFacade.of(handler);
        }

        @Override
        public long insert(IItemHandler handler, AEKey what, long amount, Actionable mode) {
            if (what instanceof AEItemKey itemKey) {
                var stack = itemKey.toStack(Ints.saturatedCast(amount));

                var remainder = ItemHandlerHelper.insertItem(handler, stack, mode.isSimulate());
                return amount - remainder.getCount();
            }

            return 0;
        }

        @Nullable
        @Override
        public ItemStack getStack(AEKey what, long amount) {
            if (what instanceof AEItemKey itemKey) {
                return itemKey.toStack(Ints.saturatedCast(amount));
            }
            return null;
        }
    };
    public static final HandlerStrategy<IFluidHandler, FluidStack> FLUIDS = new HandlerStrategy<>(AEKeyType.fluids()) {
        @Override
        public boolean isSupported(AEKey what) {
            return AEFluidKey.is(what);
        }

        @Override
        public ExternalStorageFacade getFacade(IFluidHandler handler) {
            return ExternalStorageFacade.of(handler);
        }

        @Override
        public long insert(IFluidHandler handler, AEKey what, long amount, Actionable mode) {
            if (what instanceof AEFluidKey fluidKey && amount > 0) {
                var stack = fluidKey.toStack(Ints.saturatedCast(amount));
                return handler.fill(stack, mode.getFluidAction());
            }

            return 0;
        }

        @Override
        public FluidStack getStack(AEKey what, long amount) {
            if (what instanceof AEFluidKey fluidKey) {
                return fluidKey.toStack(Ints.saturatedCast(amount));
            }
            return null;
        }
    };
    private final AEKeyType keyType;

    public HandlerStrategy(AEKeyType keyType) {
        this.keyType = keyType;
    }

    public boolean isSupported(AEKey what) {
        return what.getType() == keyType;
    }

    public AEKeyType getKeyType() {
        return keyType;
    }

    public abstract ExternalStorageFacade getFacade(C handler);

    @Nullable
    public abstract S getStack(AEKey what, long amount);

    public abstract long insert(C handler, AEKey what, long amount, Actionable mode);

}
