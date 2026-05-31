package ae2.me.storage;

import ae2.api.config.Actionable;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.MEStorage;
import net.minecraft.util.text.ITextComponent;

import java.util.Objects;

/**
 * Convenient base class for wrapping another {@link MEStorage} and forwarding <strong>all</strong> methods to the base
 * inventory.
 * <p/>
 * If no delegate is set, it will act like a {@link NullInventory}.
 */
public class DelegatingMEInventory implements MEStorage {
    private MEStorage delegate;

    public DelegatingMEInventory(MEStorage delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    protected MEStorage getDelegate() {
        return delegate;
    }

    protected void setDelegate(MEStorage delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean isPreferredStorageFor(AEKey input, IActionSource source) {
        return getDelegate().isPreferredStorageFor(input, source);
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
    public KeyCounter getAvailableStacks() {
        return getDelegate().getAvailableStacks();
    }

    @Override
    public ITextComponent getDescription() {
        return getDelegate().getDescription();
    }
}
