package ae2.api.storage;

import ae2.api.config.Actionable;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.me.storage.NullInventory;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Delegates all calls to a {@link MEStorage} returned by a supplier such that the underlying storage can change
 * dynamically. If the supplier returns a null value, this storage will appear empty and read-only.
 */
public final class SupplierStorage implements MEStorage {
    private final Supplier<@Nullable MEStorage> supplier;

    public SupplierStorage(Supplier<@Nullable MEStorage> supplier) {
        this.supplier = supplier;
    }

    private MEStorage getDelegate() {
        return Objects.requireNonNullElseGet(supplier.get(), NullInventory::of);
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return getDelegate().isPreferredStorageFor(what, source);
    }

    @Override
    public boolean isStickyStorageFor(AEKey what, IActionSource source) {
        return getDelegate().isStickyStorageFor(what, source);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return getDelegate().insert(what, amount, mode, source);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return getDelegate().extract(what, amount, mode, source);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        getDelegate().getAvailableStacks(out);
    }

    @Override
    public ITextComponent getDescription() {
        return getDelegate().getDescription();
    }

    @Override
    public KeyCounter getAvailableStacks() {
        return getDelegate().getAvailableStacks();
    }
}
