package ae2.items.contents;

import net.minecraft.item.ItemStack;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A supplier that can cache its resulting Value as long as the itemstack returned by an original supplier is the same.
 */
public final class StackDependentSupplier<T> implements Supplier<T> {
    private final Supplier<ItemStack> stackSupplier;
    private final Function<ItemStack, T> transform;

    private ItemStack currentStack;
    private T currentValue;

    public StackDependentSupplier(Supplier<ItemStack> stackSupplier, Function<ItemStack, T> transform) {
        this.stackSupplier = stackSupplier;
        this.transform = transform;
    }

    @Override
    public T get() {
        var stack = this.stackSupplier.get();
        if (this.currentStack != stack) {
            this.currentValue = this.transform.apply(stack);
            this.currentStack = stack;
        }
        return this.currentValue;
    }
}
